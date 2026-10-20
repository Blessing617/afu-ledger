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

    @Test
    fun `支付成功页取到真实商户`() {
        // 真机 2026-10-20：这一页真正的商户是「某人工智能公司」，
        // 旧实现却把底部的「回首页 返回商家」当成了商户。
        val texts = listOf(
            "支付成功", "¥20.00", "某人工智能公司", "付款方式", "账户余额",
            "体验金+18", "账单贴纸+1", "一键领", "支付有礼", "回首页", "返回商家",
        )
        assertEquals("某人工智能公司", MerchantExtractor.extract(texts, Money(2000)))
    }

    @Test
    fun `整行按钮不能被当成商户`() {
        val texts = listOf("账单详情", "支出20元", "回首页 返回商家", "完成")
        assertNull(MerchantExtractor.extract(texts, Money(2000)))
    }

    @Test
    fun `按钮描述拼接文本不能被当成商户`() {
        val texts = listOf("账单详情", "支出25.80元", "返回 按钮", "支付时间", "2026-10-20 22:07:00")
        assertNull(MerchantExtractor.extract(texts, Money(2580)))

        val texts2 = listOf("账单详情", "支出30元", "返回 按钮 完成", "支付时间", "2026-10-20 21:00:00")
        assertNull(MerchantExtractor.extract(texts2, Money(3000)))
    }

    @Test
    fun `活动与优惠文案不被当成商户`() {
        val texts = listOf("账单详情", "支出25元", "支付有礼", "体验金+18", "账单贴纸+1", "去参与")
        assertNull(MerchantExtractor.extract(texts, Money(2500)))
    }
}
