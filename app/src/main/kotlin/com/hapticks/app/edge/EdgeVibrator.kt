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

object EdgeVibrator {

    private const val TAG = "HapticksEdge"
    const val ACTION_EDGE_HAPTIC = "com.hapticks.app.edge.ACTION_EDGE_HAPTIC"
    const val HAPTICKS_PKG = "com.hapticks.app"
    const val RECEIVER_CLASS = "com.hapticks.app.edge.EdgeHapticReceiver"
    const val EXTRA_EDGE = "edge"
    const val EXTRA_PATTERN = "pattern"

    @Volatile private var cachedEffect: VibrationEffect? = null
    @Volatile private var cachedPattern: HapticPattern? = null
    @Volatile private var cachedIntensity: Float? = null
    @Volatile private var broadcastOnly: Boolean = false
    @Volatile private var touchAttrs: VibrationAttributes? = null

    fun play(
        context: Context,
        edge: Edge,
        pattern: HapticPattern,
        intensity: Float,
        kind: EdgeHapticKind = EdgeHapticKind.FALLBACK,
        intensityScale: Float = 1.0f,
    ) {
        val appCtx = context.applicationContext ?: context
        val finalIntensity = resolveIntensity(kind, intensity, intensityScale)
        if (!broadcastOnly) {
            if (tryDirect(appCtx, pattern, finalIntensity)) return
            broadcastOnly = true
            Log.i(TAG, "Falling back to broadcast vibration for this process")
        }
        sendBroadcast(appCtx, edge, pattern, finalIntensity)
    }

    private fun resolveIntensity(
        kind: EdgeHapticKind,
        baseIntensity: Float,
        intensityScale: Float,
    ): Float {
        val profileScale = when (kind) {
            EdgeHapticKind.PULL -> 0.50f
            EdgeHapticKind.ABSORB -> 1.00f
            EdgeHapticKind.FALLBACK -> 0.65f
        }
        return (baseIntensity * profileScale * intensityScale).coerceIn(0.05f, 1.0f)
    }

    private fun tryDirect(context: Context, pattern: HapticPattern, intensity: Float): Boolean {
        val vibrator = resolveVibrator(context) ?: return false
        if (!vibrator.hasVibrator()) return false
        val effect = if (pattern == cachedPattern && (intensity == cachedIntensity)) {
            cachedEffect ?: buildEdgeEffect(pattern, intensity).also { cachedEffect = it }
        } else {
            buildEdgeEffect(pattern, intensity).also {
                cachedEffect = it
                cachedPattern = pattern
                cachedIntensity = intensity
            }
        }
        val attrs = touchAttrs ?: VibrationAttributes.createForUsage(
            VibrationAttributes.USAGE_TOUCH,
        ).also { touchAttrs = it }
        return try {
            vibrator.vibrate(effect, attrs)
            true
        } catch (_: SecurityException) {
            false
        } catch (t: Throwable) {
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

    private fun buildEdgeEffect(pattern: HapticPattern, intensity: Float): VibrationEffect {
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
            }
            composition.compose()
        } catch (_: Throwable) {
            val effectId = when (pattern) {
                HapticPattern.CLICK -> VibrationEffect.EFFECT_CLICK
                HapticPattern.TICK -> VibrationEffect.EFFECT_TICK
                HapticPattern.HEAVY_CLICK -> VibrationEffect.EFFECT_HEAVY_CLICK
                HapticPattern.DOUBLE_CLICK -> VibrationEffect.EFFECT_DOUBLE_CLICK
                HapticPattern.SOFT_BUMP -> VibrationEffect.EFFECT_TICK
                HapticPattern.DOUBLE_TICK -> VibrationEffect.EFFECT_DOUBLE_CLICK
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
            putExtra(KEY_EDGE_INTENSITY, intensity)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        try {
            context.sendBroadcast(intent)
        } catch (t: Throwable) {
            Log.w(TAG, "Edge haptic broadcast failed", t)
        }
    }

    internal fun resetFallbackForTest() {
        broadcastOnly = false
    }

    fun edgeEffect(pattern: HapticPattern, intensity: Float): VibrationEffect {
        return buildEdgeEffect(pattern, intensity)
    }

    const val KEY_EDGE_INTENSITY = "edge_intensity"
}
