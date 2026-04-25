package com.hapticks.app.data

import androidx.compose.runtime.Immutable
import com.hapticks.app.haptics.HapticPattern

@Immutable
data class HapticsSettings(
    val tapEnabled: Boolean = true,
    val intensity: Float = 1.0f,
    val pattern: HapticPattern = HapticPattern.Default,
    val scrollEnabled: Boolean = false,
    val scrollIntensity: Float = 0.45f,
    val scrollPattern: HapticPattern = HapticPattern.TICK,
    val edgePattern: HapticPattern = HapticPattern.TICK,
    val edgeIntensity: Float = 1.0f,
    val a11yScrollBoundEdge: Boolean = false,
    val edgeLsposedLibxposedPath: Boolean = false,

    val useDynamicColors: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val amoledBlack: Boolean = false,
    val seedColor: Int = 0xFF6750A4.toInt(),
) {
    companion object {
        val Default: HapticsSettings = HapticsSettings()
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }
