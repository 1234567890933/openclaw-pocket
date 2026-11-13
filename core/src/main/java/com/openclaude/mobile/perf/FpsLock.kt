package com.openclaude.mobile.perf

import android.app.Activity
import android.os.Build
import com.openclaude.mobile.util.Logs

/**
 * Caps the app at 24 fps.
 *
 * Nothing here is a game: the fastest thing on screen is a shimmer gradient. A phone
 * rendering at 90 fps to animate a three-dot indicator is spending battery and making
 * heat for no visible benefit, and on a low-end device it also makes frame pacing
 * worse because the GPU never idles.
 *
 * The platform call is invoked reflectively. `Window.setFrameRate` exists from API 30
 * but is not in every SDK stub this project is built against, and a build that fails
 * over a battery optimisation is a bad trade — so it is looked up at runtime and
 * skipped silently when absent.
 */
object FpsLock {

    const val TARGET_FPS = 24

    /** FRAME_RATE_COMPATIBILITY_FIXED_SOURCE */
    private const val FIXED_SOURCE = 1

    fun apply(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Logs.d("fps", "frame-rate hint needs API 30; relying on cheap animations")
            return
        }
        runCatching {
            val method = activity.window.javaClass.getMethod(
                "setFrameRate",
                Float::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            method.invoke(activity.window, TARGET_FPS.toFloat(), FIXED_SOURCE)
            Logs.i("fps", "capped at $TARGET_FPS fps")
        }.onFailure {
            Logs.w("fps", "frame rate hint unavailable: ${it.message}")
        }
    }
}
