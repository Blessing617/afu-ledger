package com.afu.ledger.auto

import com.afu.ledger.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountParserTest {

    @Test
    fun `解析带货币符号的金额`() {
        assertEquals(2550L, AmountParser.parseOne("¥25.50")?.cents)
        assertEquals(129900L, AmountParser.parseOne("¥1,299.00")?.cents)
        assertEquals(1L, AmountParser.parseOne("￥0.01")?.cents)
        assertEquals(2550L, AmountParser.parseOne("支付成功 ¥ 25.50")?.cents)
    }

    @Test
    fun `解析以元结尾的金额`() {
        assertEquals(2550L, AmountParser.parseOne("25.5元")?.cents)
        assertEquals(1990L, AmountParser.parseOne("19.90 元")?.cents)
        assertEquals(1990L, AmountParser.parseOne("-19.90元")?.cents)
    }

    @Test
    fun `不把单号时间验证码当金额`() {
        assertNull(AmountParser.parseOne("交易单号 4200001234"))
        assertNull(AmountParser.parseOne("12:30"))
        assertNull(AmountParser.parseOne("验证码 8821"))
        assertNull(AmountParser.parseOne(""))
    }

    @Test
    fun `整页金额去重`() {
        val amounts = AmountParser.parseAll(listOf("¥25.50", "25.50元", "订单金额", "¥3.00"))
        assertEquals(listOf(Money(2550), Money(300)), amounts)
    }

    @Test
    fun `整页没有金额时返回空`() {
        assertEquals(emptyList<Money>(), AmountParser.parseAll(listOf("支付成功", "完成")))
    }

    @Test
    fun `裸金额只认整条两位小数`() {
        assertEquals(2550L, AmountParser.parseBare("-25.50")?.cents)
        assertEquals(129900L, AmountParser.parseBare("1,299.00")?.cents)
        // 单号、日期、时间都不是裸金额
        assertNull(AmountParser.parseBare("2026101822001234567"))
        assertNull(AmountParser.parseBare("2026-10-18"))
        assertNull(AmountParser.parseBare("12:30"))
        assertNull(AmountParser.parseBare("25.50 元"))
    }

    @Test
    fun `默认不把裸金额当金额`() {
        assertEquals(emptyList<Money>(), AmountParser.parseAll(listOf("-25.50", "地铁")))
        assertEquals(
            listOf(Money(2550)),
            AmountParser.parseAll(listOf("-25.50", "地铁"), allowBareAmount = true),
        )
    }
}
