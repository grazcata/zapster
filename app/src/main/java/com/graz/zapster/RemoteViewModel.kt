package com.graz.zapster

import android.app.Application
import android.net.Uri
import android.os.Build
import android.util.Log
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.graz.zapster.data.DiscoveredTv
import com.graz.zapster.data.SettingsStore
import com.graz.zapster.data.TvDiscovery
import com.graz.zapster.data.TvSettings
import com.graz.zapster.data.atv.AtvCertificateStore
import com.graz.zapster.data.atv.AtvKey
import com.graz.zapster.data.atv.AtvPairingSession
import com.graz.zapster.data.atv.AtvRemoteClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.net.InetSocketAddress
import java.net.NetworkInterface

enum class ConnectionState { Unknown, Connecting, Connected, Error }

/** Where the user is in the pairing flow. */
enum class PairingStage { Idle, Requesting, AwaitingCode, Completing }

data class SideloadStatus(
    val active: Boolean = false,
    val progress: String = "",
    val isSuccess: Boolean = false,
    val isError: Boolean = false,
    val errorDetails: String? = null,
    val httpFallbackUrl: String? = null,
)

data class UiState(
    val settings: TvSettings = TvSettings(),
    val settingsLoaded: Boolean = false,
    val connection: ConnectionState = ConnectionState.Unknown,
    val powered: Boolean = false,
    val message: String? = null,
    val showSettings: Boolean = false,
    val scanning: Boolean = false,
    val discovered: List<DiscoveredTv> = emptyList(),
    val scanCompleted: Boolean = false,
    val pairing: PairingStage = PairingStage.Idle,
    val pairingHost: String = "",
    val sideload: SideloadStatus = SideloadStatus(),
)

class RemoteViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)
    private val discovery = TvDiscovery(app)
    private val identity by lazy { AtvCertificateStore.identity(app) }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var remote: AtvRemoteClient? = null
    private var pairingSession: AtvPairingSession? = null

    init {
        viewModelScope.launch {
            store.settings.collect { settings ->
                val previous = _state.value.settings
                val changed = settings.host != previous.host ||
                    settings.paired != previous.paired
                _state.value = _state.value.copy(
                    settings = settings,
                    settingsLoaded = true,
                    showSettings = _state.value.showSettings || !settings.isConfigured,
                )
                if (changed && settings.isConfigured) connect()
            }
        }
    }

    // ----------------------------------------------------------- connection

    fun connect() {
        val settings = _state.value.settings
        if (!settings.isConfigured) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                connection = ConnectionState.Connecting,
                message = null,
            )
            remote?.close()
            val client = AtvRemoteClient(
                host = settings.host,
                identity = identity,
                onPowerState = { on ->
                    _state.value = _state.value.copy(powered = on)
                },
                onDisconnect = { reason ->
                    _state.value = _state.value.copy(
                        connection = ConnectionState.Error,
                        message = reason,
                    )
                    // Auto-reconnect in background when connection drops
                    scheduleReconnect()
                },
            )
            remote = client

            client.connect().fold(
                onSuccess = {
                    _state.value = _state.value.copy(
                        connection = ConnectionState.Connected,
                        powered = client.isOn,
                        message = null,
                    )
                },
                onFailure = { error ->
                    Log.w(TAG, "Remote connection to ${settings.host} failed", error)
                    _state.value = _state.value.copy(
                        connection = ConnectionState.Error,
                        // A refused certificate is the normal signal that the TV
                        // has forgotten this client, not a network problem.
                        message = describeConnectFailure(error),
                    )
                },
            )
        }
    }

    private fun describeConnectFailure(error: Throwable): String {
        val text = error.message.orEmpty()
        return when {
            text.contains("SSL", true) || text.contains("certificate", true) ->
                "The TV no longer trusts this app — pair again"
            else -> "Cannot reach the TV at ${_state.value.settings.host}"
        }
    }

    private var reconnectJob: kotlinx.coroutines.Job? = null

    private fun scheduleReconnect() {
        if (!_state.value.settings.isConfigured) return
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            if (_state.value.connection != ConnectionState.Connected) {
                Log.d(TAG, "Attempting automatic background reconnection...")
                connect()
            }
        }
    }

    // -------------------------------------------------------------- pairing

    /** Opens a pairing session; the TV puts a six-digit code on screen. */
    fun startPairing(host: String, name: String = "") {
        val target = host.trim()
        if (target.isBlank()) {
            report("Choose a TV first")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(
                pairing = PairingStage.Requesting,
                pairingHost = target,
                message = null,
            )
            pairingSession?.close()
            val session = AtvPairingSession(target, identity)
            pairingSession = session

            session.start(clientName = "TV Remote").fold(
                onSuccess = {
                    _state.value = _state.value.copy(pairing = PairingStage.AwaitingCode)
                    if (name.isNotBlank()) {
                        _state.value = _state.value.copy(
                            settings = _state.value.settings.copy(name = name)
                        )
                    }
                },
                onFailure = { error ->
                    pairingSession = null
                    _state.value = _state.value.copy(
                        pairing = PairingStage.Idle,
                        message = error.message ?: "Could not start pairing",
                    )
                },
            )
        }
    }

    fun submitPairingCode(code: String) {
        val session = pairingSession ?: run {
            report("Start pairing again")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(pairing = PairingStage.Completing)
            session.complete(code).fold(
                onSuccess = {
                    pairingSession = null
                    _state.value = _state.value.copy(
                        pairing = PairingStage.Idle,
                        showSettings = false,
                    )
                    store.save(
                        TvSettings(
                            host = _state.value.pairingHost,
                            name = _state.value.settings.name,
                            paired = true,
                        )
                    )
                },
                onFailure = { error ->
                    // A local checksum failure keeps the session open, so the
                    // user can retype rather than restart the whole flow.
                    _state.value = _state.value.copy(
                        pairing = PairingStage.AwaitingCode,
                        message = error.message ?: "Pairing failed",
                    )
                },
            )
        }
    }

    fun cancelPairing() {
        pairingSession?.close()
        pairingSession = null
        _state.value = _state.value.copy(pairing = PairingStage.Idle)
    }

    /** Forgets the client identity, so the next pairing starts clean. */
    fun unpair() {
        viewModelScope.launch {
            remote?.close()
            remote = null
            AtvCertificateStore.reset(getApplication())
            store.save(TvSettings(host = _state.value.settings.host))
            _state.value = _state.value.copy(
                connection = ConnectionState.Unknown,
                showSettings = true,
            )
        }
    }

    // ------------------------------------------------------------ discovery

    fun scan() {
        if (_state.value.scanning) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                scanning = true,
                scanCompleted = false,
                discovered = emptyList(),
            )
            val found = runCatching { discovery.discover() }.getOrDefault(emptyList())
            _state.value = _state.value.copy(
                scanning = false,
                scanCompleted = true,
                discovered = found,
            )
        }
    }

    // -------------------------------------------------------------- actions

    fun press(keyCode: Int) {
        val client = remote
        if (client == null || !client.isConnected) {
            connect()
            report("Connexion à la TV en cours...")
            return
        }
        haptic()
        client.sendKey(keyCode)
    }

    /** Sends a text string directly to the active input field on the TV. */
    fun sendText(text: String, pressEnter: Boolean = false) {
        val client = remote
        if (client == null || !client.isConnected) {
            connect()
            report("Connexion à la TV en cours...")
            return
        }
        haptic()
        client.sendText(text)
        if (pressEnter) {
            viewModelScope.launch {
                kotlinx.coroutines.delay(200)
                client.sendKey(AtvKey.DPAD_CENTER)
            }
        }
    }

    fun launchApp(link: String) {
        val client = remote
        if (client == null || !client.isConnected) {
            connect()
            report("Connexion à la TV en cours...")
            return
        }
        haptic()
        client.launchAppLink(link)
    }

    /**
     * POWER is a toggle on this protocol, so it is only sent when the TV's
     * reported state differs from the one requested.
     */
    fun setPower(on: Boolean) {
        val client = remote
        if (client == null || !client.isConnected) {
            report("Not connected to the TV")
            return
        }
        if (client.isOn == on) return
        haptic()
        client.sendKey(AtvKey.POWER)
    }

    fun openSettings() {
        _state.value = _state.value.copy(showSettings = true)
    }

    fun closeSettings() {
        if (_state.value.settings.isConfigured) {
            _state.value = _state.value.copy(showSettings = false)
        }
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun dismissSideload() {
        _state.value = _state.value.copy(sideload = SideloadStatus())
    }

    /**
     * Sideload an APK to the TV.
     * First attempts direct ADB install on port 5555.
     * If ADB is unreachable (USB debugging disabled), starts a local HTTP server
     * and sends the download link to the TV (or displays it for Downloader).
     */
    fun installApk(uri: Uri, fileName: String?) {
        val host = _state.value.settings.host
        if (host.isBlank()) {
            report("Aucun téléviseur sélectionné")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val displayName = fileName ?: "application.apk"
            _state.value = _state.value.copy(
                sideload = SideloadStatus(
                    active = true,
                    progress = "Préparation de $displayName..."
                )
            )

            val context = getApplication<Application>()
            val tempFile = File(context.cacheDir, "sideload_temp.apk")
            try {
                // 1. Copy stream from Uri to temporary file
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: error("Impossible de lire le fichier APK")

                val sizeMb = String.format("%.1f", tempFile.length() / (1024.0 * 1024.0))
                _state.value = _state.value.copy(
                    sideload = SideloadStatus(
                        active = true,
                        progress = "Connexion ADB à $host:5555 ($sizeMb Mo)..."
                    )
                )

                // 2. Try direct ADB install via dadb
                var adbSuccess = false
                var adbError: String? = null
                try {
                    dadb.Dadb.create(host, 5555).use { dadbClient ->
                        _state.value = _state.value.copy(
                            sideload = SideloadStatus(
                                active = true,
                                progress = "Installation en cours sur la TV ($sizeMb Mo)..."
                            )
                        )
                        dadbClient.install(tempFile)
                        adbSuccess = true
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "ADB install failed: ${t.message}", t)
                    adbError = t.message ?: "Échec de connexion au port ADB 5555"
                }

                if (adbSuccess) {
                    _state.value = _state.value.copy(
                        sideload = SideloadStatus(
                            active = false,
                            isSuccess = true,
                            progress = "$displayName a été installée avec succès sur la TV !"
                        )
                    )
                    haptic()
                    return@launch
                }

                // 3. Fallback: Start local HTTP server
                _state.value = _state.value.copy(
                    sideload = SideloadStatus(
                        active = true,
                        progress = "Port ADB fermé. Démarrage du serveur de partage local..."
                    )
                )
                val serverUrl = startLocalApkServer(tempFile)
                _state.value = _state.value.copy(
                    sideload = SideloadStatus(
                        active = false,
                        isError = true,
                        errorDetails = adbError,
                        httpFallbackUrl = serverUrl,
                        progress = "Le port ADB est fermé. Téléchargez l'APK sur la TV via ce lien :"
                    )
                )
                // Launch on TV if remote is connected
                remote?.launchAppLink(serverUrl)
            } catch (e: Throwable) {
                Log.e(TAG, "Sideload error", e)
                _state.value = _state.value.copy(
                    sideload = SideloadStatus(
                        active = false,
                        isError = true,
                        errorDetails = e.message,
                        progress = "Erreur lors du traitement de l'APK"
                    )
                )
            }
        }
    }

    private var serverSocket: java.net.ServerSocket? = null
    private var serverThread: Thread? = null

    private fun startLocalApkServer(file: File): String {
        stopLocalApkServer()
        val ip = getLocalIpAddress() ?: "127.0.0.1"
        val port = 8080
        val server = java.net.ServerSocket(port)
        serverSocket = server

        val thread = Thread {
            try {
                while (!server.isClosed) {
                    val socket = server.accept()
                    Thread {
                        try {
                            socket.use { s ->
                                val inStream = s.getInputStream()
                                val outStream = s.getOutputStream()
                                val reader = inStream.bufferedReader()
                                val requestLine = reader.readLine() ?: return@use

                                val responseHeader = "HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: application/vnd.android.package-archive\r\n" +
                                        "Content-Length: ${file.length()}\r\n" +
                                        "Content-Disposition: attachment; filename=\"app.apk\"\r\n" +
                                        "Connection: close\r\n\r\n"
                                outStream.write(responseHeader.toByteArray(Charsets.UTF_8))
                                file.inputStream().use { fileIn ->
                                    fileIn.copyTo(outStream)
                                }
                                outStream.flush()
                            }
                        } catch (ignored: Exception) {}
                    }.start()
                }
            } catch (ignored: Exception) {}
        }
        thread.isDaemon = true
        thread.start()
        serverThread = thread

        return "http://$ip:$port/app.apk"
    }

    private fun stopLocalApkServer() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        serverThread = null
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (ignored: Exception) {}
        return null
    }

    override fun onCleared() {
        stopLocalApkServer()
        remote?.close()
        pairingSession?.close()
        super.onCleared()
    }

    private companion object {
        const val TAG = "TvRemote"
    }

    private fun report(message: String) {
        _state.value = _state.value.copy(message = message)
    }

    private fun haptic() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getApplication<Application>().getSystemService(VibratorManager::class.java)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getApplication<Application>().getSystemService(Vibrator::class.java)
        }
        if (vibrator?.hasVibrator() != true) return
        vibrator.vibrate(VibrationEffect.createOneShot(12, 60))
    }
}
