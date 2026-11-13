package com.openclaude.mobile.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.openclaude.mobile.util.Logs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Dictation, with Hindi as a first-class language.
 *
 * The whole point is that you can talk in Hindi and get Hindi text, so the locale is
 * `hi-IN` by default and the recogniser is asked for partial results — waiting for
 * the final transcript makes the composer feel dead while you speak.
 *
 * Uses the platform recogniser rather than a bundled model: it is already on the
 * phone, it is already in the user's language, and it costs no APK size.
 */
class VoiceInput(private val context: Context) {

    data class State(
        val listening: Boolean = false,
        val partial: String = "",
        val finalText: String = "",
        val error: String? = null,
        val language: String = "hi-IN"
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun toggleLanguage() {
        val next = if (_state.value.language == "hi-IN") "en-IN" else "hi-IN"
        _state.value = _state.value.copy(language = next)
        Logs.i("voice", "language -> $next")
    }

    fun start() {
        if (!isAvailable()) {
            _state.value = _state.value.copy(error = "no speech service on this device")
            return
        }
        stop()

        val engine = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = engine
        engine.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = _state.value.copy(listening = true, error = null, partial = "")
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() {
                _state.value = _state.value.copy(listening = false)
            }

            override fun onError(error: Int) {
                _state.value = _state.value.copy(
                    listening = false,
                    error = describe(error)
                )
                Logs.w("voice", "error ${describe(error)}")
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                _state.value = _state.value.copy(listening = false, finalText = text, partial = "")
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (text.isNotEmpty()) _state.value = _state.value.copy(partial = text)
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, _state.value.language)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, _state.value.language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        runCatching { engine.startListening(intent) }
            .onFailure { _state.value = _state.value.copy(error = it.message) }
    }

    fun stop() {
        runCatching {
            recognizer?.stopListening()
            recognizer?.destroy()
        }
        recognizer = null
        _state.value = _state.value.copy(listening = false)
    }

    fun consumeFinal(): String {
        val text = _state.value.finalText
        _state.value = _state.value.copy(finalText = "", partial = "")
        return text
    }

    private fun describe(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "audio problem"
        SpeechRecognizer.ERROR_CLIENT -> "recogniser client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "microphone permission denied"
        SpeechRecognizer.ERROR_NETWORK -> "network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "did not catch that"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recogniser busy"
        SpeechRecognizer.ERROR_SERVER -> "speech server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no speech detected"
        else -> "recognition error $code"
    }
}
