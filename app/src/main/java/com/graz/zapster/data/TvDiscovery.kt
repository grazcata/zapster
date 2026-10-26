package com.graz.zapster.data

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

data class DiscoveredTv(
    val host: String,
    val friendlyName: String,
    val model: String?,
    val manufacturer: String?,
) {
    /** What to show in a picker: the TV's own name, falling back to its model. */
    val label: String get() = friendlyName.ifBlank { model ?: host }
}

/**
 * Finds Bravia sets on the current network.
 *
 * Two strategies, tried in order:
 *  1. **SSDP** — the standard UPnP discovery multicast the TV already answers.
 *     Fast (about two seconds) and exact.
 *  2. **Subnet sweep** — probes every address on the local /24 for the TV's
 *     unauthenticated device-description document. Slower, but works on the many
 *     home routers and guest networks that block multicast.
 */
class TvDiscovery(private val context: Context) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(1, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    suspend fun discover(): List<DiscoveredTv> {
        val viaSsdp = discoverViaSsdp()
        return viaSsdp.ifEmpty { discoverViaSweep() }
    }

    // ---------------------------------------------------------------- SSDP

    private suspend fun discoverViaSsdp(): List<DiscoveredTv> = withContext(Dispatchers.IO) {
        // Some devices drop inbound multicast without this lock held.
        val lock = runCatching {
            val wifi = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifi.createMulticastLock("tv-remote-discovery").apply {
                setReferenceCounted(true)
                acquire()
            }
        }.getOrNull()

        val locations = mutableSetOf<String>()
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 400

                val group = InetAddress.getByName(SSDP_ADDRESS)
                // Sony's own service type finds Bravias directly; rootdevice is a
                // safety net for models that only answer the generic search.
                val searchTargets = listOf(SONY_SCALAR_ST, "upnp:rootdevice")

                // UDP is lossy, so each search target goes out more than once.
                repeat(2) {
                    for (target in searchTargets) {
                        val message = buildMSearch(target).toByteArray()
                        runCatching {
                            socket.send(
                                DatagramPacket(message, message.size, group, SSDP_PORT)
                            )
                        }
                    }
                }

                val deadline = System.currentTimeMillis() + SSDP_WINDOW_MS
                val buffer = ByteArray(2048)
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    val response = String(packet.data, 0, packet.length)
                    headerValue(response, "LOCATION")?.let { locations += it }
                }
            }
        } catch (_: Exception) {
            // Multicast unavailable on this network; the sweep will take over.
        } finally {
            runCatching { lock?.release() }
        }

        // Resolve each advertised description document concurrently.
        coroutineScope {
            locations.map { location -> async { describe(location) } }
                .awaitAll()
                .filterNotNull()
                .distinctBy { it.host }
        }
    }

    private fun buildMSearch(searchTarget: String): String = buildString {
        append("M-SEARCH * HTTP/1.1\r\n")
        append("HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n")
        append("MAN: \"ssdp:discover\"\r\n")
        append("MX: 1\r\n")
        append("ST: $searchTarget\r\n")
        append("\r\n")
    }

    private fun headerValue(response: String, name: String): String? =
        response.lineSequence()
            .firstOrNull { it.startsWith("$name:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    // --------------------------------------------------------------- sweep

    private suspend fun discoverViaSweep(): List<DiscoveredTv> {
        val candidates = localSubnetAddresses() ?: return emptyList()

        // Batched rather than all-at-once: a few hundred simultaneous sockets
        // will exhaust file descriptors on some devices.
        val found = mutableListOf<DiscoveredTv>()
        for (batch in candidates.chunked(48)) {
            val results = coroutineScope {
                batch.map { host -> async(Dispatchers.IO) { probe(host) } }.awaitAll()
            }
            found += results.filterNotNull()
        }
        return found.distinctBy { it.host }
    }

    /** Every host address on the phone's own IPv4 subnet, excluding itself. */
    private fun localSubnetAddresses(): List<String>? {
        val (address, prefix) = localIpv4() ?: return null

        // Only sweep networks small enough to finish quickly — /22 is 1022 hosts.
        if (prefix < 22 || prefix > 30) return null

        val octets = address.address
        val base = octets.foldIndexed(0L) { i, acc, b ->
            acc or ((b.toLong() and 0xFF) shl (8 * (3 - i)))
        }
        val mask = (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
        val network = base and mask
        val broadcast = network or mask.inv().and(0xFFFFFFFFL)

        return ((network + 1) until broadcast).map { value ->
            "${(value shr 24) and 0xFF}.${(value shr 16) and 0xFF}." +
                "${(value shr 8) and 0xFF}.${value and 0xFF}"
        }.filter { it != address.hostAddress }
    }

    private fun localIpv4(): Pair<Inet4Address, Int>? = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.interfaceAddresses.asSequence() }
            .firstNotNullOfOrNull { ifaceAddress ->
                val addr = ifaceAddress.address
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    addr to ifaceAddress.networkPrefixLength.toInt()
                } else {
                    null
                }
            }
    }.getOrNull()

    /** Cheap TCP knock first, so closed addresses cost milliseconds, not a full HTTP timeout. */
    private fun probe(host: String): DiscoveredTv? {
        val reachable = runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, 80), 250)
                true
            }
        }.getOrDefault(false)
        if (!reachable) return null

        return describe("http://$host$DESCRIPTION_PATH")
    }

    // ------------------------------------------------------- description doc

    /** Fetches and parses a UPnP device description; null if it isn't a Bravia. */
    private fun describe(location: String): DiscoveredTv? {
        val host = runCatching { java.net.URI(location).host }.getOrNull() ?: return null

        val xml = runCatching {
            http.newCall(Request.Builder().url(location).build()).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull() ?: return null

        val manufacturer = tag(xml, "manufacturer")
        val friendlyName = tag(xml, "friendlyName").orEmpty()
        val model = tag(xml, "modelName")

        // A Bravia identifies itself either by advertising Sony's Scalar Web API
        // or, failing that, by naming Sony as the manufacturer.
        val isBravia = xml.contains("ScalarWebAPI", ignoreCase = true) ||
            manufacturer?.contains("Sony", ignoreCase = true) == true
        if (!isBravia) return null

        return DiscoveredTv(
            host = host,
            friendlyName = friendlyName,
            model = model,
            manufacturer = manufacturer,
        )
    }

    private fun tag(xml: String, name: String): String? =
        Regex(
            "<(?:[A-Za-z0-9_]+:)?$name[^>]*>(.*?)</(?:[A-Za-z0-9_]+:)?$name>",
            RegexOption.DOT_MATCHES_ALL,
        ).find(xml)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }

    private companion object {
        const val SSDP_ADDRESS = "239.255.255.250"
        const val SSDP_PORT = 1900
        const val SSDP_WINDOW_MS = 2500L
        const val SONY_SCALAR_ST = "urn:schemas-sony-com:service:ScalarWebAPI:1"

        /** Bravia serves its description here without authentication. */
        const val DESCRIPTION_PATH = "/sony/webapi/ssdp/dd.xml"
    }
}
