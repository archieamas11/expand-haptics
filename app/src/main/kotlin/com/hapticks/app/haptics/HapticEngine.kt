package com.hapticks.app.haptics

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi
import java.util.concurrent.ConcurrentHashMap

class HapticEngine(context: Context) {

    private val vibrator: Vibrator = run {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    }

    private val hasVibrator: Boolean = vibrator.hasVibrator()
    private val hasAmplitudeControl: Boolean = vibrator.hasAmplitudeControl()

    private val hasEnvelopeSupport: Boolean = Build.VERSION.SDK_INT >= 36 &&
            vibrator.areEnvelopeEffectsSupported()

    private val primitiveSupport: Map<HapticPattern, Boolean> =
        HapticPattern.entries.associateWith { pattern ->
            vibrator.areAllPrimitivesSupported(*primitivesRequired(pattern))
        }
    private val compositionCache: ConcurrentHashMap<Int, VibrationEffect> =
        ConcurrentHashMap(HapticPattern.entries.size * INTENSITY_BUCKETS)

    private val envelopeCache: ConcurrentHashMap<Int, VibrationEffect> =
        ConcurrentHashMap(HapticPattern.entries.size * INTENSITY_BUCKETS)

    private val fallbackCache: ConcurrentHashMap<Int, VibrationEffect> =
        ConcurrentHashMap(HapticPattern.entries.size * INTENSITY_BUCKETS)

