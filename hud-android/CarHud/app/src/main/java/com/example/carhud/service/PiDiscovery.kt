package com.example.carhud.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Discovers the Pi on the USB tether (or other local link) by probing
 * https://IP:8000/health. Used when Pi host is set to "auto".
 *
 * Debug: logcat tag **CarHudPiDiscovery** (`adb logcat -s CarHudPiDiscovery`).
 * [lastDiscoveryReport] holds the last run for copy in Settings.
 */
object PiDiscovery {

    private const val TAG = "CarHudPiDiscovery"
    private const val HUD_PORT = 8000
    private const val PROBE_TIMEOUT_MS = 1500L
    /** Scan full /24 minus gateway; USB DHCP can assign high last octets (e.g. .140). */
    private const val MAX_HOSTS_TO_TRY = 253

    /** Last discovery text (for Settings → copy). Updated every [discover] call. */
    @Volatile
    var lastDiscoveryReport: String = ""
        private set

    private val firstProbeErrorLogged = AtomicBoolean(false)

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

    private fun line(sb: StringBuilder, msg: String) {
        Log.i(TAG, msg)
        sb.appendLine(msg)
    }

    /** All host IPs in a /24 given the network address (e.g. 10.157.227.0) or any address in that /24. */
    private fun candidateIpsInSlash24Containing(addr: Inet4Address): List<String> {
        if (!isPrivateAddress(addr)) return emptyList()
        val octets = addr.address
        if (octets.size != 4) return emptyList()
        val base = "${octets[0].toInt() and 0xFF}.${octets[1].toInt() and 0xFF}.${octets[2].toInt() and 0xFF}"
        return (1..254).take(MAX_HOSTS_TO_TRY).map { "$base.$it" }
    }

