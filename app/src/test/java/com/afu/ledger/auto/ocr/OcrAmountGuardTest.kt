package com.afu.ledger.auto.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrAmountGuardTest {

    private fun check(vararg lines: String) = OcrAmountGuard.check(lines.toList())

    @Test
    fun `正常金额放行`() {
        assertEquals(OcrAmountGuard.Verdict.Ok, check("支付成功", "¥25.50", "瑞幸咖啡"))
        assertEquals(OcrAmountGuard.Verdict.Ok, check("¥1,299.00"))
        assertEquals(OcrAmountGuard.Verdict.Ok, check("账单详情", "-42.00"))
    }

    @Test
    fun `金额里混入字母时拒绝`() {
        val verdict = check("账单详情", "¥4Z.50", "地铁")
        assertTrue("应被拒绝，实际 $verdict", verdict is OcrAmountGuard.Verdict.Rejected)
    }

    @Test
    fun `整数位过多时拒绝`() {
        assertTrue(check("¥1234567.00") is OcrAmountGuard.Verdict.Rejected)
    }

    @Test
    fun `小数位过多时拒绝`() {
        assertTrue(check("¥25.555") is OcrAmountGuard.Verdict.Rejected)
    }

    @Test
    fun `不含货币符号的行不影响判定`() {
        assertEquals(OcrAmountGuard.Verdict.Ok, check("支付方式 零钱", "交易单号 4200002"))
    }
}
