package com.afu.ledger.auto.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotThrottleTest {

    private var now = 1_000L
    private val throttle = ScreenshotThrottle(clock = { now })

    private fun capture(kind: ScreenshotThrottle.EventKind, screenOn: Boolean = true) =
        throttle.shouldCapture(kind, screenInteractive = screenOn, overlayShowing = false)

    @Test
    fun `内容变化在间隔内只放行一次`() {
        assertTrue(capture(ScreenshotThrottle.EventKind.CONTENT_CHANGED))

        now += 1_000
        assertFalse(capture(ScreenshotThrottle.EventKind.CONTENT_CHANGED))
        assertEquals(ScreenshotThrottle.Decision.THROTTLED, throttle.lastDecision)

        now += 7_000
        assertTrue(capture(ScreenshotThrottle.EventKind.CONTENT_CHANGED))
    }

    @Test
    fun `窗口状态变化允许更短的间隔`() {
        assertTrue(capture(ScreenshotThrottle.EventKind.WINDOW_STATE_CHANGED))

        now += 1_600
        assertTrue(capture(ScreenshotThrottle.EventKind.WINDOW_STATE_CHANGED))
    }

    @Test
    fun `连续未命中会把间隔放大`() {
        repeat(3) {
            assertTrue(capture(ScreenshotThrottle.EventKind.CONTENT_CHANGED))
            throttle.recordResult(false)
            now += 100_000
        }
        // 8s * 4
        assertEquals(32_000L, throttle.currentIntervalMillis(ScreenshotThrottle.EventKind.CONTENT_CHANGED))
    }

    @Test
    fun `连续未命中最终退避到上限`() {
        repeat(5) {
            capture(ScreenshotThrottle.EventKind.CONTENT_CHANGED)
            throttle.recordResult(false)
            now += 100_000
        }
        assertEquals(60_000L, throttle.currentIntervalMillis(ScreenshotThrottle.EventKind.CONTENT_CHANGED))
    }

    @Test
    fun `命中后退避归零`() {
        repeat(4) {
            capture(ScreenshotThrottle.EventKind.CONTENT_CHANGED)
            throttle.recordResult(false)
            now += 100_000
        }
        throttle.recordResult(true)
        assertEquals(8_000L, throttle.currentIntervalMillis(ScreenshotThrottle.EventKind.CONTENT_CHANGED))
    }

    @Test
    fun `窗口状态变化重置退避`() {
        repeat(5) {
            capture(ScreenshotThrottle.EventKind.CONTENT_CHANGED)
            throttle.recordResult(false)
            now += 100_000
        }
        assertEquals(60_000L, throttle.currentIntervalMillis(ScreenshotThrottle.EventKind.CONTENT_CHANGED))

        now += 100_000
        capture(ScreenshotThrottle.EventKind.WINDOW_STATE_CHANGED)
        assertEquals(8_000L, throttle.currentIntervalMillis(ScreenshotThrottle.EventKind.CONTENT_CHANGED))
    }

    @Test
    fun `息屏时不放行`() {
        assertFalse(capture(ScreenshotThrottle.EventKind.WINDOW_STATE_CHANGED, screenOn = false))
        assertEquals(ScreenshotThrottle.Decision.SCREEN_OFF, throttle.lastDecision)
    }

    @Test
    fun `悬浮窗显示时不放行`() {
        val allowed = throttle.shouldCapture(
            kind = ScreenshotThrottle.EventKind.WINDOW_STATE_CHANGED,
            screenInteractive = true,
            overlayShowing = true,
        )
        assertFalse(allowed)
        assertEquals(ScreenshotThrottle.Decision.OVERLAY_SHOWING, throttle.lastDecision)
    }
}
