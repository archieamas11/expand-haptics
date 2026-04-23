package com.hapticks.app.edge

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the pure-Kotlin state machine. No Android / no XPosed deps.
 *
 * We cover each branch of the decision function:
 *  - first observation always fires
 *  - same edge within the debounce window skips
 *  - same edge after the debounce window fires
 *  - a transition (TOP -> BOTTOM) fires immediately regardless of elapsed time
 */
class EdgeDetectorTest {

    private lateinit var detector: EdgeDetector

    @Before
    fun setUp() {
        detector = EdgeDetector(debounceMs = 500L)
    }

    @Test
    fun `first top edge fires`() {
        assertEquals(EdgeDetector.Decision.FIRE, detector.detect(Edge.TOP, nowMs = 0L))
    }

    @Test
    fun `first bottom edge fires`() {
        assertEquals(EdgeDetector.Decision.FIRE, detector.detect(Edge.BOTTOM, nowMs = 0L))
    }

    @Test
    fun `same edge within debounce window skips`() {
        detector.detect(Edge.TOP, nowMs = 1_000L)
        assertEquals(EdgeDetector.Decision.SKIP, detector.detect(Edge.TOP, nowMs = 1_200L))
        assertEquals(EdgeDetector.Decision.SKIP, detector.detect(Edge.TOP, nowMs = 1_499L))
    }

    @Test
    fun `same edge at exactly the debounce boundary skips`() {
        // The state machine uses strict `> debounceMs`, so equal-to boundary must skip.
        detector.detect(Edge.TOP, nowMs = 1_000L)
        assertEquals(EdgeDetector.Decision.SKIP, detector.detect(Edge.TOP, nowMs = 1_500L))
    }

    @Test
    fun `same edge after debounce window fires again`() {
        detector.detect(Edge.TOP, nowMs = 1_000L)
        assertEquals(EdgeDetector.Decision.FIRE, detector.detect(Edge.TOP, nowMs = 1_501L))
    }

    @Test
    fun `edge transition fires even within debounce window`() {
        detector.detect(Edge.TOP, nowMs = 1_000L)
        assertEquals(EdgeDetector.Decision.FIRE, detector.detect(Edge.BOTTOM, nowMs = 1_050L))
    }

    @Test
    fun `transition resets the debounce clock for the new edge`() {
        detector.detect(Edge.TOP, nowMs = 1_000L)
        detector.detect(Edge.BOTTOM, nowMs = 1_050L)        // fires (transition)
        assertEquals(EdgeDetector.Decision.SKIP, detector.detect(Edge.BOTTOM, nowMs = 1_200L))
        assertEquals(EdgeDetector.Decision.FIRE, detector.detect(Edge.BOTTOM, nowMs = 1_600L))
    }

    @Test
    fun `custom debounce value is honored`() {
        val fast = EdgeDetector(debounceMs = 50L)
        fast.detect(Edge.TOP, nowMs = 0L)
        assertEquals(EdgeDetector.Decision.SKIP, fast.detect(Edge.TOP, nowMs = 40L))
        assertEquals(EdgeDetector.Decision.FIRE, fast.detect(Edge.TOP, nowMs = 60L))
    }

    @Test
    fun `rapid oscillation TOP BOTTOM TOP always fires`() {
        var t = 0L
        assertEquals(EdgeDetector.Decision.FIRE, detector.detect(Edge.TOP, t))
        t += 10
        assertEquals(EdgeDetector.Decision.FIRE, detector.detect(Edge.BOTTOM, t))
        t += 10
        assertEquals(EdgeDetector.Decision.FIRE, detector.detect(Edge.TOP, t))
        t += 10
        assertEquals(EdgeDetector.Decision.FIRE, detector.detect(Edge.BOTTOM, t))
    }

    @Test
    fun `reset clears state so next observation fires`() {
        detector.detect(Edge.TOP, nowMs = 1_000L)
        detector.reset()
        assertEquals(EdgeDetector.Decision.FIRE, detector.detect(Edge.TOP, nowMs = 1_050L))
    }
}
