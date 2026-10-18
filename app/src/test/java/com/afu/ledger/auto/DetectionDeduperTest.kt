package com.afu.ledger.auto

import com.afu.ledger.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionDeduperTest {

    private fun draft(amount: Long = 2550L, merchant: String? = "瑞幸咖啡"): PaymentDraft =
        PaymentDraft(
            amount = Money(amount),
            merchant = merchant,
            sourcePackage = PaymentPageRules.WECHAT_PACKAGE,
            pageType = PaymentPageType.PAYMENT_RESULT,
            capturedAt = 0L,
        )

    @Test
    fun `同一笔支付窗口内只提醒一次`() {
        var now = 1_000L
        val deduper = DetectionDeduper(clock = { now })

        assertTrue(deduper.shouldNotify(draft()))
        assertFalse(deduper.shouldNotify(draft()))
    }

    @Test
    fun `窗口过期后可以再次提醒`() {
        var now = 1_000L
        val deduper = DetectionDeduper(clock = { now })

        assertTrue(deduper.shouldNotify(draft()))
        now += DetectionDeduper.DEFAULT_WINDOW_MILLIS + 1
        assertTrue(deduper.shouldNotify(draft()))
    }

    @Test
    fun `相同金额不同商户算两笔`() {
        var now = 1_000L
        val deduper = DetectionDeduper(clock = { now })

        assertTrue(deduper.shouldNotify(draft(merchant = "瑞幸咖啡")))
        assertTrue(deduper.shouldNotify(draft(merchant = "沙县小吃")))
    }

    @Test
    fun `不同金额不同商户都算不同笔`() {
        var now = 1_000L
        val deduper = DetectionDeduper(clock = { now })

        assertTrue(deduper.shouldNotify(draft(amount = 2550L)))
        assertTrue(deduper.shouldNotify(draft(amount = 1200L)))
    }

    @Test
    fun `商户名大小写与空白不影响指纹`() {
        var now = 1_000L
        val deduper = DetectionDeduper(clock = { now })

        assertTrue(deduper.shouldNotify(draft(merchant = "Starbucks")))
        assertFalse(deduper.shouldNotify(draft(merchant = " starbucks ")))
    }

    @Test
    fun `reset 之后可以重新提醒`() {
        var now = 1_000L
        val deduper = DetectionDeduper(clock = { now })

        assertTrue(deduper.shouldNotify(draft()))
        deduper.reset()
        assertTrue(deduper.shouldNotify(draft()))
    }
}
