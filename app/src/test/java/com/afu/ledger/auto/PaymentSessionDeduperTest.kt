package com.afu.ledger.auto

import com.afu.ledger.domain.model.Money
import com.afu.ledger.domain.model.TransType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentSessionDeduperTest {

    private fun draft(
        amount: Long = 2000L,
        direction: TransType = TransType.EXPENSE,
        tradeNo: String? = null,
        merchant: String? = null,
        occurredAt: Long = 1_700_000_000_000L,
    ): PaymentDraft = PaymentDraft(
        amount = Money(amount),
        direction = direction,
        merchant = merchant,
        sourcePackage = PaymentPageRules.ALIPAY_PACKAGE,
        pageType = PaymentPageType.BILL_DETAIL,
        occurredAt = occurredAt,
        occurredAtSource = TimeSource.PAGE,
        capturedAt = 1_700_000_000_000L,
        tradeNo = tradeNo,
    )

    @Test
    fun `跨页面一页有单号一页没有时只算一笔`() {
        // 真机样本：聚合收款页有单号、支付成功页没有，两页指纹不同
        val deduper = PaymentSessionDeduper(clock = { 1_000L })

        assertFalse(deduper.isDuplicate(draft(tradeNo = "100426102022070099178250")))
        assertTrue(deduper.isDuplicate(draft(tradeNo = null)))
    }

    @Test
    fun `两页都没有单号也只算一笔`() {
        val deduper = PaymentSessionDeduper(clock = { 1_000L })

        assertFalse(deduper.isDuplicate(draft()))
        assertTrue(deduper.isDuplicate(draft()))
    }

    @Test
    fun `两个单号都非空且不同视为两笔`() {
        val deduper = PaymentSessionDeduper(clock = { 1_000L })

        assertFalse(deduper.isDuplicate(draft(tradeNo = "A")))
        assertFalse(deduper.isDuplicate(draft(tradeNo = "B")))
    }

    @Test
    fun `两个单号相同时只算一笔`() {
        val deduper = PaymentSessionDeduper(clock = { 1_000L })

        assertFalse(deduper.isDuplicate(draft(tradeNo = "A")))
        assertTrue(deduper.isDuplicate(draft(tradeNo = "A")))
    }

    @Test
    fun `金额不同视为两笔`() {
        val deduper = PaymentSessionDeduper(clock = { 1_000L })

        assertFalse(deduper.isDuplicate(draft(amount = 2000L)))
        assertFalse(deduper.isDuplicate(draft(amount = 3000L)))
    }

    @Test
    fun `方向不同视为两笔`() {
        val deduper = PaymentSessionDeduper(clock = { 1_000L })

        assertFalse(deduper.isDuplicate(draft(direction = TransType.EXPENSE)))
        assertFalse(deduper.isDuplicate(draft(direction = TransType.INCOME)))
    }

    @Test
    fun `窗口过期后可以再次命中`() {
        var now = 1_000L
        val deduper = PaymentSessionDeduper(clock = { now })

        assertFalse(deduper.isDuplicate(draft()))
        now += PaymentSessionDeduper.DEFAULT_WINDOW_MILLIS + 1
        assertFalse(deduper.isDuplicate(draft()))
    }

    @Test
    fun `不同来源应用互不影响`() {
        val deduper = PaymentSessionDeduper(clock = { 1_000L })

        assertFalse(deduper.isDuplicate(draft()))
        assertFalse(
            deduper.isDuplicate(
                draft().copy(sourcePackage = PaymentPageRules.WECHAT_PACKAGE),
            ),
        )
    }

    @Test
    fun `商户名不同不影响会话合并`() {
        val deduper = PaymentSessionDeduper(clock = { 1_000L })

        assertFalse(deduper.isDuplicate(draft(merchant = "杭州深度求索")))
        assertTrue(deduper.isDuplicate(draft(merchant = null)))
    }
}
