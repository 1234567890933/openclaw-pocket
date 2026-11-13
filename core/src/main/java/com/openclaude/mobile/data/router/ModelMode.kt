package com.openclaude.mobile.data.router

/**
 * Where a turn should run.
 *
 * AUTO is the default and the only one most people will ever use. The other two
 * exist because "keep this on the phone" is a legitimate thing to want and it must
 * be one tap away, not buried in settings.
 */
enum class ModelMode {
    AUTO,
    LOCAL,
    CLOUD;

    val label: String
        get() = when (this) {
            AUTO -> "Auto"
            LOCAL -> "On device"
            CLOUD -> "Cloud"
        }
}
