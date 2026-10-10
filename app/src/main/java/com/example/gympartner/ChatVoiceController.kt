package com.example.gympartner

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

/** One-shot voice input for the chatbot. It only listens after the mic button is tapped. */
class ChatVoiceController(context: Context) {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null
    private var onText: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    init {
        if (SpeechRecognizer.isRecognitionAvailable(appContext)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).also { speechRecognizer ->
                speechRecognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit

                    override fun onResults(results: Bundle?) {
                        val recognized = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()?.trim().orEmpty()
                        if (recognized.isBlank()) {
                            onError?.invoke("I didn't catch that. Tap the mic and try again.")
                        } else {
                            onText?.invoke(recognized)
                        }
                    }

                    override fun onError(error: Int) {
                        val message = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Tap the mic and try again."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                            else -> "Voice recognition failed. Please try again."
                        }
                        onError?.invoke(message)
                    }
                })
            }
        }

        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts?.language = Locale("en", "IN")
                pendingSpeech?.let { speakNow(it) }
            }
        }
    }

    fun listenOnce(onTextRecognized: (String) -> Unit, onFailure: (String) -> Unit) {
        onText = onTextRecognized
        onError = onFailure
        val speechRecognizer = recognizer
        if (speechRecognizer == null) {
            onFailure("Speech recognition isn't available on this device.")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your question in English")
        }
        runCatching { speechRecognizer.startListening(intent) }
            .onFailure { onFailure("Couldn't start the microphone. Please try again.") }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        if (!ttsReady) pendingSpeech = text else speakNow(text)
    }

    private fun speakNow(text: String) {
        pendingSpeech = null
        runCatching { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "GYM_PARTNER_CHAT_REPLY") }
    }

    fun release() {
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        ttsReady = false
        onText = null
        onError = null
    }
}
