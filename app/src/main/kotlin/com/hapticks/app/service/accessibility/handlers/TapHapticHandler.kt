package com.hapticks.app.service.accessibility.handlers

import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.hapticks.app.core.haptics.HapticEngine
import com.hapticks.app.data.model.AppSettings

internal class TapHapticController(
    private val engine: HapticEngine,
    private val settingsProvider: () -> AppSettings
) {
    private var lastGlobalTapTime: Long = 0L

    private var lastSourceKey: Long = 0L
    private var lastSourceTapTime: Long = 0L

    fun onEvent(event: AccessibilityEvent) {
        val settings = settingsProvider()
        if (!settings.tapEnabled) return
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return

        val now = SystemClock.uptimeMillis()
        if (now - lastGlobalTapTime < GLOBAL_WINDOW_MS) {
            return
        }

        val sourceKey = event.sourceKey
        if (sourceKey == lastSourceKey && now - lastSourceTapTime < SAME_SOURCE_COOLDOWN_MS) {
            return
        }

        lastGlobalTapTime = now
        lastSourceKey = sourceKey
        lastSourceTapTime = now

        engine.play(settings.pattern, settings.intensity)
    }

    companion object {
        private const val GLOBAL_WINDOW_MS = 120L

        private const val SAME_SOURCE_COOLDOWN_MS = 250L
    }
}