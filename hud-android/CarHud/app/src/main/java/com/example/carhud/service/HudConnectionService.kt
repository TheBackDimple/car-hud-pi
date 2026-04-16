package com.example.carhud.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.carhud.CarHudApplication
import com.example.carhud.MainActivity
import com.example.carhud.model.HudMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.security.SecureRandom
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Foreground service that maintains WebSocket connection to the Pi.
 * Sends gps_data when connected, or merged hud_data when a BLE ELM327 adapter is active
 * (see [BleObdProvider]).
 */
class HudConnectionService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var locationProvider: LocationDataProvider? = null

    private val bleObd: BleObdProvider
        get() = (application as CarHudApplication).bleObdProvider

    private fun updateState(newState: ConnectionState) {
        HudConnectionHolder.updateState(newState)
    }

    private val client: OkHttpClient by lazy {
        val trustAllCerts = object : X509TrustManager {
            override fun checkClientTrusted(
                chain: Array<java.security.cert.X509Certificate>,
                authType: String
            ) = Unit

            override fun checkServerTrusted(
                chain: Array<java.security.cert.X509Certificate>,
                authType: String
            ) = Unit

            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> =
                emptyArray()
        }

        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<javax.net.ssl.TrustManager>(trustAllCerts), SecureRandom())
        }

        OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    private var webSocket: WebSocket? = null
    private var piHost: String = DEFAULT_PI_HOST

    override fun onCreate() {
        super.onCreate()
        HudConnectionHolder.registerService(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                startForegroundNotification()
                piHost = (intent.getStringExtra(EXTRA_PI_HOST) ?: DEFAULT_PI_HOST)
                    .replace("carhud_local", "carhud.local")
                if (piHost.equals("auto", ignoreCase = true)) {
                    Log.w(LOG_TAG, "CONNECT: Pi host is auto → PiDiscovery will run")
                    serviceScope.launch { discoverAndConnect() }
                } else {
                    Log.w(LOG_TAG, "CONNECT: Pi host is \"$piHost\" → skip PiDiscovery, WebSocket only")
                    connect()
                }
            }
            ACTION_DISCONNECT -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                disconnect()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun discoverAndConnect() {
        Log.w(LOG_TAG, "discoverAndConnect: starting PiDiscovery.discover()")
        updateState(ConnectionState.Connecting)
        val discovered = withContext(Dispatchers.IO) { PiDiscovery.discover(this@HudConnectionService) }
        if (discovered != null) {
            Log.w(LOG_TAG, "discoverAndConnect: found Pi at $discovered")
            piHost = discovered
            PiHostSettings.setHost(this@HudConnectionService, discovered)
            connect()
        } else {
            Log.w(LOG_TAG, "discoverAndConnect: PiDiscovery returned null")
            updateState(
                ConnectionState.Error(
                    "Could not find the Pi over USB tether. Turn tethering on, plug in the Pi, try again — or enter the Pi IP in Settings. " +
                        "Tip: Settings → Copy last Pi discovery debug log (or adb logcat -s CarHudPiDiscovery)."
                )
            )
        }
    }

    private fun connect() {
        if (webSocket != null) {
            disconnect()
        }
        if (isInvalidHost(piHost)) {
            updateState(ConnectionState.Error("Invalid Pi host \"$piHost\". Use \"auto\" or a valid IP (e.g. 192.168.171.140). .255 is broadcast, not a host."))
            return
        }
        updateState(ConnectionState.Connecting)

        val url = "wss://$piHost:8000/ws?role=android"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                updateState(ConnectionState.Connected)
                startGpsStreaming()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Server may send connection_status (heartbeat), full_state, etc.
                // No action needed for heartbeat; UI can show "connected"
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Binary not used
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                stopGpsStreaming()
                updateState(ConnectionState.Disconnected)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                stopGpsStreaming()
                updateState(ConnectionState.Disconnected)
                this@HudConnectionService.webSocket = null
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                stopGpsStreaming()
                updateState(ConnectionState.Error(t.message ?: "Connection failed"))
                this@HudConnectionService.webSocket = null
                // No auto-reconnect — user must tap Connect again to avoid endless retry loops.
            }
        })
    }

    private fun startGpsStreaming() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        locationProvider?.stopUpdates()
        locationProvider = LocationDataProvider(this) { payload ->
            if (!bleObd.isObdBleActive()) {
                HudConnectionHolder.send(payload.toMessage())
            }
        }
        bleObd.setGpsSupplier { locationProvider?.lastMergedGps }
        locationProvider?.startUpdates(serviceScope)
    }

    private fun stopGpsStreaming() {
        bleObd.setGpsSupplier { null }
        locationProvider?.stopUpdates()
        locationProvider = null
    }

    /** Rejects broadcast (.255), network (.0), and obviously invalid IPs. */
    private fun isInvalidHost(host: String): Boolean {
        if (host.isBlank() || host.equals("auto", ignoreCase = true)) return false
        val m = IPV4_PATTERN.matcher(host)
        if (!m.matches()) return false
        val lastOctet = m.group(4)?.toIntOrNull() ?: return true
        return lastOctet == 0 || lastOctet == 255
    }

    private fun disconnect() {
        stopGpsStreaming()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        updateState(ConnectionState.Disconnected)
    }

    fun send(message: HudMessage) {
        webSocket?.send(message.toJson())
    }

    fun sendJson(json: String) {
        webSocket?.send(json)
    }

    override fun onDestroy() {
        HudConnectionHolder.unregisterService()
        disconnect()
        super.onDestroy()
    }

    private fun startForegroundNotification() {
        val channelId = CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Car HUD Connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Car HUD")
            .setContentText("Connected To Raspberry Pi")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val LOG_TAG = "CarHudConn"
        private val IPV4_PATTERN = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")
        private const val CHANNEL_ID = "hud_connection"
        private const val NOTIFICATION_ID = 1
        private const val DEFAULT_PI_HOST = "auto"

        const val ACTION_CONNECT = "com.example.carhud.CONNECT"
        const val ACTION_DISCONNECT = "com.example.carhud.DISCONNECT"
        const val EXTRA_PI_HOST = "pi_host"

        fun startConnect(context: Context, piHost: String = DEFAULT_PI_HOST) {
            val intent = Intent(context, HudConnectionService::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_PI_HOST, piHost)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, HudConnectionService::class.java)
            context.stopService(intent)
        }
    }
}
