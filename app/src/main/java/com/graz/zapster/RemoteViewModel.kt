package com.graz.zapster

import android.app.Application
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ConnectionState { Unknown, Connecting, Connected, Error }

/** Where the user is in the pairing flow. */
enum class PairingStage { Idle, Requesting, AwaitingCode, Completing }

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
            report("Not connected to the TV")
            return
        }
        haptic()
        client.sendKey(keyCode)
    }

    fun launchApp(link: String) {
        val client = remote
        if (client == null || !client.isConnected) {
            report("Not connected to the TV")
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

    override fun onCleared() {
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
