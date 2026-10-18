package com.afu.ledger.auto.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrLineBuilderTest {

    private fun line(
        text: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        confidence: Float = 0.95f,
    ) = OcrLine(text, confidence, left, top, right, bottom)

    @Test
    fun `乱序的框按自上而下自左而右排序`() {
        val lines = listOf(
            line("完成", 400, 900, 500, 950),
            line("12:30", 50, 40, 180, 100),
            line("支付成功", 400, 300, 670, 390),
        )
        assertEquals(listOf("12:30", "支付成功", "完成"), OcrLineBuilder.build(lines))
    }

    @Test
    fun `同一行的碎片合并成一行`() {
        val lines = listOf(
            line("支付方式", 400, 724, 560, 780),
            line("零钱", 580, 726, 670, 782),
        )
        assertEquals(listOf("支付方式 零钱"), OcrLineBuilder.build(lines))
    }

    @Test
    fun `不同行不合并`() {
        val lines = listOf(
            line("支付成功", 400, 300, 670, 390),
            line("瑞幸咖啡", 420, 620, 650, 700),
        )
        assertEquals(listOf("支付成功", "瑞幸咖啡"), OcrLineBuilder.build(lines))
    }

    @Test
    fun `低置信度行被丢弃`() {
        val lines = listOf(
            line("支付成功", 400, 300, 670, 390, confidence = 0.95f),
            line("¥25.50", 395, 458, 774, 558, confidence = 0.31f),
        )
        assertEquals(listOf("支付成功"), OcrLineBuilder.build(lines))
    }

    @Test
    fun `空白行被丢弃`() {
        val lines = listOf(line("   ", 0, 0, 10, 10))
        assertTrue(OcrLineBuilder.build(lines).isEmpty())
    }
}
