package com.hapticks.app.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.hapticks.app.features.main.HapticksApp
import com.hapticks.app.service.accessibility.events.isFromOwnApp
import com.hapticks.app.service.accessibility.handlers.TapHapticController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class HapticsAccessibilityService : AccessibilityService() {

    @Volatile
    private var currentSettings = com.hapticks.app.data.model.AppSettings.Default

    private lateinit var tapController: TapHapticController
    private lateinit var scrollController: ScrollHapticController

    private var settingsJob: kotlinx.coroutines.Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val serviceHandler = Handler(Looper.myLooper() ?: Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()

        val app = application as? HapticksApp ?: run {
            disableSelf()
            return
        }

        val engine = app.hapticEngine
        tapController = TapHapticController(engine) { currentSettings }
        scrollController = ScrollHapticController(engine) { currentSettings }

        applyEventMask(com.hapticks.app.data.model.AppSettings.Default)

        settingsJob = app.preferences.settings
            .distinctUntilChanged()
            .onEach { snapshot ->
                serviceHandler.post {
                    currentSettings = snapshot
                    applyEventMask(snapshot)
                }
            }
            .launchIn(serviceScope)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val ev = event ?: return

        if (ev.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED && ev.isFromOwnApp) {
            return
        }

        when (ev.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> tapController.onEvent(ev)
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> scrollController.onEvent(ev)
        }
    }

    override fun onInterrupt() {
        // No-op: haptics are ephemeral and self-limiting.
    }

    override fun onDestroy() {
        settingsJob?.cancel()
        serviceScope.cancel()
        if (::scrollController.isInitialized) scrollController.clear()
        super.onDestroy()
    }

    private fun applyEventMask(settings: com.hapticks.app.data.model.AppSettings) {
        val info = serviceInfo ?: return

        var mask = 0
        if (settings.tapEnabled) {
            mask = mask or AccessibilityEvent.TYPE_VIEW_CLICKED
        }
        if (settings.scrollEnabled || settings.a11yScrollBoundEdge) {
            mask = mask or AccessibilityEvent.TYPE_VIEW_SCROLLED
        }

        if (mask == 0) {
            mask = AccessibilityEvent.TYPE_VIEW_CLICKED
        }

        if (info.eventTypes == mask) return
        info.eventTypes = mask
        serviceInfo = info
    }
}