    private val touchAttrs: VibrationAttributes =
        VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)

    @Volatile
    private var lastFiredAt: Long = Long.MIN_VALUE

    /**
     * Play a haptic pattern with the given intensity (0..1).
     * 
     * @param throttleMs Minimum time between any haptic firing. 
     *                   Set to 0 to disable throttling.
     */
    fun play(
        pattern: HapticPattern,
        intensity: Float,
        throttleMs: Long = 0L,
    ): Boolean {
        if (!hasVibrator) return false
        if (intensity <= MIN_AUDIBLE_INTENSITY) return false
        
        val clamped = intensity.coerceIn(0f, 1f)

        // Throttling
        if (throttleMs > 0L) {
            val now = SystemClock.uptimeMillis()
            val elapsed = if (lastFiredAt == Long.MIN_VALUE) Long.MAX_VALUE else now - lastFiredAt
            if (elapsed < throttleMs) return false
            lastFiredAt = now
        } else {
            lastFiredAt = SystemClock.uptimeMillis()
        }

        val effect = resolveEffect(pattern, clamped)
        vibrator.vibrate(effect, touchAttrs)
        return true
    }

    private fun resolveEffect(pattern: HapticPattern, intensity: Float): VibrationEffect {
        val bucket = intensityToBucket(intensity)
        val key = pattern.ordinal * INTENSITY_BUCKETS + bucket
        val bucketIntensity = bucketToIntensity(bucket)

        if (hasEnvelopeSupport && pattern.supportsEnvelope()) {
            envelopeCache[key]?.let { return it }
            val built = buildEnvelopeEffect(pattern, bucketIntensity)
            return envelopeCache.putIfAbsent(key, built) ?: built
        }

        if (primitiveSupport[pattern] == true) {
            compositionCache[key]?.let { return it }
            val built = buildCompositionEffect(pattern, bucketIntensity)
            return compositionCache.putIfAbsent(key, built) ?: built
        }

        fallbackCache[key]?.let { return it }
        val built = buildFallbackEffect(pattern, bucketIntensity)
        return fallbackCache.putIfAbsent(key, built) ?: built
    }
    
    @RequiresApi(36)
    private fun buildEnvelopeEffect(pattern: HapticPattern, intensity: Float): VibrationEffect {
        return when (pattern) {
            HapticPattern.CLICK -> {
                VibrationEffect.BasicEnvelopeBuilder()
                    .setInitialSharpness(1.0f)
                    .addControlPoint(intensity, 1.0f, 8L)   // attack
                    .addControlPoint(0f, 0.8f, 12L)         // decay
                    .build()
            }

            // Ultra-short, lighter than click. Delicate.
            HapticPattern.TICK -> {
                VibrationEffect.BasicEnvelopeBuilder()
                    .setInitialSharpness(0.9f)
                    .addControlPoint(intensity * 0.85f, 0.9f, 5L)
                    .addControlPoint(0f, 0.7f, 8L)
                    .build()
            }

            // Deep, soft, low-sharpness. Sub-bass character.
            HapticPattern.LOW_TICK -> {
                VibrationEffect.BasicEnvelopeBuilder()
                    .setInitialSharpness(0.15f)
                    .addControlPoint(intensity * 0.75f, 0.2f, 18L)
                    .addControlPoint(0f, 0.15f, 35L)
                    .build()
            }

            // Long resonant decay. Like tapping a hollow wooden box.
            HapticPattern.THUD -> {
                VibrationEffect.BasicEnvelopeBuilder()
                    .setInitialSharpness(0.1f)
                    .addControlPoint(intensity, 0.15f, 35L)          // impact
                    .addControlPoint(intensity * 0.5f, 0.1f, 80L)    // resonance
                    .addControlPoint(intensity * 0.15f, 0.1f, 60L)   // tail
                    .addControlPoint(0f, 0.1f, 40L)
                    .build()
            }

            // Two-phase "thock": sharp attack + rounded secondary bump
            HapticPattern.HEAVY_CLICK -> {
                VibrationEffect.BasicEnvelopeBuilder()
                    .setInitialSharpness(1.0f)
                    .addControlPoint(intensity, 1.0f, 10L)           // primary strike
                    .addControlPoint(intensity * 0.55f, 0.6f, 25L)   // secondary mass
                    .addControlPoint(intensity * 0.2f, 0.4f, 40L)    // decay
                    .addControlPoint(0f, 0.3f, 30L)
                    .build()
            }

            // Two distinct peaks with perceptible gap
            HapticPattern.DOUBLE_CLICK -> {
                VibrationEffect.BasicEnvelopeBuilder()
                    .setInitialSharpness(1.0f)
                    .addControlPoint(intensity, 1.0f, 8L)
                    .addControlPoint(0f, 0.5f, 55L)                  // gap
                    .addControlPoint(intensity, 1.0f, 8L)
                    .addControlPoint(0f, 0.5f, 12L)
                    .build()
            }

            // Gentle rounded hill — no sharp edges
            HapticPattern.SOFT_BUMP -> {
                VibrationEffect.BasicEnvelopeBuilder()
                    .setInitialSharpness(0.2f)
                    .addControlPoint(intensity * 0.65f, 0.25f, 45L)
                    .addControlPoint(0f, 0.2f, 55L)
                    .build()
            }

            // Two tiny peaks, faster than double-click
            HapticPattern.DOUBLE_TICK -> {
                VibrationEffect.BasicEnvelopeBuilder()
                    .setInitialSharpness(0.85f)
                    .addControlPoint(intensity * 0.9f, 0.85f, 5L)
                    .addControlPoint(0f, 0.5f, 40L)
                    .addControlPoint(intensity * 0.9f, 0.85f, 5L)
                    .addControlPoint(0f, 0.5f, 8L)
                    .build()
            }

            // Tension build → sharp release. The "rubber band" feel.
            HapticPattern.ELASTIC -> {
                VibrationEffect.BasicEnvelopeBuilder()
                    .setInitialSharpness(0.2f)
                    .addControlPoint(intensity * 0.25f, 0.25f, 45L)  // stretch (soft)
                    .addControlPoint(intensity, 1.0f, 18L)            // snap (sharp)
                    .addControlPoint(0f, 0.7f, 30L)                   // settle
                    .build()
            }

            // Decelerating rotational feel using oscillating sharpness
            HapticPattern.SPIN -> {
                VibrationEffect.BasicEnvelopeBuilder()
                    .setInitialSharpness(0.8f)
                    .addControlPoint(intensity * 0.9f, 0.8f, 20L)
                    .addControlPoint(intensity * 0.4f, 0.3f, 25L)
                    .addControlPoint(intensity * 0.6f, 0.8f, 20L)
                    .addControlPoint(intensity * 0.2f, 0.3f, 25L)
                    .addControlPoint(0f, 0.2f, 30L)
                    .build()
            }

            // Unstable, oscillating decay
            HapticPattern.WOBBLE -> {
                VibrationEffect.BasicEnvelopeBuilder()
                    .setInitialSharpness(0.9f)
                    .addControlPoint(intensity * 0.85f, 0.9f, 15L)
                    .addControlPoint(intensity * 0.25f, 0.2f, 20L)
                    .addControlPoint(intensity * 0.6f, 0.9f, 15L)
                    .addControlPoint(intensity * 0.15f, 0.2f, 25L)
                    .addControlPoint(0f, 0.2f, 30L)
                    .build()
            }

            // Burst of 3 micro-impacts
            HapticPattern.RAPID_FIRE -> {
                VibrationEffect.BasicEnvelopeBuilder()
                    .setInitialSharpness(0.9f)
                    .addControlPoint(intensity * 0.95f, 0.9f, 5L)
                    .addControlPoint(0f, 0.5f, 18L)
                    .addControlPoint(intensity * 0.95f, 0.9f, 5L)
                    .addControlPoint(0f, 0.5f, 18L)
                    .addControlPoint(intensity * 0.95f, 0.9f, 5L)
                    .addControlPoint(0f, 0.5f, 8L)
                    .build()
            }

            // Lub-dub rhythm
            HapticPattern.HEARTBEAT -> {
                VibrationEffect.BasicEnvelopeBuilder()
                    .setInitialSharpness(0.2f)
                    .addControlPoint(intensity, 0.25f, 25L)           // lub
                    .addControlPoint(0f, 0.15f, 100L)                 // gap
                    .addControlPoint(intensity * 0.45f, 0.2f, 30L)    // dub (softer)
                    .addControlPoint(0f, 0.15f, 40L)
                    .build()
            }

            // Falling sensation: stepped decay
            HapticPattern.CASCADE -> {
                VibrationEffect.BasicEnvelopeBuilder()
                    .setInitialSharpness(0.8f)
                    .addControlPoint(intensity, 0.8f, 20L)
                    .addControlPoint(intensity * 0.6f, 0.55f, 35L)
                    .addControlPoint(intensity * 0.3f, 0.35f, 50L)
                    .addControlPoint(intensity * 0.1f, 0.2f, 40L)
                    .addControlPoint(0f, 0.15f, 35L)
                    .build()
            }

            // Deep, long, sub-bass rumble
            HapticPattern.RUMBLE -> {
                VibrationEffect.BasicEnvelopeBuilder()
                    .setInitialSharpness(0.05f)
                    .addControlPoint(intensity * 0.85f, 0.05f, 60L)   // ramp
                    .addControlPoint(intensity * 0.85f, 0.05f, 120L)  // hold
                    .addControlPoint(0f, 0.05f, 60L)                  // release
                    .build()
            }
        }
    }

    private fun buildCompositionEffect(pattern: HapticPattern, intensity: Float): VibrationEffect {
        val comp = VibrationEffect.startComposition()

        when (pattern) {
            HapticPattern.CLICK -> {
                comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, intensity)
            }

            HapticPattern.TICK -> {
                comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, intensity)
            }

            HapticPattern.LOW_TICK -> {
                comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, intensity)
            }

            HapticPattern.THUD -> {
                // Deep impact + low resonance tail
                comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, intensity)
                comp.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
                    intensity * 0.35f,
                    50
                )
            }

            HapticPattern.SPIN -> {
                // Decelerating whir: three spins at decreasing intensity
                comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, intensity * 0.9f)
                comp.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_SPIN,
                    intensity * 0.5f,
                    20
                )
                comp.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_SPIN,
                    intensity * 0.2f,
                    20
                )
            }

            HapticPattern.HEAVY_CLICK -> {
                // Strong strike + secondary mass
                comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, intensity)
                comp.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                    intensity * 0.55f,
                    35
                )
            }

            HapticPattern.DOUBLE_CLICK -> {
                comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, intensity)
                comp.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                    intensity,
                    80
                )
            }

            HapticPattern.SOFT_BUMP -> {
                comp.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
                    intensity * 0.6f
                )
            }

            HapticPattern.DOUBLE_TICK -> {
                comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, intensity)
                comp.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_TICK,
                    intensity,
                    60
                )
            }

            HapticPattern.ELASTIC -> {
                // Stretch then snap
                comp.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
                    intensity * 0.7f
                )
                comp.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
                    intensity,
                    40
                )
            }

            HapticPattern.WOBBLE -> {
                comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, intensity * 0.85f)
                comp.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
                    intensity * 0.3f,
                    25
                )
                comp.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_SPIN,
                    intensity * 0.5f,
                    20
                )
            }

            HapticPattern.RAPID_FIRE -> {
                comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, intensity)
                comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, intensity, 22)
                comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, intensity, 22)
            }

            HapticPattern.HEARTBEAT -> {
                comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, intensity)
                comp.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_THUD,
                    intensity * 0.4f,
                    120
                )
            }

            HapticPattern.CASCADE -> {
                // Simulated cascade with quick fall + ticks
                comp.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
                    intensity
                )
                comp.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_TICK,
                    intensity * 0.5f,
                    60
                )
                comp.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
                    intensity * 0.25f,
                    50
                )
            }

            HapticPattern.RUMBLE -> {
                // Best approximation: slow rise + thud
                comp.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
                    intensity * 0.8f
                )
                comp.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_THUD,
                    intensity * 0.6f,
                    80
                )
            }
        }

        return comp.compose()
    }

    private fun buildFallbackEffect(pattern: HapticPattern, intensity: Float): VibrationEffect {
        return when (pattern) {
            HapticPattern.CLICK -> predefinedOrAmplitude(VibrationEffect.EFFECT_CLICK, intensity)
            HapticPattern.TICK, HapticPattern.DOUBLE_TICK, HapticPattern.RAPID_FIRE ->
                predefinedOrAmplitude(VibrationEffect.EFFECT_TICK, intensity)

            HapticPattern.LOW_TICK, HapticPattern.SOFT_BUMP ->
                oneShot(intensity, 25L)

            HapticPattern.THUD, HapticPattern.HEAVY_CLICK, HapticPattern.HEARTBEAT ->
                predefinedOrAmplitude(VibrationEffect.EFFECT_HEAVY_CLICK, intensity)

            HapticPattern.SPIN, HapticPattern.WOBBLE, HapticPattern.CASCADE ->
                predefinedOrAmplitude(VibrationEffect.EFFECT_TICK, intensity)

            HapticPattern.DOUBLE_CLICK, HapticPattern.ELASTIC ->
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)

            HapticPattern.RUMBLE -> oneShot(intensity, 180L)
        }
    }

    private fun predefinedOrAmplitude(effectId: Int, intensity: Float): VibrationEffect {
        return if (hasAmplitudeControl && intensity < AMPLITUDE_FALLBACK_THRESHOLD) {
            val amplitude = (intensity * 255f).toInt().coerceIn(1, 255)
            VibrationEffect.createOneShot(ONE_SHOT_DURATION_MS, amplitude)
        } else {
            VibrationEffect.createPredefined(effectId)
        }
    }

    private fun oneShot(intensity: Float, durationMs: Long): VibrationEffect {
        return if (hasAmplitudeControl) {
            val amplitude = (intensity * 255f).toInt().coerceIn(1, 255)
            VibrationEffect.createOneShot(durationMs, amplitude)
        } else {
            val timings = longArrayOf(0, durationMs, 20)
            val amplitudes = intArrayOf(0, (intensity * 255).toInt().coerceIn(1, 255), 0)
            VibrationEffect.createWaveform(timings, amplitudes, -1)
        }
    }

    private fun intensityToBucket(intensity: Float): Int =
        ((intensity * (INTENSITY_BUCKETS - 1)) + 0.5f).toInt()
            .coerceIn(0, INTENSITY_BUCKETS - 1)

    private fun bucketToIntensity(bucket: Int): Float =
        bucket.toFloat() / (INTENSITY_BUCKETS - 1)

    private fun HapticPattern.supportsEnvelope(): Boolean = when (this) {
        HapticPattern.CLICK,
        HapticPattern.TICK,
        HapticPattern.LOW_TICK,
        HapticPattern.THUD,
        HapticPattern.HEAVY_CLICK,
        HapticPattern.DOUBLE_CLICK,
        HapticPattern.SOFT_BUMP,
        HapticPattern.DOUBLE_TICK,
        HapticPattern.ELASTIC,
        HapticPattern.SPIN,
        HapticPattern.WOBBLE,
        HapticPattern.RAPID_FIRE,
        HapticPattern.HEARTBEAT,
        HapticPattern.CASCADE,
        HapticPattern.RUMBLE -> true
    }

    private companion object {
        const val MIN_AUDIBLE_INTENSITY = 0.01f
        const val ONE_SHOT_DURATION_MS = 20L
        const val AMPLITUDE_FALLBACK_THRESHOLD = 0.9f
        const val INTENSITY_BUCKETS = 21

        fun primitivesRequired(pattern: HapticPattern): IntArray = when (pattern) {
            HapticPattern.CLICK,
            HapticPattern.HEAVY_CLICK,
            HapticPattern.DOUBLE_CLICK ->
                intArrayOf(VibrationEffect.Composition.PRIMITIVE_CLICK)

            HapticPattern.TICK,
            HapticPattern.DOUBLE_TICK,
            HapticPattern.RAPID_FIRE,
            HapticPattern.CASCADE ->
                intArrayOf(VibrationEffect.Composition.PRIMITIVE_TICK)

            HapticPattern.LOW_TICK,
            HapticPattern.SOFT_BUMP ->
                intArrayOf(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)

            HapticPattern.THUD,
            HapticPattern.HEARTBEAT ->
                intArrayOf(
                    VibrationEffect.Composition.PRIMITIVE_THUD,
                    VibrationEffect.Composition.PRIMITIVE_LOW_TICK
                )

            HapticPattern.SPIN,
            HapticPattern.WOBBLE ->
                intArrayOf(
                    VibrationEffect.Composition.PRIMITIVE_SPIN,
                    VibrationEffect.Composition.PRIMITIVE_LOW_TICK
                )

            HapticPattern.ELASTIC ->
                intArrayOf(
                    VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
                    VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
                )

            HapticPattern.RUMBLE ->
                intArrayOf(
                    VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
                    VibrationEffect.Composition.PRIMITIVE_THUD
                )
        }
    }
}