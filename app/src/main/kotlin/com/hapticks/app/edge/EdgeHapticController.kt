package com.hapticks.app.edge

import kotlin.math.abs

enum class EdgeHapticKind { PULL, ABSORB, FALLBACK }

data class EdgeHapticDispatch(
    val edge: Edge,
    val kind: EdgeHapticKind,
    val intensityScale: Float,
)

class EdgeHapticController(
    private val isEnabled: () -> Boolean,
    private val minPullThreshold: Float = DEFAULT_PULL_THRESHOLD,
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
) {

    private val states = mutableMapOf(
        Edge.TOP to EdgeState(),
        Edge.BOTTOM to EdgeState(),
    )

    @Volatile private var lastTriggerAtMs: Long = Long.MIN_VALUE

    @Synchronized
    fun onPull(edge: Edge, deltaDistance: Float, nowMs: Long): EdgeHapticDispatch? {
        if (!isEnabled()) return null
        if (abs(deltaDistance) < minPullThreshold) return null

        val state = states.getValue(edge)
        state.isOverscrolling = true
        if (state.hasFiredInInteraction) return null
        if (isInCooldown(nowMs)) return null

        state.hasFiredInInteraction = true
        lastTriggerAtMs = nowMs
        val normalized = ((abs(deltaDistance) - minPullThreshold) / 0.2f).coerceIn(0f, 1f)
        val scale = (0.35f + normalized * 0.25f).coerceIn(0.35f, 0.60f)
        return EdgeHapticDispatch(edge = edge, kind = EdgeHapticKind.PULL, intensityScale = scale)
    }

    @Synchronized
    fun onAbsorb(edge: Edge, velocity: Int, nowMs: Long): EdgeHapticDispatch? {
        if (!isEnabled()) return null

        val state = states.getValue(edge)
        state.isOverscrolling = true
        if (state.hasFiredInInteraction) return null
        if (isInCooldown(nowMs)) return null

        state.hasFiredInInteraction = true
        lastTriggerAtMs = nowMs
        val velocityNorm = (abs(velocity).toFloat() / 10_000f).coerceIn(0f, 1f)
        val scale = (0.85f + velocityNorm * 0.35f).coerceIn(0.85f, 1.20f)
        return EdgeHapticDispatch(edge = edge, kind = EdgeHapticKind.ABSORB, intensityScale = scale)
    }

    @Synchronized
    fun onFallback(edge: Edge, nowMs: Long): EdgeHapticDispatch? {
        if (!isEnabled()) return null
        val state = states.getValue(edge)
        state.isOverscrolling = true
        if (state.hasFiredInInteraction) return null
        if (isInCooldown(nowMs)) return null

        state.hasFiredInInteraction = true
        lastTriggerAtMs = nowMs
        return EdgeHapticDispatch(edge = edge, kind = EdgeHapticKind.FALLBACK, intensityScale = 0.50f)
    }

    @Synchronized
    fun onRelease(edge: Edge) {
        val state = states.getValue(edge)
        state.isOverscrolling = false
        state.hasFiredInInteraction = false
    }

    @Synchronized
    fun onReleaseAll() {
        states.values.forEach {
            it.isOverscrolling = false
            it.hasFiredInInteraction = false
        }
    }

    private fun isInCooldown(nowMs: Long): Boolean {
        val elapsed = nowMs - lastTriggerAtMs
        return elapsed in 0..cooldownMs
    }

    private data class EdgeState(
        var isOverscrolling: Boolean = false,
        var hasFiredInInteraction: Boolean = false,
    )

    companion object {
        const val DEFAULT_PULL_THRESHOLD: Float = 0.015f
        const val DEFAULT_COOLDOWN_MS: Long = 60L
    }
}
