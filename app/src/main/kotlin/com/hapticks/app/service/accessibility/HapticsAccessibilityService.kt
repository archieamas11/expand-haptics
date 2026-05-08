package com.hapticks.app.service.accessibility

import android.accessibilityservice.AccessibilityService
import androidx.annotation.Keep
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.hapticks.app.core.haptics.HapticEngine
import com.hapticks.app.data.model.AppSettings
import com.hapticks.app.features.main.HapticksApp
import com.hapticks.app.service.accessibility.events.isFromOwnApp
import com.hapticks.app.service.accessibility.handlers.TapHapticController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.atomic.AtomicReference

@Keep
class HapticsAccessibilityService : AccessibilityService() {
    private val currentSettings = AtomicReference(AppSettings.Default)

    private lateinit var tapController: TapHapticController
    private lateinit var scrollController: ScrollHapticController
    private lateinit var hapticEngine: HapticEngine

    private var settingsJob: kotlinx.coroutines.Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val serviceHandler = Handler(Looper.myLooper() ?: Looper.getMainLooper())

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_POWER_CONNECTED) {
                val settings = currentSettings.get()
                if (settings.chargeEnabled) {
                    hapticEngine.play(
                        settings.chargePattern,
                        settings.chargeIntensity
                    )
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        val app = application as? HapticksApp ?: run {
            disableSelf()
            return
        }

        hapticEngine = app.hapticEngine
        tapController = TapHapticController(hapticEngine) { currentSettings.get() }
        scrollController = ScrollHapticController(hapticEngine) { currentSettings.get() }

        val filter = IntentFilter(Intent.ACTION_POWER_CONNECTED)
        registerReceiver(powerReceiver, filter)

        val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val isCharging = batteryStatus?.let {
            val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        } ?: false

        if (isCharging && currentSettings.get().chargeEnabled) {
            hapticEngine.play(
                currentSettings.get().chargePattern,
                currentSettings.get().chargeIntensity
            )
        }

        applyEventMask(AppSettings.Default)

        settingsJob = app.preferences.settings
            .onEach { snapshot ->
                val old = currentSettings.getAndSet(snapshot)
                if (old.tapEnabled != snapshot.tapEnabled ||
                    old.scrollEnabled != snapshot.scrollEnabled ||
                    old.a11yScrollBoundEdge != snapshot.a11yScrollBoundEdge
                ) {
                    serviceHandler.post {
                        applyEventMask(snapshot)
                    }
                }
            }
            .launchIn(serviceScope)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val ev = event ?: return

        if (ev.isFromOwnApp) return

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
        try {
            unregisterReceiver(powerReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
        if (::scrollController.isInitialized) scrollController.clear()
        super.onDestroy()
    }

    private fun applyEventMask(settings: AppSettings) {
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