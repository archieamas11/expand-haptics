package com.hapticks.app.data

import com.hapticks.app.haptics.HapticPattern

/**
 * Single source of truth for user-facing haptics configuration.
 *
 * Held as an immutable snapshot so the accessibility service can read a consistent view without
 * synchronizing with the UI thread.
 */
data class HapticsSettings(
    val tapEnabled: Boolean = true,
    val intensity: Float = 1.0f,
    val pattern: HapticPattern = HapticPattern.Default,
) {
    companion object {
        val Default: HapticsSettings = HapticsSettings()
    }
}
