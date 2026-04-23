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
    /**
     * Whether the user has opted in to the Edge Haptics feature. Orthogonal to the
     * tap-based accessibility path: the LSPosed hook only fires when this is true AND
     * [com.hapticks.app.edge.EdgeHapticsBridge.isAvailable] returns `READY`.
     */
    val edgeEnabled: Boolean = false,
    val edgePattern: HapticPattern = HapticPattern.TICK,
    val edgeIntensity: Float = 1.0f,

    // Theme settings
    val useDynamicColors: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val seedColor: Int = 0xFF6750A4.toInt(),
) {
    companion object {
        val Default: HapticsSettings = HapticsSettings()
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }
