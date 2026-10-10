package com.example.gympartner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Gym Partner AI voice assistant.
 *
 * One manager is kept alive for the whole Activity/app session.
 * It waits for "Hey Gymmy". After activation, commands are sent to the
 * existing Gym Partner AI and the response is spoken back.
 *
 * Important: Android's SpeechRecognizer uses short recognition sessions.
 * Therefore the microphone may briefly stop/start between recognition sessions;
 * this class prevents duplicate managers and uncontrolled restart loops.
 */
class VoiceAssistantManager(
    private val context: Context,
    private val assistantName: String = "Gymmy",
    private val onCommand: (String) -> String
) {
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    private var running = false
    private var conversationActive = false
    private var speaking = false
    private var ttsReady = false
    private var listening = false
    private var restartScheduled = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private val restartRunnable = Runnable {
        restartScheduled = false
        startListeningIfNeeded()
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            listening = true
        }

        override fun onBeginningOfSpeech() {
            listening = true
        }

        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            listening = false
        }

        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onError(error: Int) {
            listening = false
            if (running && !speaking) {
                scheduleRestart()
            }
        }

        override fun onResults(results: Bundle?) {
            listening = false

            if (!running) return

            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()

            if (text.isBlank()) {
                scheduleRestart()
                return
            }

            handleSpeech(text)
        }
    }

    init {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
                it.setRecognitionListener(listener)
            }
        }

        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts?.language = Locale.ENGLISH
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(utteranceId: String?) {
                        mainHandler.post {
                            speaking = false
                            if (running) scheduleRestart()
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        mainHandler.post {
                            speaking = false
                            if (running) scheduleRestart()
                        }
                    }
                })

                if (running) scheduleRestart()
            }
        }
    }

    /** Start the assistant. Safe to call repeatedly; it will not create another listener. */
    fun start() {
        if (!hasMicrophonePermission()) return
        if (running) return

        running = true
        conversationActive = false
        speaking = false
        listening = false
        scheduleRestart(immediate = true)
    }

    /** Completely stop the assistant for the current app session. */
    fun stop() {
        running = false
        conversationActive = false
        speaking = false
        listening = false
        restartScheduled = false
        mainHandler.removeCallbacks(restartRunnable)

        runCatching { recognizer?.cancel() }
        runCatching { tts?.stop() }
    }

    fun release() {
        stop()
        runCatching { recognizer?.destroy() }
        recognizer = null
        runCatching { tts?.shutdown() }
        tts = null
        ttsReady = false
    }

    private fun handleSpeech(text: String) {
        if (!running) return

        val lower = text.lowercase(Locale.getDefault())
        val wakeWord = "hey ${assistantName.lowercase(Locale.getDefault())}"

        // Waiting mode: only the wake phrase is acted upon.
        if (!conversationActive) {
            if (lower.contains(wakeWord)) {
                conversationActive = true
                speak("How can I help you?")
            } else {
                scheduleRestart()
            }
            return
        }

        // End the current conversation, then return to wake-word waiting.
        if (
            lower.contains("stop listening") ||
            lower.contains("goodbye") ||
            lower.contains("go to sleep")
        ) {
            conversationActive = false
            speak("Okay, I'll wait for Hey Gymmy.")
            return
        }

        val response = runCatching { onCommand(text) }
            .getOrElse { "Sorry, I couldn't process that." }

        speak(response)
    }

    private fun scheduleRestart(immediate: Boolean = false) {
        if (!running || speaking || !hasMicrophonePermission()) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        if (recognizer == null) return
        if (listening || restartScheduled) return

        restartScheduled = true
        mainHandler.removeCallbacks(restartRunnable)
        mainHandler.postDelayed(restartRunnable, if (immediate) 0L else 350L)
    }

    private fun startListeningIfNeeded() {
        restartScheduled = false

        if (!running || speaking || listening || !hasMicrophonePermission()) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        if (recognizer == null) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        runCatching {
            recognizer?.startListening(intent)
            listening = true
        }.onFailure {
            listening = false
            scheduleRestart()
        }
    }

    private fun speak(text: String) {
        if (!running) return

        if (text.isBlank() || !ttsReady || tts == null) {
            speaking = false
            scheduleRestart()
            return
        }

        speaking = true
        listening = false
        mainHandler.removeCallbacks(restartRunnable)
        restartScheduled = false

        runCatching {
            tts?.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "GYM_PARTNER_VOICE_RESPONSE"
            )
        }.onFailure {
            speaking = false
            scheduleRestart()
        }
    }

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
}
