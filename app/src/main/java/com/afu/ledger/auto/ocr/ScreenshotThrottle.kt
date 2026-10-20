package com.afu.ledger.auto.ocr

/**
 * 截图节流状态机，纯 Kotlin、注入时钟，可完全单测。
 *
 * 三段式：
 * 1. **窗口状态变化**用短间隔（用户真的切页了，别漏记）
 * 2. **内容变化**用长间隔（页面在动不代表换了页面）
 * 3. **连续未命中退避**（聊天页一直滑，不能一直截）
 *
 * 这里只回答「现在能不能截」，不负责截图，也不负责判断页面稳定 —— 后者由服务端的事件去抖完成。
 */
class ScreenshotThrottle(
    private val clock: () -> Long = System::currentTimeMillis,
    private val config: Config = Config(),
) {

    enum class EventKind {
        /** 窗口状态变化：真正切页 / 换应用回来。 */
        WINDOW_STATE_CHANGED,

        /** 页面内容变化：滚动、局部刷新。 */
        CONTENT_CHANGED,
    }

    data class Config(
        val minIntervalContentMillis: Long = 8_000L,
        val minIntervalWindowMillis: Long = 1_500L,
        /** 连续未命中达到该次数后开始退避。 */
        val backoffAfterAttempts: Int = 3,
        /** 连续未命中达到该次数后退避到上限。 */
        val maxBackoffAfterAttempts: Int = 5,
        val backoffMultiplier: Int = 4,
        val maxIntervalMillis: Long = 60_000L,
    )

    private var lastShotAt: Long = NEVER
    private var consecutiveMisses: Int = 0
    private var multiplier: Int = 1

    /** 最近一次判定结果，供日志使用。 */
    var lastDecision: Decision = Decision.ALLOWED
        private set

    enum class Decision {
        ALLOWED,
        THROTTLED,
        SCREEN_OFF,
        OVERLAY_SHOWING,
    }

    /**
     * 判定本次事件是否允许截图。
     *
     * 放行时会立刻记账（乐观），避免截图失败导致下一次事件又立刻重试形成风暴。
     *
     * @param pageChanged 用户主动操作（点击）或页面标识变化带来的「换页」信号。
     *   该信号 MUST 绕过退避：用户在聊天页待久了退避已经拉满到 60 秒，
     *   此时点进支付页如果还被退避拦掉，就是最典型的漏记。
     */
    fun shouldCapture(
        kind: EventKind,
        screenInteractive: Boolean,
        overlayShowing: Boolean,
        pageChanged: Boolean = false,
    ): Boolean {
        if (!screenInteractive) return reject(Decision.SCREEN_OFF)
        if (overlayShowing) return reject(Decision.OVERLAY_SHOWING)

        // 微信在单个 Activity 内翻页（聊天 → 账单列表 → 账单详情）只发内容变化事件，
        // 不会发窗口状态变化。所以「窗口变化」或「页面 class 变了」都算用户翻到了新页面：
        // 退避归零，并按切页的短间隔放行——否则用户翻得比 8 秒快就全被拦掉，直接漏记。
        val newPage = kind == EventKind.WINDOW_STATE_CHANGED || pageChanged
        if (newPage) resetBackoff()

        val now = clock()
        val last = lastShotAt
        if (last != NEVER) {
            if (newPage) {
                // 用户明确的换页动作：只受系统截图最小间隔约束（1.5s），
                // **不受退避影响**。退避是「连续盲截」的惩罚，惩罚用户主动翻页没有道理。
                if (now - last < config.minIntervalWindowMillis) {
                    return reject(Decision.THROTTLED)
                }
            } else if (now - last < currentIntervalMillis(kind)) {
                return reject(Decision.THROTTLED)
            }
        }

        lastShotAt = now
        lastDecision = Decision.ALLOWED
        return true
    }

    /** 记录一次 OCR 的结果，用于退避。 */
    fun recordResult(hit: Boolean) {
        if (hit) {
            consecutiveMisses = 0
            multiplier = 1
            return
        }
        consecutiveMisses++
        if (consecutiveMisses >= config.maxBackoffAfterAttempts) {
            multiplier = Int.MAX_VALUE / 2
        } else if (consecutiveMisses >= config.backoffAfterAttempts) {
            multiplier = config.backoffMultiplier
        }
    }

    /** 当前生效的最小间隔，供日志与测试断言。 */
    fun currentIntervalMillis(kind: EventKind): Long {
        val base = when (kind) {
            EventKind.WINDOW_STATE_CHANGED -> config.minIntervalWindowMillis
            EventKind.CONTENT_CHANGED -> config.minIntervalContentMillis
        }
        val scaled = if (multiplier >= Int.MAX_VALUE / 4) {
            config.maxIntervalMillis
        } else {
            (base * multiplier).coerceAtMost(config.maxIntervalMillis)
        }
        return scaled.coerceAtMost(config.maxIntervalMillis)
    }

    fun reset() {
        lastShotAt = NEVER
        resetBackoff()
        lastDecision = Decision.ALLOWED
    }

    private fun resetBackoff() {
        consecutiveMisses = 0
        multiplier = 1
    }

    private fun reject(decision: Decision): Boolean {
        lastDecision = decision
        return false
    }

    private companion object {
        /** 「还没截过」的哨兵值，不能用 0：注入时钟的测试里 0 是合法时刻。 */
        const val NEVER = Long.MIN_VALUE
    }
}
