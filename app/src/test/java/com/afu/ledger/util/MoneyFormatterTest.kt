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
}
