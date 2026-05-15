package com.hapticks.app.data.model

import androidx.compose.runtime.Immutable
import com.hapticks.app.core.haptics.HapticPattern

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Immutable
data class AppSettings(
    // App internal haptics enabled status
    val hapticsEnabled: Boolean = true,

    // Onboarding
    val hasCompletedOnboarding: Boolean = false,

    // Warning states
    val hasSeenScrollWarning: Boolean = false,

    // Tap Haptics Default Settings
    val tapEnabled: Boolean = true,
    val intensity: Float = 0.2f,
    val pattern: HapticPattern = HapticPattern.Default,

    // Scroll Haptics Default Settings
    val scrollEnabled: Boolean = false,
    val scrollHapticEventsPerHundredPx: Float = 2.2f,
    val scrollIntensity: Float = 0.45f,
    val scrollPattern: HapticPattern = HapticPattern.TICK,

    // Edge Haptics Default Settings
    val edgePattern: HapticPattern = HapticPattern.SOFT_BUMP,
    val edgeIntensity: Float = 1.0f,
    val a11yScrollBoundEdge: Boolean = false,

    // Charge Haptics Default Settings
    val chargeEnabled: Boolean = false,
    val chargeIntensity: Float = 0.8f,
    val chargePattern: HapticPattern = HapticPattern.HEARTBEAT,

    // Theme Default Settings
    val useDynamicColors: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val amoledBlack: Boolean = false,
    val liquidGlass: Boolean = true,
    val seedColor: Int = 0xFF6750A4.toInt(),

    // Update Management
    val lastDismissedUpdateVersion: String? = null,
    val autoCheckUpdates: Boolean = true,
) {
    companion object {
        // For scrolling haptics per distance
        const val MIN_SCROLL_EVENTS_PER_HUNDRED_PX = 0f
        const val MAX_SCROLL_EVENTS_PER_HUNDRED_PX = 20f
        val Default: AppSettings = AppSettings()
    }
}

