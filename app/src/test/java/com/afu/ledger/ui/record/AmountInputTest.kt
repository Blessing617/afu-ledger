package com.afu.ledger.ui.record

import org.junit.Assert.assertEquals
import org.junit.Test

/** 记账面板金额输入规则。 */
class AmountInputTest {

    @Test
    fun `连续数字直接追加`() {
        var value = ""
        listOf("1", "2", ".", "5").forEach { value = appendAmountKey(value, it) }
        assertEquals("12.5", value)
    }

    @Test
    fun `小数点不重复`() {
        assertEquals("1.2", appendAmountKey("1.2", "."))
    }

    @Test
    fun `空串输入小数点补零`() {
        assertEquals("0.", appendAmountKey("", "."))
    }

    @Test
    fun `小数最多两位`() {
        assertEquals("1.23", appendAmountKey("1.23", "4"))
    }

    @Test
    fun `双零键一次补两位`() {
        assertEquals("1200", appendAmountKey("12", "00"))
    }

    @Test
    fun `整数最多七位`() {
        assertEquals("1234567", appendAmountKey("1234567", "8"))
    }

    @Test
    fun `前导零被替换`() {
        assertEquals("5", appendAmountKey("0", "5"))
    }

    @Test
    fun `零后面点小数点保留`() {
        assertEquals("0.5", appendAmountKey("0.", "5"))
    }
}
