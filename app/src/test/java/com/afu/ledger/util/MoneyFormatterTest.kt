package com.afu.ledger.util

import com.afu.ledger.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatterTest {

    @Test
    fun `格式化带千分位`() {
        assertEquals("12,345.67", MoneyFormatter.formatYuan(1234567))
    }

    @Test
    fun `格式化零`() {
        assertEquals("0.00", MoneyFormatter.formatYuan(0))
    }

    @Test
    fun `带符号输出支出`() {
        assertEquals("-25.50", MoneyFormatter.formatSigned(Money(2550), isExpense = true))
    }

    @Test
    fun `带符号输出收入`() {
        assertEquals("+25.50", MoneyFormatter.formatSigned(Money(2550), isExpense = false))
    }

    @Test
    fun `不足一万仍显示完整金额`() {
        assertEquals("9,999.00", MoneyFormatter.formatCompactYuan(999_900))
        assertEquals("¥9,999.00", MoneyFormatter.formatCompactWithSymbol(Money(999_900)))
    }

    @Test
    fun `一万起用万`() {
        assertEquals("1.00万", MoneyFormatter.formatCompactYuan(1_000_000))
        assertEquals("1.23万", MoneyFormatter.formatCompactYuan(1_234_500))
        assertEquals("¥1.23万", MoneyFormatter.formatCompactWithSymbol(Money(1_234_500)))
    }

    @Test
    fun `一亿起用亿`() {
        assertEquals("1.00亿", MoneyFormatter.formatCompactYuan(MoneyFormatter.YI_CENTS))
        assertEquals("1.23亿", MoneyFormatter.formatCompactYuan(12_345_000_000))
    }

    @Test
    fun `一万亿起用万亿`() {
        assertEquals("1.00万亿", MoneyFormatter.formatCompactYuan(MoneyFormatter.WAN_YI_CENTS))
        // 真机截图里的 99,999,999,999,956.00 元 = 9,999,999,999,995,600 分
        assertEquals("99.99万亿", MoneyFormatter.formatCompactYuan(9_999_999_999_995_600L))
    }

    @Test
    fun `负数大额也缩写且负号在最前`() {
        assertEquals("-1.23亿", MoneyFormatter.formatCompactYuan(-12_345_000_000L))
        assertEquals("-¥1.23亿", MoneyFormatter.formatCompactWithSymbol(Money(-12_345_000_000L)))
    }
}
