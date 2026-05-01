package com.hapticks.app.service.accessibility.handlers

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.LinkedHashMap

internal object ScrollEdgeHapticHandler {

    /**
     * Cap tracked surfaces to prevent unbounded memory growth.
     * LRU eviction via LinkedHashMap(accessOrder=true) removes least-recently-used
     * surface when the cap is exceeded — handles apps with many scroll containers.
     */
    private const val MAX_TRACKED_SURFACES = 128

    private val perSurface = object : LinkedHashMap<String, AbsoluteEdgeState>(
        16, 0.75f, true // accessOrder=true → LRU
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, AbsoluteEdgeState>
        ) = size > MAX_TRACKED_SURFACES
    }

    fun onViewScrolled(event: AccessibilityEvent): Result {
        val key = scrolledSurfaceKey(event) ?: return Result.NoHaptic
        val snap = normalizeAbsoluteSnapshot(event) ?: run {
            perSurface.remove(key)
            return Result.NoHaptic
        }

        val old = perSurface[key] ?: AbsoluteEdgeState()
        val (newState, action) = advanceAbsoluteEdge(old, snap)
        perSurface[key] = newState

        return when (action) {
            AbsoluteEdgeAction.REACHED_TOP,
            AbsoluteEdgeAction.REACHED_BOTTOM -> Result.PlayEdgeHaptic

            null -> Result.NoHaptic
        }
    }

    enum class Result { PlayEdgeHaptic, NoHaptic }
}


private fun normalizeAbsoluteSnapshot(event: AccessibilityEvent): AbsoluteEdgeSnapshot? {
    // Preferred path: pixel-based metrics (most precise when present).
    if (event.maxScrollY > 0) {
        val y = event.scrollY.coerceIn(0, event.maxScrollY)
        return AbsoluteEdgeSnapshot(scrollY = y, maxScrollY = event.maxScrollY)
    }
    // Some apps expose only horizontal scroll metrics.
    if (event.maxScrollX > 0) {
        val x = event.scrollX.coerceIn(0, event.maxScrollX)
        return AbsoluteEdgeSnapshot(scrollY = x, maxScrollY = event.maxScrollX)
    }

    // Compatibility path for apps that expose index-based scrolling only.
    // Many RecyclerView/ListView implementations report from/to/itemCount while
    // leaving scrollY/maxScrollY at -1, so we switch to list-index space.
    val itemCount = event.itemCount
    if (itemCount > 0) {
        val maxIndex = itemCount - 1
        val pos = when {
            event.toIndex >= 0 -> event.toIndex
            event.fromIndex >= 0 -> event.fromIndex
            else -> -1
        }
        if (pos >= 0) {
            return AbsoluteEdgeSnapshot(
                scrollY = pos.coerceIn(0, maxIndex),
                maxScrollY = maxIndex,
            )
        }
    }

    // Last compatibility path: infer edge state from source node scroll actions.
    // Some apps (YouTube/Instagram/Gmail feeds) omit absolute/index metrics but still
    // expose whether forward/backward scroll is currently possible.
    inferSnapshotFromSourceActions(event)?.let { return it }

    return null
}

private fun inferSnapshotFromSourceActions(event: AccessibilityEvent): AbsoluteEdgeSnapshot? {
    val node = event.source ?: return null
    return try {
        val actions = node.actionList ?: return null
        val canForward = actions.any { action ->
            action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD.id
        }
        val canBackward = actions.any { action ->
            action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD.id
        }

        when {
            !canBackward && canForward -> AbsoluteEdgeSnapshot(scrollY = 0, maxScrollY = 1)
            canBackward && !canForward -> AbsoluteEdgeSnapshot(scrollY = 1, maxScrollY = 1)
            else -> null
        }
    } finally {
        if (android.os.Build.VERSION.SDK_INT < 33) {
            try {
                node.recycle()
            } catch (_: Exception) {
            }
        }
    }
}

// ── State machine ────────────────────────────────────────────────────────────

private enum class AbsoluteEdgeAction { REACHED_TOP, REACHED_BOTTOM }

private data class AbsoluteEdgeState(
    val lastScrollY: Int? = null,
    val atTopFired: Boolean = false,
    val atBottomFired: Boolean = false,
)

private data class AbsoluteEdgeSnapshot(
    val scrollY: Int,
    val maxScrollY: Int,
)

/**
 * Pure state-transition function — no side effects, fully testable.
 *
 * Single-fire semantics: edge haptic fires exactly once per edge visit.
 * The latch resets only when the user scrolls away from the edge, preventing
 * repeated haptics during rubber-band overscroll physics at the boundary.
 *
 * Example timeline for bottom edge:
 *   scrollY=480/500 → no action (not at edge)
 *   scrollY=500/500 → REACHED_BOTTOM fired, atBottomFired=true
 *   scrollY=500/500 → no action (already fired, still at edge)
 *   scrollY=480/500 → no action (scrolled away, reset atBottomFired=false)
 *   scrollY=500/500 → REACHED_BOTTOM fired again (new visit)
 */
private fun advanceAbsoluteEdge(
    state: AbsoluteEdgeState,
    snap: AbsoluteEdgeSnapshot,
): Pair<AbsoluteEdgeState, AbsoluteEdgeAction?> {
    val my = snap.maxScrollY
    if (my <= 0) return AbsoluteEdgeState(lastScrollY = snap.scrollY.coerceAtLeast(0)) to null

    val y = snap.scrollY.coerceIn(0, my)

    val hitTop = y == 0
    val hitBottom = y == my

    val topShouldFire = hitTop && !state.atTopFired
    val bottomShouldFire = hitBottom && !state.atBottomFired

    val action = when {
        topShouldFire -> AbsoluteEdgeAction.REACHED_TOP
        bottomShouldFire -> AbsoluteEdgeAction.REACHED_BOTTOM
        else -> null
    }

    // Latch true while at edge; reset to false when scrolled away.
    val newTopFired = if (hitTop) state.atTopFired || topShouldFire else false
    val newBottomFired = if (hitBottom) state.atBottomFired || bottomShouldFire else false

    return AbsoluteEdgeState(
        lastScrollY = y,
        atTopFired = newTopFired,
        atBottomFired = newBottomFired,
    ) to action
}

