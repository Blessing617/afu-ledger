package com.afu.ledger.auto

import com.afu.ledger.domain.model.Money
import com.afu.ledger.domain.model.TransType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionDeduperTest {

    private fun draft(amount: Long = 2550L, merchant: String? = "瑞幸咖啡"): PaymentDraft =
        PaymentDraft(
            amount = Money(amount),
            direction = TransType.EXPENSE,
            merchant = merchant,
            sourcePackage = PaymentPageRules.WECHAT_PACKAGE,
            pageType = PaymentPageType.BILL_DETAIL,
            occurredAt = 1_700_000_000_000L,
            occurredAtSource = TimeSource.PAGE,
            capturedAt = 1_700_000_000_000L,
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
    fun `商户名不参与指纹所以同金额不同商户算同一笔`() {
        // 真机证据：同一张账单两次 OCR 会把商户名读成「阿佑鲜烫牛內米线」和
        // 「阿佑鮮烫牛内米线」。商户名带进指纹就变成两个指纹，同一张账单反复弹窗。
        // 代价是「同一分钟内给两家付了同样金额」会被合并成一次 —— 罕见且能手动补记。
        var now = 1_000L
        val deduper = DetectionDeduper(clock = { now })

        assertTrue(deduper.shouldNotify(draft(merchant = "瑞幸咖啡")))
        assertFalse(deduper.shouldNotify(draft(merchant = "沙县小吃")))
    }

    @Test
    fun `同金额但账单时间不同分钟算两笔`() {
        val deduper = DetectionDeduper(clock = { 1_000L })

        assertTrue(deduper.shouldNotify(draft()))
        val later = draft().copy(occurredAt = 1_700_000_000_000L + 120_000L)
        assertTrue(deduper.shouldNotify(later))
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
