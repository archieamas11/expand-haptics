package com.hapticks.app.edge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EdgeDetectorTest {

    private var enabled = true
    private val controller = EdgeHapticController(
        isEnabled = { enabled },
        minPullThreshold = 0.015f,
        cooldownMs = 60L,
    )

    @Test
    fun `disabled gate blocks all triggers`() {
        enabled = false
        assertNull(controller.onPull(Edge.TOP, deltaDistance = 0.05f, nowMs = 0L))
        assertNull(controller.onAbsorb(Edge.TOP, velocity = 2_000, nowMs = 80L))
        assertNull(controller.onFallback(Edge.TOP, nowMs = 160L))
    }

    @Test
    fun `tiny pull is ignored by threshold`() {
        val dispatch = controller.onPull(Edge.TOP, deltaDistance = 0.005f, nowMs = 0L)
        assertNull(dispatch)
    }

    @Test
    fun `cooldown suppresses rapid repeat across edges`() {
        val first = controller.onFallback(Edge.TOP, nowMs = 0L)
        val blocked = controller.onFallback(Edge.BOTTOM, nowMs = 30L)
        val allowed = controller.onFallback(Edge.BOTTOM, nowMs = 80L)
        assertNotNull(first)
        assertNull(blocked)
        assertNotNull(allowed)
    }

    @Test
    fun `one fire per interaction until release`() {
        val first = controller.onPull(Edge.TOP, deltaDistance = 0.04f, nowMs = 0L)
        val second = controller.onPull(Edge.TOP, deltaDistance = 0.04f, nowMs = 100L)
        assertNotNull(first)
        assertNull(second)
    }

    @Test
    fun `release enables next interaction`() {
        val first = controller.onPull(Edge.TOP, deltaDistance = 0.04f, nowMs = 0L)
        controller.onRelease(Edge.TOP)
        val second = controller.onPull(Edge.TOP, deltaDistance = 0.04f, nowMs = 100L)
        assertNotNull(first)
        assertNotNull(second)
    }

    @Test
    fun `absorb feedback intensity is stronger than pull`() {
        val pull = controller.onPull(Edge.BOTTOM, deltaDistance = 0.06f, nowMs = 0L)
        controller.onRelease(Edge.BOTTOM)
        val absorb = controller.onAbsorb(Edge.BOTTOM, velocity = 8_000, nowMs = 100L)
        assertNotNull(pull)
        assertNotNull(absorb)
        assertEquals(true, absorb!!.intensityScale > pull!!.intensityScale)
        assertEquals(EdgeHapticKind.PULL, pull.kind)
        assertEquals(EdgeHapticKind.ABSORB, absorb.kind)
    }
}
