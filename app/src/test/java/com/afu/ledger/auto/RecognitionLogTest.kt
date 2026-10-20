package com.afu.ledger.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionLogTest {

    private fun entry(index: Int): RecognitionEntry = RecognitionEntry(
        at = index.toLong(),
        packageName = "com.tencent.mm",
        outcome = "outcome-$index",
    )

    @Test
    fun `最新的记录排在最前`() {
        val log = RecognitionLog(maxEntries = 10)
        log.record(entry(1))
        log.record(entry(2))

        assertEquals(listOf(2L, 1L), log.entries.value.map { it.at })
    }

    @Test
    fun `超过上限时丢弃最旧的`() {
        val log = RecognitionLog(maxEntries = 3)
        (1..5).forEach { log.record(entry(it)) }

        val entries = log.entries.value
        assertEquals(3, entries.size)
        assertEquals(listOf(5L, 4L, 3L), entries.map { it.at })
    }

    @Test
    fun `clear 之后为空`() {
        val log = RecognitionLog()
        log.record(entry(1))
        log.clear()

        assertTrue(log.entries.value.isEmpty())
    }
}
