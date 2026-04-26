package com.hapticks.app.service.accessibility.typeviewinteracted

import android.view.accessibility.AccessibilityEvent
import com.hapticks.app.data.HapticsSettings
import com.hapticks.app.haptics.HapticEngine

/**
 * Haptic feedback driven by view interaction accessibility events: taps/clicks and
 * selection changes commonly used by switches and toggles.
 */
object InteractableViewHaptics {

    /** Bits for [android.accessibilityservice.AccessibilityServiceInfo.eventTypes]. */
    fun eventTypeMask(settings: HapticsSettings): Int {
        if (!settings.tapEnabled) return 0
        return AccessibilityEvent.TYPE_VIEW_CLICKED or AccessibilityEvent.TYPE_VIEW_SELECTED
    }

    /**
     * Handles [AccessibilityEvent.TYPE_VIEW_CLICKED] and [AccessibilityEvent.TYPE_VIEW_SELECTED].
     *
     * Selection events are restricted to switch-like class names so list focus changes do not
     * trigger the same feedback as toggling a switch.
     */
    fun handle(engine: HapticEngine, settings: HapticsSettings, event: AccessibilityEvent): Boolean {
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                if (settings.tapEnabled) {
                    engine.play(settings.pattern, settings.intensity)
                }
                return true
            }
            AccessibilityEvent.TYPE_VIEW_SELECTED -> {
                if (settings.tapEnabled && isLikelySwitchOrToggle(event.className)) {
                    engine.play(settings.pattern, settings.intensity)
                }
                return true
            }
        }
        return false
    }

    private fun isLikelySwitchOrToggle(className: CharSequence?): Boolean {
        val n = className?.toString() ?: return false
        if (n.contains("CheckBox", ignoreCase = true)) return false
        return n.contains("Switch", ignoreCase = true) ||
            n.endsWith("ToggleButton", ignoreCase = true)
    }
}
