package com.example.carhud.service

import android.graphics.Bitmap
import android.util.Base64
import com.example.carhud.model.MapFramePayload
import com.google.android.gms.maps.GoogleMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/**
 * Captures Google Map snapshots and sends them over WebSocket.
 * Runs at 2 FPS (500ms interval) when streaming is active.
 */
class MapStreamManager(private val scope: CoroutineScope) {
    var isStreaming = false
        private set

    private var captureJob: Job? = null
    private var jpegQuality = 60
    private var captureIntervalMs = 500L

    fun setQuality(quality: Int) {
        jpegQuality = quality.coerceIn(30, 90)
    }

    fun setCaptureIntervalMs(intervalMs: Long) {
        captureIntervalMs = intervalMs.coerceIn(250, 2000)
    }

    fun startStreaming(googleMap: GoogleMap) {
        if (isStreaming) return
        isStreaming = true
        jpegQuality = MapStreamSettings.quality.value.jpegQuality
        captureIntervalMs = MapStreamSettings.fps.value.intervalMs
        captureJob = scope.launch(Dispatchers.Main) {
            captureLoop(googleMap)
        }
    }

    fun stopStreaming() {
        isStreaming = false
        captureJob?.cancel()
        captureJob = null
    }

    private suspend fun captureLoop(googleMap: GoogleMap) {
        while (scope.isActive && isStreaming) {
            suspendCancellableCoroutine<Unit> { cont ->
                googleMap.snapshot { bitmap ->
                    if (bitmap != null && isStreaming) {
                        scope.launch(Dispatchers.Default) {
                            val payload = bitmapToPayload(bitmap)
                            if (payload != null) {
                                HudConnectionHolder.send(payload.toMessage())
                            }
                        }
                    }
                    cont.resume(Unit)
                }
            }
            kotlinx.coroutines.delay(captureIntervalMs)
        }
    }

    private fun bitmapToPayload(bitmap: Bitmap): MapFramePayload? = runCatching {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, stream)
        val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        MapFramePayload(
            image = base64,
            width = bitmap.width,
            height = bitmap.height,
            timestamp = System.currentTimeMillis()
        )
    }.getOrNull()
}
