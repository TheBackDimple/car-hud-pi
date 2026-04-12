package com.example.carhud.service

import android.app.Application
import android.os.Build
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Speaks turn-by-turn instructions when [VoiceNavigationSettings] is enabled and
 * [NavigationStateHolder] publishes a new step (same source as the HUD).
 */
object NavigationVoiceController {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ttsReady = MutableStateFlow(false)

    private var tts: TextToSpeech? = null
    private var initialized = false

    private var lastSpokenKey: String? = null

    fun init(application: Application) {
        if (initialized) return
        initialized = true

        val appContext = application.applicationContext
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                ttsReady.value = true
            }
        }

        appScope.launch {
            combine(
                VoiceNavigationSettings.isEnabled(appContext),
                NavigationStateHolder.currentStep,
                ttsReady
            ) { enabled, step, ready ->
                Triple(enabled, step, ready)
            }.collect { (enabled, step, ready) ->
                if (!enabled || step == null) {
                    tts?.stop()
                    lastSpokenKey = null
                    return@collect
                }
                val engine = tts
                if (engine == null || !ready) return@collect

                val key = "${step.instruction}|${step.distance}"
                if (key == lastSpokenKey) return@collect
                lastSpokenKey = key

                val utterance = "${step.instruction.trim()}. ${step.distance}"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    engine.speak(utterance, TextToSpeech.QUEUE_FLUSH, null, "carhud_nav")
                } else {
                    @Suppress("DEPRECATION")
                    engine.speak(utterance, TextToSpeech.QUEUE_FLUSH, null)
                }
            }
        }
    }
}
