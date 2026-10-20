package com.afu.ledger.domain.schedule

import com.afu.ledger.domain.model.Money
import com.afu.ledger.domain.model.TransType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleCalculatorTest {

    private fun rule(
        frequency: ScheduleFrequency = ScheduleFrequency.MONTHLY,
        interval: Int = 1,
        weekdays: Set<DayOfWeek> = emptySet(),
        dayOfMonth: Int = 1,
        monthOfYear: Int = 1,
        time: LocalTime = LocalTime.of(9, 0),
        start: LocalDate = LocalDate.of(2026, 1, 1),
        end: LocalDate? = null,
        max: Int? = null,
        lastGenerated: LocalDate? = null,
        count: Int = 0,
        enabled: Boolean = true,
    ) = ScheduleRule(
        accountId = 1,
        categoryId = 1,
        type = TransType.EXPENSE,
        amount = Money(100_00),
        frequency = frequency,
        interval = interval,
        weekdays = weekdays,
        dayOfMonth = dayOfMonth,
        monthOfYear = monthOfYear,
        timeOfDay = time,
        startDate = start,
        endDate = end,
        maxOccurrences = max,
        lastGeneratedDate = lastGenerated,
        generatedCount = count,
        enabled = enabled,
    )

    @Test
    fun `每月 31 日在小月收敛到月末`() {
        val r = rule(dayOfMonth = 31, start = LocalDate.of(2026, 1, 1))
        val dates = ScheduleCalculator.sequenceFor(r).take(4).toList()
        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 2, 28),
                LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 4, 30),
            ),
            dates,
        )
    }

    @Test
    fun `闰年二月 29 日也能收敛`() {
        val r = rule(dayOfMonth = 31, start = LocalDate.of(2028, 1, 1))
        assertEquals(
            LocalDate.of(2028, 2, 29),
            ScheduleCalculator.sequenceFor(r).drop(1).first(),
        )
    }

    @Test
    fun `每年 2 月 29 日在平年落到 28 日`() {
        val r = rule(
            frequency = ScheduleFrequency.YEARLY,
            monthOfYear = 2,
            dayOfMonth = 29,
            start = LocalDate.of(2026, 1, 1),
        )
        val dates = ScheduleCalculator.sequenceFor(r).take(2).toList()
        assertEquals(LocalDate.of(2026, 2, 28), dates[0])
        assertEquals(LocalDate.of(2027, 2, 28), dates[1])
    }

    @Test
    fun `每天按间隔推进`() {
        val r = rule(
            frequency = ScheduleFrequency.DAILY,
            interval = 3,
            start = LocalDate.of(2026, 10, 1),
        )
        assertEquals(
            listOf(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 4),
                LocalDate.of(2026, 10, 7),
            ),
            ScheduleCalculator.sequenceFor(r).take(3).toList(),
        )
    }

    @Test
    fun `每周多选按周展开`() {
        val r = rule(
            frequency = ScheduleFrequency.WEEKLY,
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
            start = LocalDate.of(2026, 10, 5), // 周一
        )
        assertEquals(
            listOf(
                LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 10, 9),
                LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 10, 16),
            ),
            ScheduleCalculator.sequenceFor(r).take(4).toList(),
        )
    }

    @Test
    fun `每周间隔两周一跳`() {
        val r = rule(
            frequency = ScheduleFrequency.WEEKLY,
            interval = 2,
            weekdays = setOf(DayOfWeek.MONDAY),
            start = LocalDate.of(2026, 10, 5),
        )
        assertEquals(
            listOf(
                LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 10, 19),
            ),
            ScheduleCalculator.sequenceFor(r).take(2).toList(),
        )
    }

    @Test
    fun `结束日期之后不再生成`() {
        val r = rule(
            frequency = ScheduleFrequency.DAILY,
            start = LocalDate.of(2026, 10, 1),
            end = LocalDate.of(2026, 10, 3),
        )
        assertEquals(3, ScheduleCalculator.sequenceFor(r).count())
    }

    @Test
    fun `到期判定包含今天的时间`() {
        val r = rule(
            frequency = ScheduleFrequency.DAILY,
            time = LocalTime.of(9, 0),
            start = LocalDate.of(2026, 10, 20),
        )
        // 09:00 之前还没到
        assertTrue(ScheduleCalculator.dueOccurrences(r, LocalDateTime.of(2026, 10, 20, 8, 59)).isEmpty)
        // 09:00 整点已到期
        assertEquals(
            listOf(LocalDate.of(2026, 10, 20)),
            ScheduleCalculator.dueOccurrences(r, LocalDateTime.of(2026, 10, 20, 9, 0)).dates,
        )
    }

    @Test
    fun `关机三天后补三笔`() {
        val r = rule(
            frequency = ScheduleFrequency.DAILY,
            start = LocalDate.of(2026, 10, 1),
            lastGenerated = LocalDate.of(2026, 10, 1),
        )
        val due = ScheduleCalculator.dueOccurrences(r, LocalDateTime.of(2026, 10, 4, 23, 0))
        assertEquals(3, due.dates.size)
        assertEquals(0, due.skipped)
        assertEquals(LocalDate.of(2026, 10, 2), due.dates.first())
    }

    @Test
    fun `补记上限之外的期次被跳过并计数`() {
        val r = rule(
            frequency = ScheduleFrequency.DAILY,
            start = LocalDate.of(2024, 1, 1),
        )
        val due = ScheduleCalculator.dueOccurrences(
            rule = r,
            now = LocalDateTime.of(2026, 10, 20, 12, 0),
            maxCatchUp = 10,
        )
        assertEquals(10, due.dates.size)
        assertTrue(due.skipped > 0)
        // 补的是最近的 10 期
        assertEquals(LocalDate.of(2026, 10, 20), due.dates.last())
    }

    @Test
    fun `执行满 N 期后不再到期`() {
        val r = rule(
            frequency = ScheduleFrequency.DAILY,
            start = LocalDate.of(2026, 10, 1),
            max = 2,
            count = 2,
        )
        assertTrue(
            ScheduleCalculator.dueOccurrences(r, LocalDateTime.of(2026, 10, 20, 12, 0)).isEmpty,
        )
        assertNull(ScheduleCalculator.nextOccurrence(r, LocalDateTime.of(2026, 10, 20, 12, 0)))
    }

    @Test
    fun `下一次计划时刻跳过已生成期次`() {
        val r = rule(
            frequency = ScheduleFrequency.MONTHLY,
            dayOfMonth = 1,
            start = LocalDate.of(2026, 1, 1),
            lastGenerated = LocalDate.of(2026, 10, 1),
        )
        assertEquals(
            LocalDateTime.of(2026, 11, 1, 9, 0),
            ScheduleCalculator.nextOccurrence(r, LocalDateTime.of(2026, 10, 20, 12, 0)),
        )
    }

    @Test
    fun `暂停的规则没有到期期次`() {
        val r = rule(frequency = ScheduleFrequency.DAILY, enabled = false)
        assertTrue(
            ScheduleCalculator.dueOccurrences(r, LocalDateTime.of(2026, 10, 20, 12, 0)).isEmpty,
        )
    }

    @Test
    fun `跨时区按本地时分换算毫秒`() {
        val ms = ScheduleCalculator.occurrenceMillis(
            LocalDate.of(2026, 10, 20),
            LocalTime.of(9, 0),
            java.time.ZoneId.of("Asia/Shanghai"),
        )
        assertEquals(
            LocalDateTime.of(2026, 10, 20, 9, 0)
                .atZone(java.time.ZoneId.of("Asia/Shanghai"))
                .toInstant()
                .toEpochMilli(),
            ms,
        )
    }
}
