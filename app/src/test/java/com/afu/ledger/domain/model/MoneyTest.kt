package com.afu.ledger.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun `解析整数金额`() {
        assertEquals(Money(1200), Money.parseYuan("12"))
    }

    @Test
    fun `解析两位小数`() {
        assertEquals(Money(1234), Money.parseYuan("12.34"))
    }

    @Test
    fun `解析一位小数按角补齐`() {
        assertEquals(Money(1250), Money.parseYuan("12.5"))
    }

    @Test
    fun `解析负数`() {
        assertEquals(Money(-1234), Money.parseYuan("-12.34"))
    }

    @Test
    fun `解析带千分位逗号的金额`() {
        // 预算编辑会预填 `3,000.00`，这里必须能解析，否则保存按钮永远灰着
        assertEquals(Money(300000), Money.parseYuan("3,000.00"))
        assertEquals(Money(123456789), Money.parseYuan("1,234,567.89"))
        assertEquals(Money(-123400), Money.parseYuan("-1,234.00"))
    }

    @Test
    fun `解析带首尾空格与前导加号`() {
        assertEquals(Money(1234), Money.parseYuan("  12.34  "))
        assertEquals(Money(2500), Money.parseYuan("+25.00"))
    }

    @Test
    fun `小数部分带逗号返回 null`() {
        assertNull(Money.parseYuan("12.3,4"))
    }

    @Test
    fun `整数九位可解析`() {
        assertEquals(Money(999_999_999_99L), Money.parseYuan("999,999,999.99"))
    }

    @Test
    fun `整数超过九位返回 null`() {
        assertNull(Money.parseYuan("1,000,000,000"))
        assertNull(Money.parseYuan("1234567890.00"))
    }

    @Test
    fun `千分位位置不严谨也按容错接受`() {
        assertEquals(Money(123400), Money.parseYuan("1,234.00"))
        assertEquals(Money(12300), Money.parseYuan("1,23.00"))
    }

    @Test
    fun `空串返回 null`() {
        assertNull(Money.parseYuan(""))
        assertNull(Money.parseYuan("   "))
    }

    @Test
    fun `非数字返回 null`() {
        assertNull(Money.parseYuan("abc"))
        assertNull(Money.parseYuan("12a"))
    }

    @Test
    fun `超过两位小数返回 null`() {
        assertNull(Money.parseYuan("12.345"))
    }

    @Test
    fun `加法不丢精度`() {
        // 0.1 + 0.2 用 Double 会得到 0.30000000000000004，用分则精确
        val a = Money.parseYuan("0.1")!!
        val b = Money.parseYuan("0.2")!!
        assertEquals(Money(30), a + b)
    }

    @Test
    fun `减法与取反`() {
        val a = Money(1000)
        val b = Money(250)
        assertEquals(Money(750), a - b)
        assertEquals(Money(-750), (b - a))
        assertEquals(Money(750), (b - a).abs())
    }

    @Test
    fun `比较大小`() {
        assertEquals(true, Money(100) > Money(50))
        assertEquals(true, Money(50) < Money(100))
    }
}
