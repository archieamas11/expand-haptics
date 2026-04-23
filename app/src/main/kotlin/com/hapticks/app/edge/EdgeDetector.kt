package com.hapticks.app.edge

/**
 * Which extremity of a scroll container the user has reached.
 */
enum class Edge { TOP, BOTTOM }

/**
 * Process-wide state machine that dedupes edge haptics so the user feels exactly one
 * pulse when scroll settles at an extreme, even though the underlying scroll container
 * can emit many `canScrollVertically == false` samples in a row (RecyclerView's idle
 * state may trigger twice, WebView's scroll callback can fire on every pixel, etc.).
 *
 * Fires only when:
 *  - no edge has ever fired yet, OR
 *  - the current edge differs from the last fired edge (`TOP <-> BOTTOM` transition), OR
 *  - more than [debounceMs] have elapsed since the last fire.
 *
 * Pure Kotlin: accepts `nowMs` so tests can drive the machine without touching
 * [android.os.SystemClock]. The hot path takes one monitor acquire and two volatile
 * writes, so the synchronized block is fine at scroll-callback frequencies.
 */
class EdgeDetector(
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
) {

    enum class Decision { FIRE, SKIP }

    @Volatile private var lastEdge: Edge? = null
    @Volatile private var lastFiredAtMs: Long = Long.MIN_VALUE

    /**
     * Decide whether [edge] should trigger a haptic at [nowMs] and record the decision.
     * Returns [Decision.FIRE] iff the caller should dispatch the vibration.
     */
    @Synchronized
    fun detect(edge: Edge, nowMs: Long): Decision {
        val prev = lastEdge
        val elapsed = nowMs - lastFiredAtMs
        val shouldFire = prev == null || prev != edge || elapsed > debounceMs
        if (!shouldFire) return Decision.SKIP
        lastEdge = edge
        lastFiredAtMs = nowMs
        return Decision.FIRE
    }

    /** Visible for tests so each test starts from a clean slate. */
    @Synchronized
    internal fun reset() {
        lastEdge = null
        lastFiredAtMs = Long.MIN_VALUE
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MS: Long = 500L

        /** Shared instance used by the in-process LSPosed hooks. */
        val Shared: EdgeDetector = EdgeDetector()
    }
}
