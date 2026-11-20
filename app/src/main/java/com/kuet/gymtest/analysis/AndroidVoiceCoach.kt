package com.kuet.gymtest.analysis

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Screen-owned Android speech engine. A missing English voice leaves the workout usable. */
class AndroidVoiceCoach(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var ready = false
    private var closed = false
    private var pending: String? = null

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            main.post {
                if (!closed && status == TextToSpeech.SUCCESS) {
                    ready = (engine?.setLanguage(Locale.US) ?: TextToSpeech.LANG_MISSING_DATA) >= 0
                    if (ready) pending?.let(::speak)
                    pending = null
                }
            }
        }
    }

    fun speak(cue: String) {
        if (closed) return
        if (ready) engine?.speak(cue, TextToSpeech.QUEUE_ADD, null, "workout-coach-${System.nanoTime()}")
        else pending = cue
    }

    fun stop() { pending = null; engine?.stop() }

    fun close() {
        closed = true
        ready = false
        pending = null
        engine?.stop()
        engine?.shutdown()
        engine = null
    }
}
