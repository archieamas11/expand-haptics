package com.hapticks.app.data.model

import androidx.compose.runtime.Immutable
import com.hapticks.app.core.haptics.HapticPattern

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Immutable
data class AppSettings(
    // App internal haptics enabled status
    val hapticsEnabled: Boolean = true,

    // Tap Haptics Default Settings
    val tapEnabled: Boolean = true,
    val intensity: Float = 0.2f,
    val pattern: HapticPattern = HapticPattern.Default,

    // Onboarding
    val hasCompletedOnboarding: Boolean = false,

    // Scroll Haptics Default Settings
    val scrollEnabled: Boolean = false,
    val scrollHapticEventsPerHundredPx: Float = 2.2f,
    val scrollIntensity: Float = 0.45f,
    val scrollPattern: HapticPattern = HapticPattern.TICK,

    // Edge Haptics Default Settings
    val edgePattern: HapticPattern = HapticPattern.SOFT_BUMP,
    val edgeIntensity: Float = 1.0f,
    val a11yScrollBoundEdge: Boolean = false,

    // Theme Default Settings
    val useDynamicColors: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val amoledBlack: Boolean = false,
    val liquidGlass: Boolean = true,
    val seedColor: Int = 0xFF6750A4.toInt(),

    // Update Management
    val lastDismissedUpdateVersion: String? = null,
) {
    companion object {
        // For scrolling haptics per distance
        const val MIN_SCROLL_EVENTS_PER_HUNDRED_PX = 0f
        const val MAX_SCROLL_EVENTS_PER_HUNDRED_PX = 20f
        val Default: AppSettings = AppSettings()
    }
}

