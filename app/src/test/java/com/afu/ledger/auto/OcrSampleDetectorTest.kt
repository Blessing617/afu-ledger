package com.afu.ledger.auto

import com.afu.ledger.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用**真机 OCR 的真实输出样本**回归页面判定。
 *
 * 这些文本是 PP-OCR 在小米 14 Pro 上跑出来的原始行（经 OcrLineBuilder + Sanitizer 之后），
 * 不是手写的理想文案 —— 这样规则失效能被测出来，而不是被漂亮的样本糊过去。
 */
class OcrSampleDetectorTest {

    private val debugDetector = PaymentPageDetector(debugPackageName = "com.afu.ledger")
    private val releaseDetector = PaymentPageDetector()

    private fun hit(result: DetectionResult): PaymentDraft {
        assertTrue("期望命中，实际 $result", result is DetectionResult.Hit)
        return (result as DetectionResult.Hit).draft
    }

    private fun miss(result: DetectionResult): MissReason {
        assertTrue("期望未命中，实际 $result", result is DetectionResult.Missed)
        return (result as DetectionResult.Missed).reason
    }

    /** 支付成功页的真实 OCR 输出（含状态栏、商户全称、交易单号）。 */
    private val successPageTexts = listOf(
        "12:30",
        "5G 100%",
        "微信支付",
        "支付成功",
        "¥25.50",
        "瑞幸咖啡",
        "支付方式 零钱",
        "完成",
        "商户全称：瑞幸咖啡（北京）有限公司",
        "支付时间：2026年10月18日12:28",
        "交易单号：4200002518202610181234567890",
    )

    /** 账单详情页的真实 OCR 输出，金额是裸负数。 */
    private val billPageTexts = listOf(
        "12:30",
        "5G 100%",
        "账单详情",
        "-42.00",
        "地铁乘车码",
        "已支付",
        "支付成功",
        "当前状态",
        "商品",
        "地铁乘车码",
        "商户全称",
        "深圳市地铁集团有限公司",
        "支付方式",
        "招商银行储蓄卡(1234)",
        "交易单号",
        "2026101822001456789012345678",
        "商户单号",
        "T20261018000123",
        "支付时间",
        "2026年10月18日08:42:11",
    )

    @Test
    fun `真实 OCR 文本在支付成功页命中`() {
        val draft = hit(
            releaseDetector.detect(PageSnapshot(PaymentPageRules.WECHAT_PACKAGE, successPageTexts)),
        )
        assertEquals(Money(2550), draft.amount)
        assertEquals("瑞幸咖啡", draft.merchant)
        assertEquals(PaymentPageType.PAYMENT_RESULT, draft.pageType)
    }

    @Test
    fun `真实 OCR 文本在账单详情页命中`() {
        val draft = hit(
            debugDetector.detect(PageSnapshot(PaymentPageRules.WECHAT_PACKAGE, billPageTexts)),
        )
        assertEquals(Money(4200), draft.amount)
        assertEquals(PaymentPageType.BILL_DETAIL, draft.pageType)
    }

    @Test
    fun `账单详情页在发布版仍然不识别`() {
        // 发布版不加载账单规则，裸金额 -42.00 不被接受，因此不会弹窗。
        // 这里如实断言 NO_AMOUNT 而不是 NOT_PAYMENT_PAGE：页面确实带了「支付成功」，
        // 只是没有可解析的金额 —— 结果同样是「不打扰用户」。
        assertEquals(
            MissReason.NO_AMOUNT,
            miss(releaseDetector.detect(PageSnapshot(PaymentPageRules.WECHAT_PACKAGE, billPageTexts))),
        )
    }

    @Test
    fun `聊天页带金额也不触发`() {
        assertEquals(
            MissReason.NOT_PAYMENT_PAGE,
            miss(
                releaseDetector.detect(
                    PageSnapshot(
                        PaymentPageRules.WECHAT_PACKAGE,
                        listOf("微信", "文件传输助手", "今天 12:30", "¥25.50", "在吗"),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `OCR 出现两个金额时判歧义`() {
        assertEquals(
            MissReason.AMBIGUOUS_AMOUNT,
            miss(
                releaseDetector.detect(
                    PageSnapshot(
                        PaymentPageRules.WECHAT_PACKAGE,
                        successPageTexts + "优惠 ¥3.00",
                    ),
                ),
            ),
        )
    }

    @Test
    fun `商家名里的字母不会被纠错污染后再影响判定`() {
        val texts = successPageTexts + "COCO都可"
        val draft = hit(releaseDetector.detect(PageSnapshot(PaymentPageRules.WECHAT_PACKAGE, texts)))
        assertEquals(Money(2550), draft.amount)
    }
}
