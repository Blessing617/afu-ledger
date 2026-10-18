package com.afu.ledger.auto

import com.afu.ledger.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MerchantExtractorTest {

    @Test
    fun `取金额下方的商户名`() {
        val texts = listOf("支付成功", "¥25.50", "瑞幸咖啡", "完成")
        assertEquals("瑞幸咖啡", MerchantExtractor.extract(texts, Money(2550)))
    }

    @Test
    fun `取金额上方的商户名`() {
        val texts = listOf("地铁乘车码", "¥4.00", "支付成功")
        assertEquals("地铁乘车码", MerchantExtractor.extract(texts, Money(400)))
    }

    @Test
    fun `跳过按钮与元信息`() {
        val texts = listOf("支付成功", "¥25.50", "查看账单详情", "完成")
        assertNull(MerchantExtractor.extract(texts, Money(2550)))
    }

    @Test
    fun `跳过纯数字与时间`() {
        val texts = listOf("支付成功", "¥25.50", "4200001234567", "12:30", "2026-10-18")
        assertNull(MerchantExtractor.extract(texts, Money(2550)))
    }

    @Test
    fun `英文商户名也接受`() {
        val texts = listOf("支付成功", "¥25.50", "Starbucks", "完成")
        assertEquals("Starbucks", MerchantExtractor.extract(texts, Money(2550)))
    }

    @Test
    fun `跳过订单状态文案`() {
        val texts = listOf("账单详情", "支出25元", "等待确认收货", "支付时间", "2026-10-17 22:20:59")
        assertNull(MerchantExtractor.extract(texts, Money(2500)))
    }

    @Test
    fun `账单页能取到真实商户名`() {
        val texts = listOf("账单详情", "天猫", "qi店", "支出25元", "等待确认收货")
        assertEquals("qi店", MerchantExtractor.extract(texts, Money(2500)))
    }

    @Test
    fun `支付宝账单页读不到商户名时返回空`() {
        val texts = listOf(
            "返回", "账单详情", "支出25元", "等待确认收货", "支付时间", "2026-10-17 22:20:59",
            "付款方式", "账户余额(个人余额)", "交易详情", "更多",
        )
        assertNull(MerchantExtractor.extract(texts, Money(2500)))
    }
}
