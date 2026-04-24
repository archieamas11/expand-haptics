package com.hapticks.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import com.hapticks.app.HapticksApp
import com.hapticks.app.data.HapticsSettings
import com.hapticks.app.haptics.HapticEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class HapticsAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var settingsJob: Job? = null

    @Volatile
    private var current: HapticsSettings = HapticsSettings.Default

    @Volatile private var tapEnabled: Boolean = current.tapEnabled
    @Volatile private var scrollEnabled: Boolean = current.scrollEnabled

    private lateinit var engine: HapticEngine

    override fun onServiceConnected() {
        super.onServiceConnected()
        val app = application as HapticksApp
        engine = app.hapticEngine

        // Apply the initial event mask based on current defaults before we get
        // the first DataStore emission, so we don't receive spurious events.
        applyEventMask(HapticsSettings.Default)

        settingsJob = app.preferences.settings
            .distinctUntilChanged()
            .onEach { snapshot ->
                current = snapshot
                tapEnabled = snapshot.tapEnabled
                scrollEnabled = snapshot.scrollEnabled
                applyEventMask(snapshot)
            }
            .launchIn(scope)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType ?: return

        if (type == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            if (!tapEnabled) return
            val s = current
            engine.play(s.pattern, s.intensity)
            return
        }

        if (type == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            if (!scrollEnabled) return
            val s = current
            // Keep cadence tight enough to feel rhythmic while avoiding motor spam.
            engine.play(s.scrollPattern, s.scrollIntensity, throttleMs = SCROLL_THROTTLE_MS)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        settingsJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun applyEventMask(settings: HapticsSettings) {
        val info = serviceInfo ?: return
        var mask = 0
        if (settings.tapEnabled) {
            mask = mask or AccessibilityEvent.TYPE_VIEW_CLICKED
        }
        if (settings.scrollEnabled) {
            mask = mask or AccessibilityEvent.TYPE_VIEW_SCROLLED
        }

        // Always subscribe to at least one event type. If mask is 0 (both features disabled),
        // keep the current mask — removing all event subscriptions makes the service appear
        // inactive to the system and can cause it to be unbound. The onAccessibilityEvent
        // guard clauses above will still suppress actual haptic output when disabled.
        if (mask == 0) {
            // Subscribe to an innocuous event type as a keepalive; no haptic will fire
            // since both tapEnabled and scrollEnabled are false.
            mask = AccessibilityEvent.TYPE_VIEW_CLICKED
        }

        if (info.eventTypes == mask) return
        info.eventTypes = mask
        serviceInfo = info
    }

    private companion object {
        const val SCROLL_THROTTLE_MS = 42L
    }
}
