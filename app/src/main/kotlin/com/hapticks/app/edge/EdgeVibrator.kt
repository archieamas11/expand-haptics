package com.hapticks.app.edge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.hapticks.app.haptics.HapticPattern

/**
 * Hybrid vibrator used by the LSPosed hook. Tries to vibrate in-process (fastest path),
 * and on the first [SecurityException] flips permanently to an [Intent] broadcast aimed
 * at [EdgeHapticReceiver] in the Hapticks process. The receiver has `VIBRATE` permission
 * and runs the real [com.hapticks.app.haptics.HapticEngine].
 *
 * Why process-local state? The hook is loaded once per target process, so failing over
 * once per process (rather than per-fire) keeps the hot path free of exceptions after
 * the first call in apps without `VIBRATE`.
 */
object EdgeVibrator {

    private const val TAG = "HapticksEdge"
    const val ACTION_EDGE_HAPTIC = "com.hapticks.app.edge.ACTION_EDGE_HAPTIC"
    const val HAPTICKS_PKG = "com.hapticks.app"
    const val RECEIVER_CLASS = "com.hapticks.app.edge.EdgeHapticReceiver"
    const val EXTRA_EDGE = "edge"
    const val EXTRA_PATTERN = "pattern"

    // Memoize the VibrationEffect so repeated edge hits don't reallocate. Built lazily
    // on first use — building a composition touches the vibrator service and we want to
    // pay that only when the feature actually fires.
    @Volatile private var cachedEffect: VibrationEffect? = null
    @Volatile private var cachedPattern: HapticPattern? = null
    @Volatile private var cachedIntensity: Float? = null
    @Volatile private var broadcastOnly: Boolean = false
    @Volatile private var touchAttrs: VibrationAttributes? = null

    /**
     * Fire the edge haptic. Safe to call from any thread; returns quickly even when
     * the broadcast fallback is used (OS queues the dispatch asynchronously).
     */
    fun play(context: Context, edge: Edge, pattern: HapticPattern, intensity: Float) {
        val appCtx = context.applicationContext ?: context
        if (!broadcastOnly) {
            if (tryDirect(appCtx, pattern, intensity)) return
            broadcastOnly = true
            Log.i(TAG, "Falling back to broadcast vibration for this process")
        }
        sendBroadcast(appCtx, edge, pattern, intensity)
    }

    private fun tryDirect(context: Context, pattern: HapticPattern, intensity: Float): Boolean {
        val vibrator = resolveVibrator(context) ?: return false
        if (!vibrator.hasVibrator()) return false
        val effect = if (pattern == cachedPattern && intensity == cachedIntensity) {
            cachedEffect ?: buildEdgeEffect(vibrator, pattern, intensity).also { cachedEffect = it }
        } else {
            buildEdgeEffect(vibrator, pattern, intensity).also {
                cachedEffect = it
                cachedPattern = pattern
                cachedIntensity = intensity
            }
        }
        val attrs = touchAttrs ?: VibrationAttributes.createForUsage(
            VibrationAttributes.USAGE_TOUCH
        ).also { touchAttrs = it }
        return try {
            vibrator.vibrate(effect, attrs)
            true
        } catch (se: SecurityException) {
            // No VIBRATE in this target process. Expected for most third-party apps.
            false
        } catch (t: Throwable) {
            // Some OEM vibrator services throw RemoteException-wrapped errors; treat as
            // a transient miss and retry on the next edge hit (do not disable).
            Log.w(TAG, "vibrate() threw; retrying next edge", t)
            true
        }
    }

    private fun resolveVibrator(context: Context): Vibrator? {
        return try {
            val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                    as? VibratorManager
            mgr?.defaultVibrator
        } catch (t: Throwable) {
            null
        }
    }

    private fun buildEdgeEffect(vibrator: Vibrator, pattern: HapticPattern, intensity: Float): VibrationEffect {
        return try {
            val composition = VibrationEffect.startComposition()
            when (pattern) {
                HapticPattern.CLICK ->
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, intensity)
                HapticPattern.TICK ->
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, intensity)
                HapticPattern.HEAVY_CLICK -> {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, intensity)
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, intensity)
                }
                HapticPattern.DOUBLE_CLICK -> {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, intensity)
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, intensity, 80)
                }
                HapticPattern.SOFT_BUMP ->
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, intensity)
                HapticPattern.DOUBLE_TICK -> {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, intensity)
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, intensity, 60)
                }
                HapticPattern.TENSION_RELEASE -> {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, intensity)
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, intensity)
                }
            }
            composition.compose()
        } catch (t: Throwable) {
            val effectId = when (pattern) {
                HapticPattern.CLICK -> VibrationEffect.EFFECT_CLICK
                HapticPattern.TICK -> VibrationEffect.EFFECT_TICK
                HapticPattern.HEAVY_CLICK -> VibrationEffect.EFFECT_HEAVY_CLICK
                HapticPattern.DOUBLE_CLICK -> VibrationEffect.EFFECT_DOUBLE_CLICK
                HapticPattern.SOFT_BUMP -> VibrationEffect.EFFECT_TICK
                HapticPattern.DOUBLE_TICK -> VibrationEffect.EFFECT_DOUBLE_CLICK
                HapticPattern.TENSION_RELEASE -> VibrationEffect.EFFECT_HEAVY_CLICK
            }
            VibrationEffect.createPredefined(effectId)
        }
    }

    private fun sendBroadcast(context: Context, edge: Edge, pattern: HapticPattern, intensity: Float) {
        val intent = Intent(ACTION_EDGE_HAPTIC).apply {
            component = ComponentName(HAPTICKS_PKG, RECEIVER_CLASS)
            setPackage(HAPTICKS_PKG)
            putExtra(EXTRA_EDGE, edge.name)
            putExtra(EXTRA_PATTERN, pattern.name)
            putExtra(KEY_EDGE_INTENSITY, intensity) // Using KEY_EDGE_INTENSITY for simplicity or define another constant
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        try {
            context.sendBroadcast(intent)
        } catch (t: Throwable) {
            Log.w(TAG, "Edge haptic broadcast failed", t)
        }
    }

    /** Reset-for-test helper used by [EdgeHapticsBridge.testEdgeHaptic]. */
    internal fun resetFallbackForTest() {
        broadcastOnly = false
    }

    /**
     * Build and return a one-shot edge VibrationEffect suitable for testing in-app
     * (inside the Hapticks process, which always holds VIBRATE). Decoupled from the
     * cache so the real vibrator instance the caller uses can be any they want.
     */
    fun edgeEffect(context: Context, pattern: HapticPattern, intensity: Float): VibrationEffect {
        val vibrator = resolveVibrator(context) ?: throw IllegalStateException("No vibrator")
        return buildEdgeEffect(vibrator, pattern, intensity)
    }

    const val KEY_EDGE_INTENSITY = "edge_intensity"
}
