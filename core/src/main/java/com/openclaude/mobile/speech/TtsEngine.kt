package com.openclaude.mobile.speech

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.openclaude.mobile.util.Logs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * Reads an answer out loud.
 *
 * Text-to-speech is one of those features that is either instant or useless: if the
 * first word takes a second to arrive, people stop using it. So the engine is
 * initialised at app start, not on first tap, and a queued utterance is dropped
 * rather than delayed if the engine is still warming.
 *
 * Language is chosen from the text, not from the UI locale. A Hindi answer read with
 * an English voice is worse than not reading it at all, and the detector below is
 * cheap because it only has to answer one question: is this Devanagari?
 */
class TtsEngine(private val context: Context) {

    private var tts: TextToSpeech? = null

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _speaking = MutableStateFlow<String?>(null)
    val speaking: StateFlow<String?> = _speaking.asStateFlow()

    private var preferredHindi: Locale? = null
    private var preferredEnglish: Locale? = null

    fun init() {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts
                if (engine != null) {
                    pickLocales(engine)
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) { }
                        override fun onDone(utteranceId: String?) { _speaking.value = null }
                        @Deprecated("kept for API 21")
                        override fun onError(utteranceId: String?) { _speaking.value = null }
                        override fun onError(utteranceId: String?, errorCode: Int) {
                            Logs.w("tts", "error $errorCode")
                            _speaking.value = null
                        }
                    })
                    _ready.value = true
                    Logs.i("tts", "engine ready")
                }
            } else {
                Logs.w("tts", "engine unavailable ($status)")
                _ready.value = false
            }
        }
    }

    private fun pickLocales(engine: TextToSpeech) {
        val available = runCatching { engine.availableLanguages }.getOrDefault(emptySet())

        preferredHindi = available
            .filterIsInstance<Locale>()
            .firstOrNull { it.language == "hi" }
            ?: Locale("hi", "IN")

        preferredEnglish = available
            .filterIsInstance<Locale>()
            .firstOrNull { it.language == "en" && it.country == "IN" }
            ?: Locale.US

        Logs.i("tts", "hindi=${preferredHindi?.displayName} english=${preferredEnglish?.displayName}")
    }

    /** True when the text is mostly Devanagari. */
    fun isDevanagari(text: String): Boolean {
        val sample = text.take(240)
        if (sample.isBlank()) return false
        val devanagari = sample.count { it.code in 0x0900..0x097F }
        return devanagari > sample.length / 3
    }

    fun speak(text: String, rate: Float = 1.0f) {
        val engine = tts
        if (engine == null || !_ready.value) {
            Logs.w("tts", "not ready, dropping utterance")
            return
        }
        val clean = text
            .replace(Regex("```[\\s\\S]*?```"), " code block ")
            .replace(Regex("[#*_>`]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (clean.isEmpty()) return

        val locale = if (isDevanagari(clean)) preferredHindi else preferredEnglish
        runCatching {
            locale?.let { engine.setLanguage(it) }
            engine.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
            val id = UUID.randomUUID().toString()
            _speaking.value = id
            engine.speak(clean.take(3000), TextToSpeech.QUEUE_FLUSH, null, id)
        }.onFailure {
            Logs.e("tts", "speak failed", it)
            _speaking.value = null
        }
    }

    fun stop() {
        runCatching { tts?.stop() }
        _speaking.value = null
    }

    fun shutdown() {
        runCatching { tts?.shutdown() }
        tts = null
        _ready.value = false
    }

    /** Whether this device has a usable engine at all, for hiding the button. */
    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
}
