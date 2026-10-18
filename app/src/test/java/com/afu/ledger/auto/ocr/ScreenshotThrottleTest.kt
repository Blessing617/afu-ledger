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
    fun `页面 class 变化按切页间隔放行`() {
        assertTrue(capture(ScreenshotThrottle.EventKind.CONTENT_CHANGED))

        // 1.6 秒后：同样内容变化事件仍在 8 秒间隔内，应被拦
        now += 1_600
        assertFalse(capture(ScreenshotThrottle.EventKind.CONTENT_CHANGED))

        // 但页面 class 变了（微信内部翻页）就按切页间隔（1.5s）走，应放行
        now += 100
        assertTrue(
            throttle.shouldCapture(
                kind = ScreenshotThrottle.EventKind.CONTENT_CHANGED,
                screenInteractive = true,
                overlayShowing = false,
                pageChanged = true,
            ),
        )
    }

    @Test
    fun `页面 class 变化会重置退避`() {
        repeat(5) {
            capture(ScreenshotThrottle.EventKind.CONTENT_CHANGED)
            throttle.recordResult(false)
            now += 100_000
        }
        assertEquals(60_000L, throttle.currentIntervalMillis(ScreenshotThrottle.EventKind.CONTENT_CHANGED))

        now += 100_000
        throttle.shouldCapture(
            kind = ScreenshotThrottle.EventKind.CONTENT_CHANGED,
            screenInteractive = true,
            overlayShowing = false,
            pageChanged = true,
        )
        assertEquals(8_000L, throttle.currentIntervalMillis(ScreenshotThrottle.EventKind.CONTENT_CHANGED))
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

    @Test
    fun `退避拉满后用户主动点击仍放行`() {
        // 在聊天页连续未命中，退避会拉到上限 60 秒
        repeat(5) {
            capture(ScreenshotThrottle.EventKind.CONTENT_CHANGED)
            throttle.recordResult(false)
            now += 100_000
        }
        assertEquals(60_000L, throttle.currentIntervalMillis(ScreenshotThrottle.EventKind.CONTENT_CHANGED))

        // 刚截过一张，紧接着用户点进支付页：不能等 60 秒
        now += 2_000
        val allowed = throttle.shouldCapture(
            kind = ScreenshotThrottle.EventKind.CONTENT_CHANGED,
            screenInteractive = true,
            overlayShowing = false,
            pageChanged = true,
        )
        assertTrue("退避期内的主动换页必须放行，否则会漏记", allowed)
    }

    @Test
    fun `退避期内非主动内容变化仍被拦`() {
        // 连续未命中把间隔拉到 60 秒；最后一次截图后只过 2 秒
        repeat(5) {
            capture(ScreenshotThrottle.EventKind.CONTENT_CHANGED)
            throttle.recordResult(false)
            now += 100_000
        }
        // 重新截一次，把 lastShotAt 拉到当前时刻
        assertTrue(capture(ScreenshotThrottle.EventKind.CONTENT_CHANGED))
        assertEquals(60_000L, throttle.currentIntervalMillis(ScreenshotThrottle.EventKind.CONTENT_CHANGED))

        now += 2_000
        val allowed = capture(ScreenshotThrottle.EventKind.CONTENT_CHANGED)
        assertFalse("没有换页信号时仍应遵守退避间隔", allowed)
    }

    @Test
    fun `主动换页仍受系统截图最小间隔约束`() {
        assertTrue(
            throttle.shouldCapture(
                kind = ScreenshotThrottle.EventKind.WINDOW_STATE_CHANGED,
                screenInteractive = true,
                overlayShowing = false,
            ),
        )
        // 系统侧 takeScreenshot 本身有最小间隔，太快会被它拒绝，所以这里也要拦
        now += 200
        val allowed = throttle.shouldCapture(
            kind = ScreenshotThrottle.EventKind.CONTENT_CHANGED,
            screenInteractive = true,
            overlayShowing = false,
            pageChanged = true,
        )
        assertFalse("距上次截图仅 200ms，应被最小间隔拦住", allowed)
    }
}
