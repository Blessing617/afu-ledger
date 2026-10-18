package com.afu.ledger.auto

import com.afu.ledger.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentPageDetectorTest {

    private val releaseDetector = PaymentPageDetector()

    private fun hit(result: DetectionResult): PaymentDraft {
        assertTrue("期望命中，实际 $result", result is DetectionResult.Hit)
        return (result as DetectionResult.Hit).draft
    }

    private fun miss(result: DetectionResult): MissReason {
        assertTrue("期望未命中，实际 $result", result is DetectionResult.Missed)
        return (result as DetectionResult.Missed).reason
    }

    // ---- 公开常见文案样本（微信 / 支付宝各多套），用于规则校准 ----

    @Test
    fun `微信支付成功页`() {
        val draft = hit(
            releaseDetector.detect(
                PageSnapshot(
                    PaymentPageRules.WECHAT_PACKAGE,
                    listOf("支付成功", "¥25.50", "瑞幸咖啡", "完成"),
                ),
            ),
        )
        assertEquals(Money(2550), draft.amount)
        assertEquals("瑞幸咖啡", draft.merchant)
        assertEquals(PaymentPageType.PAYMENT_RESULT, draft.pageType)
    }

    @Test
    fun `微信旧版文案 已支付`() {
        val draft = hit(
            releaseDetector.detect(
                PageSnapshot(
                    PaymentPageRules.WECHAT_PACKAGE,
                    listOf("已支付", "15.00元", "美团单车", "查看账单详情"),
                ),
            ),
        )
        assertEquals(Money(1500), draft.amount)
    }

    @Test
    fun `支付宝付款成功页`() {
        val draft = hit(
            releaseDetector.detect(
                PageSnapshot(
                    PaymentPageRules.ALIPAY_PACKAGE,
                    listOf("付款成功", "¥120.00", "盒马鲜生", "完成"),
                ),
            ),
        )
        assertEquals(Money(12000), draft.amount)
        assertEquals("盒马鲜生", draft.merchant)
    }

    @Test
    fun `支付宝交易成功页`() {
        val draft = hit(
            releaseDetector.detect(
                PageSnapshot(
                    PaymentPageRules.ALIPAY_PACKAGE,
                    listOf("交易成功", "¥6.50", "便利蜂", "返回首页"),
                ),
            ),
        )
        assertEquals(Money(650), draft.amount)
    }

    // ---- 不该触发的情况 ----

    @Test
    fun `非受支持应用直接跳过`() {
        assertEquals(
            MissReason.UNSUPPORTED_PACKAGE,
            miss(releaseDetector.detect(PageSnapshot("com.example.shop", listOf("支付成功", "¥25.50")))),
        )
    }

    @Test
    fun `聊天列表不是支付页`() {
        assertEquals(
            MissReason.NOT_PAYMENT_PAGE,
            miss(
                releaseDetector.detect(
                    PageSnapshot(PaymentPageRules.WECHAT_PACKAGE, listOf("微信", "文件传输助手", "今天 12:30")),
                ),
            ),
        )
    }

    @Test
    fun `扫码页不是支付页`() {
        assertEquals(
            MissReason.NOT_PAYMENT_PAGE,
            miss(
                releaseDetector.detect(
                    PageSnapshot(PaymentPageRules.WECHAT_PACKAGE, listOf("扫一扫", "相册", "取消")),
                ),
            ),
        )
    }

    @Test
    fun `金额无法解析`() {
        assertEquals(
            MissReason.NO_AMOUNT,
            miss(
                releaseDetector.detect(
                    PageSnapshot(PaymentPageRules.WECHAT_PACKAGE, listOf("支付成功", "金额", "完成")),
                ),
            ),
        )
    }

    @Test
    fun `多个金额视为歧义`() {
        assertEquals(
            MissReason.AMBIGUOUS_AMOUNT,
            miss(
                releaseDetector.detect(
                    PageSnapshot(PaymentPageRules.WECHAT_PACKAGE, listOf("支付成功", "¥25.50", "优惠 ¥3.00")),
                ),
            ),
        )
    }

    @Test
    fun `金额为零放弃`() {
        assertEquals(
            MissReason.ZERO_AMOUNT,
            miss(
                releaseDetector.detect(
                    PageSnapshot(PaymentPageRules.WECHAT_PACKAGE, listOf("支付成功", "¥0.00")),
                ),
            ),
        )
    }

    @Test
    fun `未来时态不记账`() {
        assertEquals(
            MissReason.EXCLUDED_KEYWORD,
            miss(
                releaseDetector.detect(
                    PageSnapshot(PaymentPageRules.WECHAT_PACKAGE, listOf("支付成功", "¥19.90", "将于明日扣款")),
                ),
            ),
        )
    }

    @Test
    fun `退款页不记账`() {
        assertEquals(
            MissReason.EXCLUDED_KEYWORD,
            miss(
                releaseDetector.detect(
                    PageSnapshot(PaymentPageRules.WECHAT_PACKAGE, listOf("退款成功", "¥25.50")),
                ),
            ),
        )
    }

    // ---- 账单详情页只在 debug 生效 ----

    @Test
    fun `账单详情页发布版不识别`() {
        val snapshot = PageSnapshot(
            PaymentPageRules.ALIPAY_PACKAGE,
            listOf("账单详情", "-25.50", "地铁", "交易单号 2026101822001"),
        )
        assertEquals(MissReason.NOT_PAYMENT_PAGE, miss(releaseDetector.detect(snapshot)))
    }

    @Test
    fun `账单详情页 debug 版可识别`() {
        val debugDetector = PaymentPageDetector(debugPackageName = "com.afu.ledger")
        val snapshot = PageSnapshot(
            PaymentPageRules.ALIPAY_PACKAGE,
            listOf("账单详情", "-25.50", "地铁", "交易单号 2026101822001"),
        )
        val draft = hit(debugDetector.detect(snapshot))
        assertEquals(Money(2550), draft.amount)
        assertEquals(PaymentPageType.BILL_DETAIL, draft.pageType)
    }

    @Test
    fun `debug 版放行自家模拟页面`() {
        val debugDetector = PaymentPageDetector(debugPackageName = "com.afu.ledger")
        val draft = hit(
            debugDetector.detect(
                PageSnapshot("com.afu.ledger", listOf("支付成功", "¥25.50", "瑞幸咖啡")),
            ),
        )
        assertEquals(Money(2550), draft.amount)
    }
}
