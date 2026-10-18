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
