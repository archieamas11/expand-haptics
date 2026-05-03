package com.hapticks.app.service.accessibility

import android.os.SystemClock
import android.util.LongSparseArray
import android.view.accessibility.AccessibilityEvent
import com.hapticks.app.core.haptics.HapticEngine
import com.hapticks.app.data.model.AppSettings
import kotlin.math.abs

internal class ScrollHapticController(
    private val engine: HapticEngine,
    private val settingsProvider: () -> AppSettings
) {
    private val states = LongSparseArray<ScrollState>(INITIAL_CAPACITY)

    fun onEvent(event: AccessibilityEvent) {
        val settings = settingsProvider()

        val pos: Int
        val max: Int
        when {
            event.maxScrollY > 0 -> {
                pos = event.scrollY.coerceIn(0, event.maxScrollY)
                max = event.maxScrollY
            }

            event.maxScrollX > 0 -> {
                pos = event.scrollX.coerceIn(0, event.maxScrollX)
                max = event.maxScrollX
            }

            event.itemCount > 0 -> {
                val lastIndex = event.itemCount - 1
                pos = when {
                    event.toIndex in 0..lastIndex -> event.toIndex
                    event.fromIndex in 0..lastIndex -> event.fromIndex
                    else -> return
                }
                max = lastIndex
            }

            else -> return
        }

        val key = event.surfaceKey
        val now = SystemClock.uptimeMillis()

        val state = states.get(key) ?: ScrollState().also {
            states.put(key, it)
            if (states.size() > MAX_STATES) trim(now)
        }
        state.lastAccessTime = now

        if (settings.a11yScrollBoundEdge) {
            val edgeConsumed = processEdge(state, pos, max, settings)
            if (edgeConsumed) {
                state.lastPos = pos
                state.lastEventTime = event.eventTime
                state.emitAnchor = pos.toFloat()
                return
            }
        }

        if (settings.scrollEnabled) {
            processContent(state, pos, max, settings, now, event.eventTime)
        }
    }

    fun clear() {
        states.clear()
    }

    private fun processEdge(
        state: ScrollState,
        pos: Int,
        max: Int,
        settings: AppSettings
    ): Boolean {
        if (max <= 0) {
            state.topFired = false
            state.bottomFired = false
            return false
        }

        val hitTop = pos <= 0
        val hitBottom = pos >= max

        val fireTop = hitTop && !state.topFired
        val fireBottom = hitBottom && !state.bottomFired

        state.topFired = hitTop
        state.bottomFired = hitBottom

        return if (fireTop || fireBottom) {
            engine.play(settings.edgePattern, settings.edgeIntensity, throttleMs = EDGE_THROTTLE_MS)
            true
        } else false
    }

    private fun processContent(
        state: ScrollState,
        pos: Int,
        max: Int,
        settings: AppSettings,
        now: Long,
        eventTime: Long
    ) {
        if (state.lastEventTime < 0L) {
            state.lastPos = pos
            state.lastEventTime = eventTime
            state.emitAnchor = pos.toFloat()
            return
        }

        val step = pos - state.lastPos
        if (step == 0) {
            state.lastEventTime = eventTime
            return
        }

        if (abs(step) < NOISE_FLOOR_PX) {
            state.lastPos = pos
            state.lastEventTime = eventTime
            return
        }

        val dt = (eventTime - state.lastEventTime).coerceIn(1L, VELOCITY_DT_CAP_MS)
        val instantV = abs(step) * 1000f / dt
        val smoothedV = if (state.smoothedVelocity < 0f) {
            instantV
        } else {
            state.smoothedVelocity * (1f - VELOCITY_SMOOTHING) + instantV * VELOCITY_SMOOTHING
        }

        val rate = settings.scrollHapticEventsPerHundredPx.coerceIn(
            AppSettings.MIN_SCROLL_EVENTS_PER_HUNDRED_PX,
            AppSettings.MAX_SCROLL_EVENTS_PER_HUNDRED_PX
        )
        val flingScale = flingCreditGainScale(smoothedV)
        val k = (rate / REFERENCE_PX) * flingScale

        val signedFromAnchor = pos - state.emitAnchor
        val credits = abs(signedFromAnchor) * k

        val intensityScale = slowDragIntensityScale(smoothedV)
        val pulseIntensity =
            (settings.scrollIntensity.coerceIn(0f, 1f) * intensityScale).coerceIn(0f, 1f)

        var shouldEmit = false
        if (credits >= 1f) {
            val elapsed = if (state.lastEmitTime == 0L) Long.MAX_VALUE else now - state.lastEmitTime
            if (elapsed >= MIN_EMIT_INTERVAL_MS) {
                val pxPerCredit = REFERENCE_PX / (rate * flingScale).coerceAtLeast(1e-5f)
                val direction = if (signedFromAnchor >= 0f) 1f else -1f
                state.emitAnchor += direction * pxPerCredit
                shouldEmit = true
                state.lastEmitTime = now
            }
        }

        state.lastPos = pos
        state.lastEventTime = eventTime
        state.smoothedVelocity = smoothedV

        if (shouldEmit) {
            engine.play(settings.scrollPattern, pulseIntensity, throttleMs = 0L)
        }
    }

    private fun trim(now: Long) {
        var i = states.size() - 1
        while (i >= 0) {
            if (now - states.valueAt(i).lastAccessTime > STATE_TTL_MS) {
                states.removeAt(i)
            }
            i--
        }
        if (states.size() > MAX_STATES) {
            states.clear()
        }
    }

    private fun flingCreditGainScale(v: Float): Float {
        if (v <= FLING_BLEND_START_PPS) return 1f
        val t = ((v - FLING_BLEND_START_PPS) / (FLING_BLEND_END_PPS - FLING_BLEND_START_PPS))
            .coerceIn(0f, 1f)
        return 1f - (1f - FLING_CREDIT_GAIN_MIN) * t
    }

    private fun slowDragIntensityScale(v: Float): Float {
        val t = (v / SLOW_DRAG_BLEND_PPS).coerceIn(0f, 1f)
        return SLOW_INTENSITY_MIN_SCALE + (1f - SLOW_INTENSITY_MIN_SCALE) * t
    }

    private class ScrollState {
        // Content
        var lastPos: Int = 0
        var lastEventTime: Long = -1L
        var smoothedVelocity: Float = -1f
        var lastEmitTime: Long = 0L
        var emitAnchor: Float = 0f

        // Edge
        var topFired: Boolean = false
        var bottomFired: Boolean = false

        // Eviction
        var lastAccessTime: Long = 0L
    }

    companion object {
        private const val INITIAL_CAPACITY = 64
        private const val MAX_STATES = 128
        private const val STATE_TTL_MS = 30_000L
        private const val EDGE_THROTTLE_MS = 200L

        private const val REFERENCE_PX = 100f
        private const val NOISE_FLOOR_PX = 4
        private const val MIN_EMIT_INTERVAL_MS = 55L
        private const val FLING_BLEND_START_PPS = 900f
        private const val FLING_BLEND_END_PPS = 5200f
        private const val FLING_CREDIT_GAIN_MIN = 0.62f
        private const val SLOW_DRAG_BLEND_PPS = 220f
        private const val SLOW_INTENSITY_MIN_SCALE = 0.38f
        private const val VELOCITY_DT_CAP_MS = 200L
        private const val VELOCITY_SMOOTHING = 0.55f
    }
}