    /**
     * Routes often carry the tether subnet when [linkAddresses] is empty (OEM-specific).
     */
    private fun addCandidatesFromRoutes(lp: LinkProperties, candidates: MutableSet<String>) {
        for (route in lp.routes) {
            val gw = route.gateway
            if (gw is Inet4Address && isPrivateAddress(gw)) {
                candidateIps(gw).forEach { candidates.add(it) }
            }
            val dest = route.destination ?: continue
            if (dest.prefixLength != 24) continue
            val dAddr = dest.address
            if (dAddr is Inet4Address && isPrivateAddress(dAddr)) {
                candidateIpsInSlash24Containing(dAddr).forEach { candidates.add(it) }
            }
        }
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
    private fun orderedNetworksWithCandidates(cm: ConnectivityManager, report: StringBuilder): List<Pair<Network, List<String>>> {
        val usb = mutableListOf<Pair<Network, List<String>>>()
        val other = mutableListOf<Pair<Network, List<String>>>()
        val nets = try {
            cm.allNetworks.toList()
        } catch (e: Exception) {
            line(report, "allNetworks failed: ${e.message}")
            emptyList()
        }

        line(report, "allNetworks count=${nets.size}")

        try {
            for (network in nets) {
                val lp: LinkProperties = try {
                    cm.getLinkProperties(network)
                } catch (e: Exception) {
                    line(report, "getLinkProperties failed for $network: ${e.message}")
                    null
                } ?: continue

                val iface = lp.interfaceName ?: "?"
                val linksStr = lp.linkAddresses.joinToString("; ") { la ->
                    "${la.address?.hostAddress}/${la.prefixLength}"
                }.ifEmpty { "(none)" }
                val routesStr = lp.routes.joinToString("; ") { r ->
                    val d = r.destination?.toString() ?: "?"
                    val g = r.gateway?.hostAddress ?: "no-gw"
                    "$d -> $g"
                }.ifEmpty { "(none)" }

                val candidates = LinkedHashSet<String>()
                addCandidatesFromRoutes(lp, candidates)

                for (la in lp.linkAddresses) {
                    val a = la.address
                    if (a !is Inet4Address || !isPrivateAddress(a)) continue
                    candidateIpsFromDeviceOnSubnet(a, la.prefixLength).forEach { candidates.add(it) }
                }

                val usbHint = isLikelyUsbTetherInterface(lp.interfaceName)
                line(
                    report,
                    "iface=$iface usbLike=$usbHint candidates=${candidates.size} | links=$linksStr | routes=$routesStr"
                )

                if (candidates.isEmpty()) {
                    line(report, "  -> skip (no private /24 candidates from routes+links)")
                    continue
                }

                val sample = candidates.take(5).joinToString(", ") + if (candidates.size > 5) ", …" else ""
                line(report, "  sample probes: $sample")

                val pair = network to candidates.toList()
                if (usbHint) {
                    usb.add(pair)
                } else {
                    other.add(pair)
                }
            }
        } catch (e: Exception) {
            line(report, "orderedNetworksWithCandidates exception: ${e.stackTraceToString()}")
        }

        val merged = usb + other
        line(report, "probe order: ${usb.size} usb-like group(s), ${other.size} other(s), total networks with candidates=${merged.size}")
        return merged
    }

    private fun probeHost(host: String): Boolean {
        return try {
            val request = Request.Builder()
                .url("https://$host:$HUD_PORT/health")
                .build()
            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            if (firstProbeErrorLogged.compareAndSet(false, true)) {
                Log.w(TAG, "First https health probe failed (example host=$host)", e)
            }
            false
        }
    }

    private suspend fun probeCandidatesBatches(candidates: List<String>, report: StringBuilder): String? {
        if (candidates.isEmpty()) return null
        var batches = 0
        return coroutineScope {
            for (batch in candidates.chunked(10)) {
                batches++
                val results = batch.map { ip ->
                    async { if (probeHost(ip)) ip else null }
                }.awaitAll()
                val found = results.firstOrNull { it != null }
                if (found != null) {
                    line(report, "probe hit on batch $batches: $found")
                    return@coroutineScope found
                }
            }
            line(report, "probed $batches batch(es), no /health 200")
            null
        }
    }

    suspend fun discover(context: Context): String? = withContext(Dispatchers.IO) {
        val report = StringBuilder()
        firstProbeErrorLogged.set(false)

        line(report, "=== Pi discovery ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())} ===")
        line(report, "sdk=${Build.VERSION.SDK_INT} model=${Build.MODEL}")

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            line(report, "ConnectivityManager null")
            lastDiscoveryReport = report.toString()
            return@withContext null
        }

        val priorBound = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cm.boundNetworkForProcess
        } else {
            null
        }
        line(report, "boundNetworkForProcess(before)=${priorBound?.toString() ?: "null"}")

        val pairs = try {
            orderedNetworksWithCandidates(cm, report)
        } catch (e: Exception) {
            line(report, "orderedNetworksWithCandidates: ${e.stackTraceToString()}")
            emptyList()
        }

        if (pairs.isEmpty()) {
            line(report, "RESULT: no networks with candidates (empty list)")
            lastDiscoveryReport = report.toString()
            return@withContext null
        }

        val result: String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            var found: String? = null
            try {
                for ((network, candidates) in pairs) {
                    if (candidates.isEmpty()) continue
                    line(report, "--- binding to $network (${candidates.size} candidates) ---")
                    val ok = cm.bindProcessToNetwork(network)
                    line(report, "bindProcessToNetwork -> $ok")
                    if (!ok) continue
                    try {
                        found = probeCandidatesBatches(candidates, report)
                        if (found != null) break
                    } finally {
                        cm.bindProcessToNetwork(null)
                        line(report, "bindProcessToNetwork(null) after probe")
                    }
                }
            } finally {
                cm.bindProcessToNetwork(priorBound)
                line(report, "restored boundNetworkForProcess -> $priorBound")
            }
            line(report, "RESULT: ${found ?: "not found"}")
            found
        } else {
            val flat = pairs.flatMap { it.second }.distinct()
            line(report, "API<23 flat candidates=${flat.size}")
            val f = probeCandidatesBatches(flat, report)
            line(report, "RESULT: ${f ?: "not found"}")
            f
        }

        lastDiscoveryReport = report.toString()
        result
    }
}
