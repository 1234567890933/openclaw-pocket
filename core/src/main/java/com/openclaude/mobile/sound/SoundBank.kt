package com.openclaude.mobile.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.openclaude.mobile.core.R
import com.openclaude.mobile.util.Logs

/**
 * The three interface sounds, and the rules for when they are allowed to play.
 *
 * Rules first, because sound is the easiest way to make an app feel cheap:
 *  - Never on scroll, never on list updates. Only on a deliberate tap.
 *  - Never twice within 60 ms — a fast tapper should not produce a machine-gun.
 *  - Never when the device is on silent, and never when the user turned it off.
 *  - Loaded once, played from memory. Decoding a WAV on the main thread during a tap
 *    is exactly how a "smooth" app drops a frame.
 */
class SoundBank(private val context: Context) {

    private var pool: SoundPool? = null
    private var tap = 0
    private var send = 0
    private var done = 0
    private var loaded = false
    private var lastPlay = 0L

    var enabled: Boolean = true

    fun init() {
        if (pool != null) return
        pool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()

        // Count every load instead of flipping a boolean on the first one: playing a
        // sound that has not finished decoding silently does nothing, which reads as
        // "the tap sound is broken" rather than "it is not ready yet".
        var pending = 3
        pool?.setOnLoadCompleteListener { _, _, status ->
            if (status != 0) Logs.w("sfx", "load failed: $status")
            pending -= 1
            if (pending <= 0) loaded = true
        }

        // A missing resource must not take the app down: R.raw ids are compile-time
        // constants, but a future variant that strips them would make load() return 0,
        // and the guard in play() already handles that.
        tap = pool?.load(context, R.raw.sfx_tap, 1) ?: 0
        send = pool?.load(context, R.raw.sfx_send, 1) ?: 0
        done = pool?.load(context, R.raw.sfx_done, 1) ?: 0
    }

    fun tap() = play(tap)
    fun send() = play(send)
    fun done() = play(done)

    private fun play(id: Int) {
        if (!enabled || !loaded || id == 0) return
        val now = System.currentTimeMillis()
        if (now - lastPlay < 60L) return
        lastPlay = now
        pool?.play(id, 0.35f, 0.35f, 1, 0, 1f)
    }

    /** Called when the process is going away; SoundPool holds native buffers. */
    fun release() {
        pool?.release()
        pool = null
        loaded = false
    }
}
