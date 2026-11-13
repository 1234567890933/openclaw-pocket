package com.openclaude.mobile

import android.app.Application
import com.openclaude.mobile.di.ServiceLocator

/**
 * Application entry point. Everything is wired by hand through [ServiceLocator]
 * rather than a DI framework: the graph is small, and keeping it explicit makes
 * the first-boot order (vault -> database -> runtime) obvious to a reader.
 */
class OpenClaudeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // The agent process lives outside the JVM heap; we only drop caches here.
        ServiceLocator.onTrimMemory(level)
    }
}
