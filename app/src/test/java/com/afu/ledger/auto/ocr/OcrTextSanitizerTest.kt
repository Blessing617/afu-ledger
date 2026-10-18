package com.afu.ledger.auto.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrTextSanitizerTest {

    private fun sanitize(raw: String) = OcrTextSanitizer.sanitize(raw)

    @Test
    fun `字母 O 在数字上下文里纠正为零`() {
        assertEquals("-42.00", sanitize("-42.OO"))
        assertEquals("¥25.50", sanitize("¥25.5O"))
    }

    @Test
    fun `账单负数金额可被纠正`() {
        assertEquals("-42.00", sanitize("-42.oo"))
    }

    @Test
    fun `字母 l 与竖线在数字上下文里纠正为一`() {
        assertEquals("12.00", sanitize("l2.00"))
        assertEquals("¥1.00", sanitize("¥l.00"))
    }

    @Test
    fun `S 与 B 的形近替换`() {
        assertEquals("25.00", sanitize("2S.00"))
        assertEquals("8.00", sanitize("B.00"))
    }

    @Test
    fun `紧邻数字的 Y 纠正为货币符号`() {
        assertEquals("¥25.50", sanitize("Y25.50"))
        assertEquals("¥25.50", sanitize("y25.50"))
    }

    @Test
    fun `全角数字与符号转半角`() {
        assertEquals("25.50", sanitize("２５．５０"))
        assertEquals("-42.00", sanitize("－４２．００"))
    }

    @Test
    fun `商户名里的字母不被改写`() {
        // COCO 里的 O 周围没有数字，绝不能变成 C0C0
        assertEquals("COCO都可", sanitize("COCO都可"))
        assertEquals("BARBERSHOP", sanitize("BARBERSHOP"))
        assertEquals("瑞幸咖啡", sanitize("瑞幸咖啡"))
    }

    @Test
    fun `弱形近的 Z 不做替换`() {
        // 这不是纠错而是编造：Z 可能本来就是字母
        assertEquals("¥4Z.50", sanitize("¥4Z.50"))
    }

    @Test
    fun `千分位与空白保持不变交给金额解析`() {
        assertEquals("¥ 1,299.00", sanitize("¥ 1,299.00"))
    }

    @Test
    fun `空串不炸`() {
        assertEquals("", sanitize(""))
    }
}
