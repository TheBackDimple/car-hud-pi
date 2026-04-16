package com.example.carhud.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
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
 * Discovers the Pi on the local network (e.g. USB tether subnet) by probing
 * https://IP:8000/health. Used when Pi host is set to "auto".
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

    /**
     * Generates candidate IPs in the same subnet as the gateway.
     * Excludes the gateway (usually .1) and tries .2, .3, ... first.
     */
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
     * Probes https://host:8000/health. Returns host if it responds with 200.
     */
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

    /**
     * Discovers the Pi on the current network's subnet.
     * Returns the Pi's IP address, or null if not found.
     * Probes candidates in parallel for speed.
     */
    suspend fun discover(context: Context): String? = withContext(Dispatchers.IO) {
        val gateway = getDefaultGateway(context) ?: return@withContext null
        val candidates = candidateIps(gateway)
        var foundIp: String? = null
        coroutineScope {
            for (batch in candidates.chunked(10)) {
                val results = batch.map { ip ->
                    async { if (probeHost(ip)) ip else null }
                }.awaitAll()
                foundIp = results.firstOrNull { it != null }
                if (foundIp != null) break
            }
        }
        foundIp
    }
}
