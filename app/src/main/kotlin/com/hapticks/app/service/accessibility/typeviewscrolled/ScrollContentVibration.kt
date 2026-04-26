package com.hapticks.app.service.accessibility.typeviewscrolled

import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.hapticks.app.data.HapticsSettings
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

internal object ScrollContentVibration {

    private const val REFERENCE_PX = 100f
    private const val MAX_TRACKED_SURFACES = 128

    /**
     * Ignore tiny steps from touch noise / layout jitter. Slightly higher than 2px so
     * alternating 2px+2px micro-shifts do not stack into meaningful credit.
     */
    private const val NOISE_FLOOR_PX = 4

    /** At most one tick per accessibility event so pulses never stack in the same frame. */

    /** Spacing between emitted ticks (ms). */
    private const val MIN_EMIT_INTERVAL_MS = 55L

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
                lastEventTime = eventTime,
                smoothedVelocityPps = -1f,
                lastHapticEmitUptimeMs = 0L,
                emitAnchorPx = pos.toFloat(),
            )
            evictIfNeeded()
            return Decision.None
        }

        val signedStep = pos - prev.lastPos
        if (signedStep == 0) {
            return Decision.None
        }

        if (abs(signedStep) < NOISE_FLOOR_PX) {
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
        val stepAbs = abs(signedStep).toFloat()
        val instantVelocityPps = stepAbs * 1000f / dtForVelocity.toFloat()
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
        val k = (rate / REFERENCE_PX) * flingScale

        /**
         * Credit is derived from distance to [emitAnchorPx], not summed abs(step).
         * That way rubber-band / bounce (same net scrollY) does not accrue multiple
         * pulses for back-and-forth motion over the same range.
         */
        val signedFromAnchor = pos.toFloat() - prev.emitAnchorPx
        val distFromAnchor = abs(signedFromAnchor)
        val creditsAvailable = distFromAnchor * k

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
        var emitAnchorPx = prev.emitAnchorPx
        if (creditsAvailable >= 1f && canEmitTick) {
            val denom = (rate * flingScale).coerceAtLeast(1e-5f)
            val pxPerCredit = REFERENCE_PX / denom
            val towardPos = if (signedFromAnchor >= 0f) 1f else -1f
            emitAnchorPx += towardPos * pxPerCredit
            pulses = 1
            lastEmit = nowUptime
        }

        perSurface[key] = ContentState(
            lastPos = pos,
            lastEventTime = eventTime,
            smoothedVelocityPps = smoothedV,
            lastHapticEmitUptimeMs = lastEmit,
            emitAnchorPx = emitAnchorPx,
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
        val lastEventTime: Long,
        val smoothedVelocityPps: Float,
        val lastHapticEmitUptimeMs: Long,
        /** Scroll phase reference so credit is not double-counted on reversing motion. */
        val emitAnchorPx: Float,
    )

    sealed class Decision {
        data object None : Decision()
        data class Play(val intensity: Float) : Decision()
    }
}
