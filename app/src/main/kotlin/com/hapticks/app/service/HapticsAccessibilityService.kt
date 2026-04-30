package com.hapticks.app.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.hapticks.app.HapticksApp
import com.hapticks.app.data.AppSettings
import com.hapticks.app.haptics.HapticEngine
import com.hapticks.app.service.accessibility.isAccessibilityEventFromOwnApplication
import com.hapticks.app.service.accessibility.interacted.InteractableViewHaptics
import com.hapticks.app.service.accessibility.scrolled.ScrollAbsoluteEdgeVibration
import com.hapticks.app.service.accessibility.scrolled.ScrollContentVibration
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class HapticsAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var settingsJob: Job? = null

    @Volatile
    private var current: AppSettings = AppSettings.Default

    private lateinit var engine: HapticEngine

    private val typeClicked = AccessibilityEvent.TYPE_VIEW_CLICKED
    private val typeWindowChanged = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
    private val typeScrolled = AccessibilityEvent.TYPE_VIEW_SCROLLED

    override fun onServiceConnected() {
        super.onServiceConnected()
        val app = application as HapticksApp
        engine = app.hapticEngine

        applyEventMask(AppSettings.Default)

        settingsJob = app.preferences.settings
            .distinctUntilChanged()
            .onEach { snapshot ->
                current = snapshot
                applyEventMask(snapshot)
            }
            .launchIn(scope)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val ev = event ?: return

        if (ev.eventType != typeScrolled) {
            if (isAccessibilityEventFromOwnApplication(ev)) return
        }

        val type = ev.eventType
        val settings = current

        if (type == typeClicked) {
            InteractableViewHaptics.handle(engine, settings, ev)
            return
        }

        if (type == typeWindowChanged) {
            if (settings.tapEnabled && InteractableViewHaptics.hasToggleLikeContentChange(ev)) {
                InteractableViewHaptics.handle(engine, settings, ev)
            }
            return
        }

        if (type == typeScrolled) {
            var consumedByEdge = false
            if (settings.a11yScrollBoundEdge) {
                if (ScrollAbsoluteEdgeVibration.onViewScrolled(ev) ==
                    ScrollAbsoluteEdgeVibration.Result.PlayEdgeHaptic
                ) {
                    engine.play(settings.edgePattern, settings.edgeIntensity, throttleMs = EDGE_THROTTLE_MS)
                    consumedByEdge = true
                }
            }
            if (settings.scrollEnabled && !consumedByEdge) {
                when (val scroll = ScrollContentVibration.onViewScrolled(ev, settings)) {
                    is ScrollContentVibration.Decision.Play -> {
                        engine.play(
                            settings.scrollPattern,
                            scroll.intensity,
                            throttleMs = 0L,
                        )
                    }
                    ScrollContentVibration.Decision.None -> Unit
                }
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        settingsJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun applyEventMask(settings: AppSettings) {
        val info = serviceInfo ?: return
        var mask = InteractableViewHaptics.eventTypeMask(settings)
        if (settings.scrollEnabled || settings.a11yScrollBoundEdge) {
            mask = mask or typeScrolled
        }
        if (mask == 0) mask = typeClicked
        if (info.eventTypes == mask) return
        info.eventTypes = mask
        serviceInfo = info
    }

    private companion object {
        const val EDGE_THROTTLE_MS = 200L
    }
}