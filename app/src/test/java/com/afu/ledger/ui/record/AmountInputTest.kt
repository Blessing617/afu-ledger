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
    fun `带小数的金额按顺序输入`() {
        var value = ""
        listOf("4", "4", "6", ".", "2", "2").forEach { value = appendAmountKey(value, it) }
        assertEquals("446.22", value)
    }

    @Test
    fun `整数位可以长到九位`() {
        var value = ""
        "123456789".forEach { value = appendAmountKey(value, it.toString()) }
        assertEquals("123456789", value)
        assertEquals("123456789", appendAmountKey(value, "0"))
    }

    @Test
    fun `整数九位后仍可加两位小数`() {
        assertEquals("123456789.12", appendAmountKey("123456789.1", "2"))
    }

    @Test
    fun `整数最多九位`() {
        assertEquals("123456789", appendAmountKey("123456789", "0"))
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
