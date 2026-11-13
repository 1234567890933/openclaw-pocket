package com.openclaude.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.openclaude.mobile.ui.RootScreen
import com.openclaude.mobile.ui.theme.OpenClaudeTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.openclaude.mobile.perf.FpsLock.apply(this)
        setContent {
            OpenClaudeTheme {
                RootScreen()
            }
        }
    }

    /**
     * Floating window.
     *
     * When the user leaves the app, it shrinks into picture-in-picture instead of
     * disappearing — the conversation stays visible and the agent keeps running.
     * This is the sanctioned form of a floating window: no overlay permission, no
     * draw-over-other-apps, and the system can still kill it under memory pressure.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        if (isInPictureInPictureMode) return
        runCatching {
            val params = android.app.PictureInPictureParams.Builder()
                .setAspectRatio(android.util.Rational(9, 16))
                .build()
            enterPictureInPictureMode(params)
        }.onFailure { /* device refused PiP; nothing to do */ }
    }
}
