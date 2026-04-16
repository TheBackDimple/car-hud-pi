package com.example.carhud.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Discovers the Pi on the USB tether (or other local link) by probing
 * https://IP:8000/health. Used when Pi host is set to "auto".
 *
 * Probes must run on the same [Network] as the tether: OkHttp's default client
 * follows the process default route (often cellular), which cannot reach the Pi's
 * private IP. We use [ConnectivityManager.bindProcessToNetwork] per network (API 23+).
 */
object PiDiscovery {

    private const val HUD_PORT = 8000
    private const val PROBE_TIMEOUT_MS = 1500L
    /** Scan full /24 minus gateway; USB DHCP can assign high last octets (e.g. .140). */
    private const val MAX_HOSTS_TO_TRY = 253

    private val httpClient by lazy {
        val trustAllCerts = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
            override fun getAcceptedIssuers() = emptyArray<java.security.cert.X509Certificate>()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustAllCerts), SecureRandom())
        }
        OkHttpClient.Builder()
            .connectTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    /**
     * Returns the default gateway's IPv4 address, or null if not a private network.
     */
    private fun getDefaultGateway(context: Context): Inet4Address? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val network = cm.activeNetwork ?: return null
        val linkProps = try {
            cm.getLinkProperties(network)
        } catch (_: Exception) {
            null
        } ?: return null

        for (route in linkProps.routes) {
            if (route.destination?.prefixLength == 0) {
                val gateway = route.gateway
                if (gateway is Inet4Address && isPrivateAddress(gateway)) {
                    return gateway
                }
            }
        }
        return null
    }

    /**
     * Builds /24 host IPs from the phone's own address (same subnet as the Pi on USB tether).
     * OEMs often report a non-24 [prefixLen]; tether is still effectively /24 for discovery.
     */
    private fun candidateIpsFromDeviceOnSubnet(addr: Inet4Address, prefixLen: Int): List<String> {
        if (!isPrivateAddress(addr)) return emptyList()
        if (prefixLen == 32) return emptyList()
        val octets = addr.address
        if (octets.size != 4) return emptyList()
        val base = "${octets[0].toInt() and 0xFF}.${octets[1].toInt() and 0xFF}.${octets[2].toInt() and 0xFF}"
        val selfLast = octets[3].toInt() and 0xFF
        return (1..254)
            .filter { it != selfLast }
            .take(MAX_HOSTS_TO_TRY)
            .map { "$base.$it" }
    }

    /** USB RNDIS/NCM interfaces often include these substrings in [LinkProperties.getInterfaceName]. */
    private fun isLikelyUsbTetherInterface(interfaceName: String?): Boolean {
        if (interfaceName.isNullOrBlank()) return false
        val n = interfaceName.lowercase()
        return n.contains("rndis") || n.contains("ncm") || n.contains("usb")
    }

    private fun isPrivateAddress(addr: Inet4Address): Boolean {
        val octets = addr.address
        if (octets.size != 4) return false
        return when (octets[0].toInt() and 0xFF) {
            10 -> true
            172 -> octets[1].toInt() and 0xFF in 16..31
            192 -> octets[1].toInt() and 0xFF == 168
            else -> false
        }
    }

    private fun candidateIps(gateway: Inet4Address): List<String> {
        val octets = gateway.address
        if (octets.size != 4) return emptyList()
        val base = "${octets[0].toInt() and 0xFF}.${octets[1].toInt() and 0xFF}.${octets[2].toInt() and 0xFF}"
        val gatewayLast = octets[3].toInt() and 0xFF
        return (2..254)
            .filter { it != gatewayLast }
            .take(MAX_HOSTS_TO_TRY)
            .map { "$base.$it" }
    }

    /**
     * USB tether networks first, then others. Each pair is probed only while bound to that [Network].
     */
    private fun orderedNetworksWithCandidates(cm: ConnectivityManager, context: Context): List<Pair<Network, List<String>>> {
        val usb = mutableListOf<Pair<Network, List<String>>>()
        val other = mutableListOf<Pair<Network, List<String>>>()
        val active = cm.activeNetwork

        try {
            for (network in cm.allNetworks) {
                val lp: LinkProperties = try {
                    cm.getLinkProperties(network)
                } catch (_: Exception) {
                    null
                } ?: continue

                val candidates = LinkedHashSet<String>()

                if (network == active) {
                    getDefaultGateway(context)?.let { gw -> candidateIps(gw).forEach { candidates.add(it) } }
                }

                for (la in lp.linkAddresses) {
                    val a = la.address
                    if (a !is Inet4Address || !isPrivateAddress(a)) continue
                    candidateIpsFromDeviceOnSubnet(a, la.prefixLength).forEach { candidates.add(it) }
                }

                if (candidates.isEmpty()) continue

                val pair = network to candidates.toList()
                if (isLikelyUsbTetherInterface(lp.interfaceName)) {
                    usb.add(pair)
                } else {
                    other.add(pair)
                }
            }
        } catch (_: Exception) {
            // ignore
        }

        return usb + other
    }

    private fun probeHost(host: String): Boolean {
        return try {
            val request = Request.Builder()
                .url("https://$host:$HUD_PORT/health")
                .build()
            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun probeCandidatesBatches(candidates: List<String>): String? {
        if (candidates.isEmpty()) return null
        return coroutineScope {
            for (batch in candidates.chunked(10)) {
                val results = batch.map { ip ->
                    async { if (probeHost(ip)) ip else null }
                }.awaitAll()
                val found = results.firstOrNull { it != null }
                if (found != null) return@coroutineScope found
            }
            null
        }
    }

    suspend fun discover(context: Context): String? = withContext(Dispatchers.IO) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return@withContext null
        val pairs = orderedNetworksWithCandidates(cm, context)
        if (pairs.isEmpty()) return@withContext null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val prior = cm.boundNetworkForProcess
            try {
                for ((network, candidates) in pairs) {
                    if (candidates.isEmpty()) continue
                    if (!cm.bindProcessToNetwork(network)) continue
                    try {
                        val found = probeCandidatesBatches(candidates)
                        if (found != null) return@withContext found
                    } finally {
                        cm.bindProcessToNetwork(null)
                    }
                }
            } finally {
                cm.bindProcessToNetwork(prior)
            }
            null
        } else {
            val flat = pairs.flatMap { it.second }.distinct()
            probeCandidatesBatches(flat)
        }
    }
}
