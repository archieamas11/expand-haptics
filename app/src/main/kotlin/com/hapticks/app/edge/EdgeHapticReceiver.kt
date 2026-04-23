package com.hapticks.app.edge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.VibrationAttributes
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.hapticks.app.HapticksApp
import kotlinx.coroutines.runBlocking

/**
 * Runs inside the Hapticks process (which always holds [android.Manifest.permission.VIBRATE]).
 * Receives the fallback broadcast that [EdgeVibrator] sends from hooked processes that
 * lack VIBRATE and fires the distinct edge haptic locally.
 *
 * The receiver is kept deliberately minimal: it does not touch [com.hapticks.app.haptics.HapticEngine]
 * because that engine is tuned to the tap patterns the user picked in Feel Every Tap.
 * The edge pattern lives in [EdgeVibrator.edgeEffect] so the in-hook path and the
 * fallback path fire an identical `VibrationEffect`.
 */
class EdgeHapticReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != EdgeVibrator.ACTION_EDGE_HAPTIC) return

        // Re-check the user preference inside the receiver. A rogue broadcast from a
        // third-party app should not be able to buzz the device if the user has
        // disabled Edge Haptics. We read the flag synchronously from DataStore via
        // the application singleton — the call is off the hot path of the hook.
        val (enabled, pattern, intensity) = try {
            val app = context.applicationContext as? HapticksApp
            app?.let { runBlocking { it.preferences.getEdgeSettingsOnce() } }
                ?: Triple(false, com.hapticks.app.haptics.HapticPattern.Default, 1.0f)
        } catch (t: Throwable) {
            Log.w(TAG, "edge pref check failed", t)
            Triple(false, com.hapticks.app.haptics.HapticPattern.Default, 1.0f)
        }
        if (!enabled) return

        val vibrator = resolveVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        // Prefer the pattern/intensity from the intent (fresher) but fall back to the one from preferences.
        val intentPatternName = intent.getStringExtra(EdgeVibrator.EXTRA_PATTERN)
        val finalPattern = intentPatternName?.let {
            com.hapticks.app.haptics.HapticPattern.fromStorageKey(it)
        } ?: pattern

        val finalIntensity = intent.getFloatExtra(EdgeVibrator.KEY_EDGE_INTENSITY, intensity)

        val effect = EdgeVibrator.edgeEffect(context, finalPattern, finalIntensity)
        val attrs = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
        try {
            vibrator.vibrate(effect, attrs)
        } catch (t: Throwable) {
            Log.w(TAG, "vibrate failed in receiver", t)
        }
    }

    private fun resolveVibrator(context: Context): Vibrator? = try {
        val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        mgr?.defaultVibrator
    } catch (t: Throwable) {
        null
    }

    private companion object {
        const val TAG = "HapticksEdgeRx"
    }
}
