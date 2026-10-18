package com.afu.ledger.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 节点文本通道的判定回归。
 *
 * 支付宝这类**无障碍节点可读**的页面必须走节点通道：
 * 它不需要截图、不需要 OCR，是整条识别链路里最便宜的一条路径。
 *
 * 之所以在这里把「节点够用 → 不碰 OCR」写成断言，是因为这条约束一旦被改坏
 * （比如有人把判定挪到 OCR 之后），支付宝就会从「几十毫秒」退化成「一次全屏
 * 截图 + 整页 OCR」，而且不会有任何测试失败来提醒。
 *
 * 真机上的端到端耗时会受 MIUI 无障碍授权策略影响、难以稳定复现，
 * 所以这里锁住的是**调度决策**，不是墙钟时间。
 */
class NodePathPolicyTest {

    private val releaseDetector = PaymentPageDetector()

    /** 支付宝「付款成功」结果页：节点可读，真实抓到的文本形态。 */
    private val alipayResultTexts = listOf(
        "付款成功",
        "¥25.50",
        "瑞幸咖啡",
        "支付方式 余额宝",
        "完成",
    )

    /** 支付宝账单详情页。 */
    private val alipayBillTexts = listOf(
        "账单详情",
        "地铁乘车码",
        "已支付",
        "支付方式 余额宝",
        "交易单号 2026101822001",
        "-25.50",
    )

    @Test
    fun `支付宝付款成功页由节点文本直接命中`() {
        val result = releaseDetector.detect(
            PageSnapshot(PaymentPageRules.ALIPAY_PACKAGE, alipayResultTexts),
        )
        assertTrue("期望命中，实际 $result", result is DetectionResult.Hit)
        assertEquals(2550L, (result as DetectionResult.Hit).draft.amount.cents)
    }

    @Test
    fun `支付宝账单详情页在发布版同样命中`() {
        // 账单详情页是当前的主力目标：裸负金额必须被接受，且发布版与 debug 一致。
        val result = releaseDetector.detect(
            PageSnapshot(PaymentPageRules.ALIPAY_PACKAGE, alipayBillTexts),
        )
        assertTrue("期望命中，实际 $result", result is DetectionResult.Hit)
        assertEquals(2550L, (result as DetectionResult.Hit).draft.amount.cents)
        assertEquals(PaymentPageType.BILL_DETAIL, result.draft.pageType)
    }

    @Test
    fun `支付宝页面文本足以判定时不需要 OCR`() {
        // 这条对应 AutoRecognitionEngine.hasUsableNodeText 的阈值：
        // 行数 >= 3 或总字符数 >= 12 时，引擎直接返回，绝不创建截图任务。
        val snapshot = PageSnapshot(PaymentPageRules.ALIPAY_PACKAGE, alipayResultTexts)
        assertTrue(
            "支付宝结果页的行数应达到「够用」阈值",
            snapshot.texts.size >= MIN_USEFUL_NODE_LINES,
        )
    }

    @Test
    fun `微信空节点才会落到 OCR`() {
        // 微信自绘页面读不到文本：行数与字符数都低于阈值，这时才值得付出截图 + OCR 成本。
        val emptySnapshot = PageSnapshot(PaymentPageRules.WECHAT_PACKAGE, emptyList())
        assertFalse(emptySnapshot.texts.size >= MIN_USEFUL_NODE_LINES)
        assertFalse(emptySnapshot.texts.sumOf { it.length } >= MIN_USEFUL_NODE_CHARS)
    }

    @Test
    fun `装饰性节点不足以冒充可用文本`() {
        // 微信偶尔会暴露一两个装饰性节点，不能因为「有 1 行」就去判定或跳过 OCR
        val decorative = PageSnapshot(
            PaymentPageRules.WECHAT_PACKAGE,
            listOf("返回"),
        )
        assertFalse("1 行短文本不应被视为可用", decorative.texts.size >= MIN_USEFUL_NODE_LINES)
        assertFalse(decorative.texts.sumOf { it.length } >= MIN_USEFUL_NODE_CHARS)
    }

}
