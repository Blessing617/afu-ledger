package com.afu.ledger.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class DateFormatterTest {

    private fun millisOf(date: LocalDate, time: LocalTime = LocalTime.NOON): Long =
        date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun `同日不同时间得到相同分组 key`() {
        val day = LocalDate.of(2026, 10, 18)
        val morning = millisOf(day, LocalTime.of(8, 0))
        val evening = millisOf(day, LocalTime.of(23, 59))

        assertEquals(DateFormatter.dayKey(morning), DateFormatter.dayKey(evening))
    }

    @Test
    fun `不同日期得到不同分组 key`() {
        val a = millisOf(LocalDate.of(2026, 10, 18))
        val b = millisOf(LocalDate.of(2026, 10, 19))

        assertNotEquals(DateFormatter.dayKey(a), DateFormatter.dayKey(b))
    }

    @Test
    fun `今天显示为今天`() {
        val now = System.currentTimeMillis()
        assertEquals("今天", DateFormatter.friendlyDayLabel(now))
    }

    @Test
    fun `昨天显示为昨天`() {
        val yesterday = millisOf(LocalDate.now().minusDays(1))
        assertEquals("昨天", DateFormatter.friendlyDayLabel(yesterday))
    }

    @Test
    fun `起始与结束毫秒覆盖全天`() {
        val day = LocalDate.of(2026, 10, 18)
        val noon = millisOf(day)
        val start = DateFormatter.startOfDay(noon)
        val end = DateFormatter.endOfDayExclusive(noon)

        assertEquals(true, noon >= start)
        assertEquals(true, noon < end)
        // 相邻两天的边界应当衔接
        assertEquals(end, DateFormatter.startOfDay(end))
    }
}
