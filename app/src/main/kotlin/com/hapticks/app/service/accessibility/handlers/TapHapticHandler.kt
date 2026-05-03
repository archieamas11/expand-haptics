package com.hapticks.app.service.accessibility

import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.hapticks.app.core.haptics.HapticEngine
import com.hapticks.app.data.model.AppSettings

internal class TapHapticController(
    private val engine: HapticEngine,
    private val settingsProvider: () -> AppSettings
) {
    private var lastTapTime = 0L

    fun onEvent(event: AccessibilityEvent) {
        val settings = settingsProvider()
        if (!settings.tapEnabled) return
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return

        val now = SystemClock.uptimeMillis()
        if (now - lastTapTime < TAP_COOLDOWN_MS) return
        lastTapTime = now

        engine.play(settings.pattern, settings.intensity)
    }

    companion object {
        /** Prevents vibration queue saturation during rapid interactions. */
        private const val TAP_COOLDOWN_MS = 45L
    }
}