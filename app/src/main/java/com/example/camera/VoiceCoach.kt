package com.example.camera

import android.content.Context
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceCoach(context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var lastSpokenText: String = ""
    private var lastSpeakTimeMs: Long = 0L

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                if (result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    tts?.setSpeechRate(1.05f)
                    isInitialized = true
                }
            }
        }
    }

    fun speak(
        text: String,
        enabled: Boolean,
        force: Boolean = false,
    ) {
        if (!enabled || !isInitialized) return
        val now = SystemClock.elapsedRealtime()
        if (!force) {
            if (text == lastSpokenText && now - lastSpeakTimeMs < 4000L) return
            if (now - lastSpeakTimeMs < 2200L) return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "framelock_${now}")
        lastSpokenText = text
        lastSpeakTimeMs = now
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
