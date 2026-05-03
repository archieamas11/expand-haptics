package com.hapticks.app.core.haptics

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager


class HapticEngine(context: Context) {
    private val vibrator: Vibrator =
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
            .defaultVibrator
    private val hasVibrator: Boolean = vibrator.hasVibrator()
    private val hasAmplitudeControl: Boolean = vibrator.hasAmplitudeControl()
    private val envelopeSupported: Boolean =
        Build.VERSION.SDK_INT >= 36 && vibrator.areEnvelopeEffectsSupported()

    private val primitiveSupport = BooleanArray(HapticPattern.entries.size)
    private val primitiveQueried = BooleanArray(HapticPattern.entries.size)
    private val cache = arrayOfNulls<VibrationEffect>(
        HapticPattern.entries.size * INTENSITY_BUCKETS
    )

    private val touchAttrs: VibrationAttributes =
        VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)

    private var lastFiredAt: Long = Long.MIN_VALUE

    fun play(
        pattern: HapticPattern,
        intensity: Float,
        throttleMs: Long = 0L,
    ): Boolean {
        if (!hasVibrator || intensity <= MIN_AUDIBLE_INTENSITY) return false

        val clamped = intensity.coerceIn(0f, 1f)

        if (throttleMs > 0L) {
            val now = SystemClock.uptimeMillis()
            if (lastFiredAt != Long.MIN_VALUE && now - lastFiredAt < throttleMs) {
                return false
            }
            lastFiredAt = now
        }

        val bucket = intensityToBucket(clamped)
        val idx = pattern.ordinal * INTENSITY_BUCKETS + bucket

        val effect = cache[idx] ?: createEffect(pattern, bucket).also { cache[idx] = it }

        vibrator.vibrate(effect, touchAttrs)
        return true
    }

    private fun createEffect(pattern: HapticPattern, bucket: Int): VibrationEffect {
        val intensity = bucketToIntensity(bucket)

        return when {
            envelopeSupported && pattern.supportsEnvelope() ->
                buildEnvelopeEffect(pattern, intensity)

            resolvePrimitiveSupport(pattern) ->
                buildCompositionEffect(pattern, intensity)

            else ->
                buildFallbackEffect(pattern, intensity)
        }
    }

    private fun resolvePrimitiveSupport(pattern: HapticPattern): Boolean {
        val idx = pattern.ordinal
        if (!primitiveQueried[idx]) {
            primitiveSupport[idx] = vibrator.areAllPrimitivesSupported(*primitivesRequired(pattern))
            primitiveQueried[idx] = true
        }
        return primitiveSupport[idx]
    }

    private fun buildEnvelopeEffect(pattern: HapticPattern, intensity: Float): VibrationEffect {
        return when (pattern) {
            HapticPattern.CLICK ->
                VibrationEffect.BasicEnvelopeBuilder()
                    .setInitialSharpness(1.0f)
                    .addControlPoint(intensity, 1.0f, 8L)
                    .addControlPoint(0f, 0.8f, 12L)
                    .build()

            HapticPattern.TICK ->
                VibrationEffect.BasicEnvelopeBuilder()
                    .setInitialSharpness(0.9f)
                    .addControlPoint(intensity * 0.85f, 0.9f, 5L)
                    .addControlPoint(0f, 0.7f, 8L)
                    .build()

            HapticPattern.LOW_TICK ->
                VibrationEffect.BasicEnvelopeBuilder()
                    .setInitialSharpness(0.15f)
                    .addControlPoint(intensity * 0.75f, 0.2f, 18L)
                    .addControlPoint(0f, 0.15f, 35L)
                    .build()

            HapticPattern.THUD ->
                VibrationEffect.BasicEnvelopeBuilder()
                    .setInitialSharpness(0.1f)
                    .addControlPoint(intensity, 0.15f, 35L)
                    .addControlPoint(intensity * 0.5f, 0.1f, 80L)
                    .addControlPoint(intensity * 0.15f, 0.1f, 60L)
                    .addControlPoint(0f, 0.1f, 40L)
                    .build()

            HapticPattern.HEAVY_CLICK ->
                VibrationEffect.BasicEnvelopeBuilder()
                    .setInitialSharpness(1.0f)
                    .addControlPoint(intensity, 1.0f, 10L)
                    .addControlPoint(intensity * 0.55f, 0.6f, 25L)
                    .addControlPoint(intensity * 0.2f, 0.4f, 40L)
                    .addControlPoint(0f, 0.3f, 30L)
                    .build()

            HapticPattern.DOUBLE_CLICK ->
                VibrationEffect.BasicEnvelopeBuilder()
                    .setInitialSharpness(1.0f)
                    .addControlPoint(intensity, 1.0f, 8L)
                    .addControlPoint(0f, 0.5f, 55L)
                    .addControlPoint(intensity, 1.0f, 8L)
                    .addControlPoint(0f, 0.5f, 12L)
                    .build()

            HapticPattern.SOFT_BUMP ->
                VibrationEffect.BasicEnvelopeBuilder()
                    .setInitialSharpness(0.2f)
                    .addControlPoint(intensity * 0.65f, 0.25f, 45L)
                    .addControlPoint(0f, 0.2f, 55L)
                    .build()

            HapticPattern.DOUBLE_TICK ->
                VibrationEffect.BasicEnvelopeBuilder()
                    .setInitialSharpness(0.85f)
                    .addControlPoint(intensity * 0.9f, 0.85f, 5L)
                    .addControlPoint(0f, 0.5f, 40L)
                    .addControlPoint(intensity * 0.9f, 0.85f, 5L)
                    .addControlPoint(0f, 0.5f, 8L)
                    .build()

            HapticPattern.ELASTIC ->
                VibrationEffect.BasicEnvelopeBuilder()
                    .setInitialSharpness(0.2f)
                    .addControlPoint(intensity * 0.25f, 0.25f, 45L)
                    .addControlPoint(intensity, 1.0f, 18L)
                    .addControlPoint(0f, 0.7f, 30L)
                    .build()

            HapticPattern.SPIN ->
                VibrationEffect.BasicEnvelopeBuilder()
                    .setInitialSharpness(0.8f)
                    .addControlPoint(intensity * 0.9f, 0.8f, 20L)
                    .addControlPoint(intensity * 0.4f, 0.3f, 25L)
                    .addControlPoint(intensity * 0.6f, 0.8f, 20L)
                    .addControlPoint(intensity * 0.2f, 0.3f, 25L)
                    .addControlPoint(0f, 0.2f, 30L)
                    .build()

            HapticPattern.WOBBLE ->
                VibrationEffect.BasicEnvelopeBuilder()
                    .setInitialSharpness(0.9f)
                    .addControlPoint(intensity * 0.85f, 0.9f, 15L)
                    .addControlPoint(intensity * 0.25f, 0.2f, 20L)
                    .addControlPoint(intensity * 0.6f, 0.9f, 15L)
                    .addControlPoint(intensity * 0.15f, 0.2f, 25L)
                    .addControlPoint(0f, 0.2f, 30L)
                    .build()

            HapticPattern.RAPID_FIRE ->
                VibrationEffect.BasicEnvelopeBuilder()
                    .setInitialSharpness(0.9f)
                    .addControlPoint(intensity * 0.95f, 0.9f, 5L)
                    .addControlPoint(0f, 0.5f, 18L)
                    .addControlPoint(intensity * 0.95f, 0.9f, 5L)
                    .addControlPoint(0f, 0.5f, 18L)
                    .addControlPoint(intensity * 0.95f, 0.9f, 5L)
                    .addControlPoint(0f, 0.5f, 8L)
                    .build()

            HapticPattern.HEARTBEAT ->
                VibrationEffect.BasicEnvelopeBuilder()
                    .setInitialSharpness(0.2f)
                    .addControlPoint(intensity, 0.25f, 25L)
                    .addControlPoint(0f, 0.15f, 100L)
                    .addControlPoint(intensity * 0.45f, 0.2f, 30L)
                    .addControlPoint(0f, 0.15f, 40L)
                    .build()

            HapticPattern.CASCADE ->
                VibrationEffect.BasicEnvelopeBuilder()
                    .setInitialSharpness(0.8f)
                    .addControlPoint(intensity, 0.8f, 20L)
                    .addControlPoint(intensity * 0.6f, 0.55f, 35L)
                    .addControlPoint(intensity * 0.3f, 0.35f, 50L)
                    .addControlPoint(intensity * 0.1f, 0.2f, 40L)
                    .addControlPoint(0f, 0.15f, 35L)
                    .build()

            HapticPattern.RUMBLE ->
                VibrationEffect.BasicEnvelopeBuilder()
                    .setInitialSharpness(0.05f)
                    .addControlPoint(intensity * 0.85f, 0.05f, 60L)
                    .addControlPoint(intensity * 0.85f, 0.05f, 120L)
                    .addControlPoint(0f, 0.05f, 60L)
                    .build()
        }
    }

    private fun buildCompositionEffect(pattern: HapticPattern, intensity: Float): VibrationEffect {
        val comp = VibrationEffect.startComposition()

        when (pattern) {
            HapticPattern.CLICK ->
                comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, intensity)

            HapticPattern.TICK ->
                comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, intensity)

            HapticPattern.LOW_TICK ->
                comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, intensity)

            HapticPattern.THUD -> {
                comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, intensity)
                comp.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
                    intensity * 0.35f, 50
                )
            }

            HapticPattern.SPIN -> {
                comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN, intensity * 0.9f)
                comp.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_SPIN,
                    intensity * 0.5f, 20
                )
                comp.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_SPIN,
                    intensity * 0.2f, 20
                )
            }

            HapticPattern.HEAVY_CLICK -> {
                comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, intensity)
                comp.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                    intensity * 0.55f, 35
                )
            }

            HapticPattern.DOUBLE_CLICK -> {
                comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, intensity)
                comp.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                    intensity, 80
                )
            }

            HapticPattern.SOFT_BUMP ->
                comp.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
                    intensity * 0.6f
                )

            HapticPattern.DOUBLE_TICK -> {
                comp.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, intensity)
                comp.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_TICK,
                    intensity, 60
                )
            }

            HapticPattern.ELASTIC -> {
                comp.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
                    intensity * 0.7f
                )
                comp.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
                    intensity, 40
                )
            }

            HapticPattern.WOBBLE -> {
                comp.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_SPIN,
                    intensity * 0.85f
                )
                comp.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
                    intensity * 0.3f, 25
                )
                comp.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_SPIN,
                    intensity * 0.5f, 20
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
                    intensity * 0.4f, 120
                )
            }

            HapticPattern.CASCADE -> {
                comp.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
                    intensity
                )
                comp.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_TICK,
                    intensity * 0.5f, 60
                )
                comp.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
                    intensity * 0.25f, 50
                )
            }

            HapticPattern.RUMBLE -> {
                comp.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
                    intensity * 0.8f
                )
                comp.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_THUD,
                    intensity * 0.6f, 80
                )
            }
        }

        return comp.compose()
    }

    private fun buildFallbackEffect(pattern: HapticPattern, intensity: Float): VibrationEffect {
        return when (pattern) {
            HapticPattern.CLICK ->
                predefinedOrAmplitude(VibrationEffect.EFFECT_CLICK, intensity)

            HapticPattern.TICK,
            HapticPattern.DOUBLE_TICK,
            HapticPattern.RAPID_FIRE ->
                predefinedOrAmplitude(VibrationEffect.EFFECT_TICK, intensity)

            HapticPattern.LOW_TICK,
            HapticPattern.SOFT_BUMP ->
                oneShot(intensity, 25L)

            HapticPattern.THUD,
            HapticPattern.HEAVY_CLICK,
            HapticPattern.HEARTBEAT ->
                predefinedOrAmplitude(VibrationEffect.EFFECT_HEAVY_CLICK, intensity)

            HapticPattern.SPIN,
            HapticPattern.WOBBLE,
            HapticPattern.CASCADE ->
                predefinedOrAmplitude(VibrationEffect.EFFECT_TICK, intensity)

            HapticPattern.DOUBLE_CLICK,
            HapticPattern.ELASTIC ->
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)

            HapticPattern.RUMBLE ->
                oneShot(intensity, 180L)
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

    private fun intensityToBucket(intensity: Float): Int =
        ((intensity * (INTENSITY_BUCKETS - 1)) + 0.5f).toInt()
            .coerceIn(0, INTENSITY_BUCKETS - 1)

    private fun bucketToIntensity(bucket: Int): Float =
        bucket.toFloat() / (INTENSITY_BUCKETS - 1)

    private fun primitivesRequired(pattern: HapticPattern): IntArray = when (pattern) {
        HapticPattern.CLICK,
        HapticPattern.HEAVY_CLICK,
        HapticPattern.DOUBLE_CLICK -> PRIMS_CLICK

        HapticPattern.TICK,
        HapticPattern.DOUBLE_TICK,
        HapticPattern.RAPID_FIRE -> PRIMS_TICK

        HapticPattern.LOW_TICK,
        HapticPattern.SOFT_BUMP -> PRIMS_LOW_TICK

        HapticPattern.THUD -> PRIMS_THUD_LOWTICK
        HapticPattern.HEARTBEAT -> PRIMS_THUD

        HapticPattern.SPIN,
        HapticPattern.WOBBLE -> PRIMS_SPIN_LOWTICK

        HapticPattern.ELASTIC -> PRIMS_ELASTIC
        HapticPattern.RUMBLE -> PRIMS_RUMBLE
        HapticPattern.CASCADE -> PRIMS_CASCADE
    }

    companion object {
        private const val MIN_AUDIBLE_INTENSITY = 0.01f
        private const val ONE_SHOT_DURATION_MS = 20L
        private const val AMPLITUDE_FALLBACK_THRESHOLD = 0.9f
        private const val INTENSITY_BUCKETS = 21

        private val PRIMS_CLICK = intArrayOf(VibrationEffect.Composition.PRIMITIVE_CLICK)
        private val PRIMS_TICK = intArrayOf(VibrationEffect.Composition.PRIMITIVE_TICK)
        private val PRIMS_LOW_TICK = intArrayOf(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)
        private val PRIMS_THUD = intArrayOf(VibrationEffect.Composition.PRIMITIVE_THUD)
        private val PRIMS_THUD_LOWTICK = intArrayOf(
            VibrationEffect.Composition.PRIMITIVE_THUD,
            VibrationEffect.Composition.PRIMITIVE_LOW_TICK
        )
        private val PRIMS_SPIN_LOWTICK = intArrayOf(
            VibrationEffect.Composition.PRIMITIVE_SPIN,
            VibrationEffect.Composition.PRIMITIVE_LOW_TICK
        )
        private val PRIMS_ELASTIC = intArrayOf(
            VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
            VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
        )
        private val PRIMS_RUMBLE = intArrayOf(
            VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
            VibrationEffect.Composition.PRIMITIVE_THUD
        )
        private val PRIMS_CASCADE = intArrayOf(
            VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
            VibrationEffect.Composition.PRIMITIVE_TICK,
            VibrationEffect.Composition.PRIMITIVE_LOW_TICK
        )
    }
}