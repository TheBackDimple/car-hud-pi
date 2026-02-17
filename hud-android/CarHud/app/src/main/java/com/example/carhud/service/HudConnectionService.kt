package com.example.carhud.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.carhud.MainActivity
import com.example.carhud.model.HudMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * Foreground service that maintains WebSocket connection to the Pi.
 * Keeps connection alive when app is backgrounded.
 */
class HudConnectionService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private fun updateState(newState: ConnectionState) {
        HudConnectionHolder.updateState(newState)
    }

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

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
                piHost = intent.getStringExtra(EXTRA_PI_HOST) ?: DEFAULT_PI_HOST
                connect()
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

    private fun connect() {
        if (webSocket != null) {
            disconnect()
        }
        updateState(ConnectionState.Connecting)

        val url = "ws://$piHost:8000/ws?role=android"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                updateState(ConnectionState.Connected)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Server may send connection_status (heartbeat), full_state, etc.
                // No action needed for heartbeat; UI can show "connected"
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Binary not used
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                updateState(ConnectionState.Disconnected)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                updateState(ConnectionState.Disconnected)
                this@HudConnectionService.webSocket = null
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                updateState(ConnectionState.Error(t.message ?: "Connection failed"))
                this@HudConnectionService.webSocket = null
                scheduleReconnect()
            }
        })
    }

    private var reconnectJob: kotlinx.coroutines.Job? = null

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = serviceScope.launch {
            kotlinx.coroutines.delay(RECONNECT_DELAY_MS)
            val current = HudConnectionHolder.state.value
            if (current is ConnectionState.Disconnected || current is ConnectionState.Error) {
                connect()
            }
        }
    }

    private fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
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
            .setContentText("Connected to Raspberry Pi")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "hud_connection"
        private const val NOTIFICATION_ID = 1
        private const val DEFAULT_PI_HOST = "192.168.254.2"
        private const val RECONNECT_DELAY_MS = 5000L

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
