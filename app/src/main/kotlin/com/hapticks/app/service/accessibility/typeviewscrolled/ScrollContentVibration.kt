package com.hapticks.app.service.accessibility.typeviewscrolled

import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.hapticks.app.data.HapticsSettings
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

internal object ScrollContentVibration {

    private const val REFERENCE_PX = 100f
    private const val MAX_TRACKED_SURFACES = 128

    /** Ignore sub-pixel / accidental jitter; real scroll steps are usually larger. */
    private const val NOISE_FLOOR_PX = 3

    /**
     * At most one physical tick per accessibility event so the vibrator is not asked to
     * stack pulses in the same frame; remaining credit carries to the next event.
     */
    private const val MAX_PULSES_PER_EVENT = 1

    /** Spacing between emitted ticks (ms); credit is not consumed while this window is active. */
    private const val MIN_EMIT_INTERVAL_MS = 45L

    /** Above this speed (px/s), credit gain tapers so flings feel less mechanical. */
    private const val FLING_BLEND_START_PPS = 900f
    private const val FLING_BLEND_END_PPS = 5200f
    /** Minimum multiplier on distance→credit at high speed (wider virtual ridges). */
    private const val FLING_CREDIT_GAIN_MIN = 0.62f

    /** Below this speed, ticks stay soft (light contact with the surface). */
    private const val SLOW_DRAG_BLEND_PPS = 220f
    private const val SLOW_INTENSITY_MIN_SCALE = 0.38f

    /** Cap dt when inferring speed so a long pause does not read as ultra-slow drag. */
    private const val VELOCITY_DT_CAP_MS = 200L

    private const val VELOCITY_SMOOTHING = 0.55f

    private val perSurface = ConcurrentHashMap<String, ContentState>()

    fun onViewScrolled(event: AccessibilityEvent, settings: HapticsSettings): Decision {
        val my = event.maxScrollY
        if (my <= 0) {
            return Decision.None
        }

        val pos = event.scrollY.coerceIn(0, my)
        val key = typeViewScrolledSurfaceKey(event) ?: return Decision.None
        val eventTime = event.eventTime

        val prev = perSurface[key]
        if (prev == null) {
            perSurface[key] = ContentState(
                lastPos = pos,
                credit = 0f,
                lastEventTime = eventTime,
                smoothedVelocityPps = -1f,
                lastHapticEmitUptimeMs = 0L,
            )
            evictIfNeeded()
            return Decision.None
        }

        val delta = abs(pos - prev.lastPos)
        if (delta == 0) {
            return Decision.None
        }

        if (delta < NOISE_FLOOR_PX) {
            perSurface[key] = prev.copy(
                lastPos = pos,
                lastEventTime = eventTime,
            )
            return Decision.None
        }

        val dtRaw = if (prev.lastEventTime >= 0L) {
            val d = eventTime - prev.lastEventTime
            if (d > 0L) d else 1L
        } else {
            16L
        }
        val dtForVelocity = dtRaw.coerceIn(1L, VELOCITY_DT_CAP_MS)
        val instantVelocityPps = delta * 1000f / dtForVelocity.toFloat()
        val smoothedV = if (prev.smoothedVelocityPps < 0f) {
            instantVelocityPps
        } else {
            prev.smoothedVelocityPps * (1f - VELOCITY_SMOOTHING) +
                instantVelocityPps * VELOCITY_SMOOTHING
        }

        val rate = settings.scrollHapticEventsPerHundredPx.coerceIn(
            HapticsSettings.MIN_SCROLL_EVENTS_PER_HUNDRED_PX,
            HapticsSettings.MAX_SCROLL_EVENTS_PER_HUNDRED_PX,
        )
        val flingScale = flingCreditGainScale(smoothedV)
        val creditGain = delta * (rate / REFERENCE_PX) * flingScale
        var credit = prev.credit + creditGain

        val baseIntensity = settings.scrollIntensity.coerceIn(0f, 1f)
        val intensityScale = slowDragIntensityScale(smoothedV)
        val pulseIntensity = (baseIntensity * intensityScale).coerceIn(0f, 1f)

        val nowUptime = SystemClock.uptimeMillis()
        val emitElapsed = if (prev.lastHapticEmitUptimeMs == 0L) {
            Long.MAX_VALUE
        } else {
            nowUptime - prev.lastHapticEmitUptimeMs
        }
        val canEmitTick = emitElapsed >= MIN_EMIT_INTERVAL_MS

        var pulses = 0
        var lastEmit = prev.lastHapticEmitUptimeMs
        while (credit >= 1f && pulses < MAX_PULSES_PER_EVENT && canEmitTick) {
            credit -= 1f
            pulses++
            lastEmit = nowUptime
        }

        perSurface[key] = ContentState(
            lastPos = pos,
            credit = credit,
            lastEventTime = eventTime,
            smoothedVelocityPps = smoothedV,
            lastHapticEmitUptimeMs = lastEmit,
        )

        return if (pulses > 0) {
            Decision.Play(intensity = pulseIntensity)
        } else {
            Decision.None
        }
    }

    private fun flingCreditGainScale(velocityPps: Float): Float {
        if (velocityPps <= FLING_BLEND_START_PPS) return 1f
        val span = FLING_BLEND_END_PPS - FLING_BLEND_START_PPS
        val t = ((velocityPps - FLING_BLEND_START_PPS) / span).coerceIn(0f, 1f)
        return 1f - (1f - FLING_CREDIT_GAIN_MIN) * t
    }

    private fun slowDragIntensityScale(velocityPps: Float): Float {
        val t = (velocityPps / SLOW_DRAG_BLEND_PPS).coerceIn(0f, 1f)
        return SLOW_INTENSITY_MIN_SCALE + (1f - SLOW_INTENSITY_MIN_SCALE) * t
    }

    private fun evictIfNeeded() {
        while (perSurface.size > MAX_TRACKED_SURFACES) {
            val drop = perSurface.keys.firstOrNull() ?: return
            perSurface.remove(drop)
        }
    }

    private data class ContentState(
        val lastPos: Int,
        val credit: Float,
        val lastEventTime: Long,
        val smoothedVelocityPps: Float,
        val lastHapticEmitUptimeMs: Long,
    )

    sealed class Decision {
        data object None : Decision()
        data class Play(val intensity: Float) : Decision()
    }
}
