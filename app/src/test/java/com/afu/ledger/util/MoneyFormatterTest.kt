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
    fun `大额也完整显示而不是缩写`() {
        // 大额显示问题由布局（字号/折行）解决，不由格式解决：
        // `¥4.70万` 这种缩写在资产页被用户明确否掉了
        assertEquals("47,000.00", MoneyFormatter.formatYuan(4_700_000))
        assertEquals("¥47,000.00", MoneyFormatter.formatWithSymbol(Money(4_700_000)))
        assertEquals(
            "¥99,999,999,999,956.00",
            MoneyFormatter.formatWithSymbol(Money(9_999_999_999_995_600L)),
        )
    }

    @Test
    fun `负数大额负号在最前`() {
        assertEquals("-¥123,456,789.00", MoneyFormatter.formatWithSymbol(Money(-12_345_678_900L)))
    }

    // 明细页月历格子：曾经 1716550 分 → "+1k"，16345 分 → "-163"。
    // 这两条把「完整显示」钉死成规格，回归时先跑它们。
    @Test
    fun `月历金额完整显示不缩写`() {
        assertEquals("+17,165.50", MoneyFormatter.formatSignedYuan(1_716_550))
        assertEquals("-2,555.00", MoneyFormatter.formatSignedYuan(-255_500))
        assertEquals("+0.00", MoneyFormatter.formatSignedYuan(0))
    }

    @Test
    fun `月历金额不舍去小数`() {
        assertEquals("-163.45", MoneyFormatter.formatSignedYuan(-16_345))
        assertEquals("-26.60", MoneyFormatter.formatSignedYuan(-2_660))
        assertEquals("+0.01", MoneyFormatter.formatSignedYuan(1))
    }

    @Test
    fun `月历金额九位整数位仍完整`() {
        // 记账面板允许最多 9 位整数位，这里按上限验证不丢精度
        assertEquals("+999,999,999.99", MoneyFormatter.formatSignedYuan(99_999_999_999L))
    }

    @Test
    fun `月历金额处理 Long 边界`() {
        assertEquals(
            "-92,233,720,368,547,758.08",
            MoneyFormatter.formatSignedYuan(Long.MIN_VALUE),
        )
    }
}
