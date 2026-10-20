package com.afu.ledger.domain.schedule

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * 把「周期规则」折算成计划时刻序列。纯 Kotlin，无 Android 依赖，全部可单测。
 *
 * ## 月末收敛
 *
 * 「每月 31 日」在 2 月没有 31 号时，MUST 落在当月最后一天（2/28 或 2/29），
 * MUST NOT 跳过该月、MUST NOT 顺延到 3 月 1 日。选这个语义与 YNAB 一致，
 * 且在 UI 上要写成一句话给用户看（「遇到小月自动记在月末」）。
 *
 * ## 本地日期语义
 *
 * 序列里只有 [LocalDate] 与 [LocalTime]，换算成绝对时刻只在真正入账时做
 * （[ScheduleCalculator.occurrenceTimeOf]）。这样时区变了、夏令时跳变了，
 * 规则仍然按「当地时间几点」执行，而不是整体漂移。
 */
object ScheduleCalculator {

    /** 单次生成序列的保护上限，避免规则写错时无限迭代。 */
    const val MAX_ITERATIONS: Int = 5_000

    /** 一次补记最多回补多少期。超出部分跳过，并由调用方提示跳过了多少。 */
    const val MAX_CATCH_UP: Int = 400

    data class DueOccurrences(
        val dates: List<LocalDate>,
        /** 因为补记上限被跳过的期数（0 表示没跳过）。 */
        val skipped: Int,
    ) {
        val isEmpty: Boolean get() = dates.isEmpty()
    }

    /**
     * 截至 [now] 已经到期、且尚未生成过的期次（按时间升序）。
     *
     * 「到期」= 该期次的本地日期时间早于或等于 now。
     * 「尚未生成」= 晚于 [ScheduleRule.lastGeneratedDate]。
     */
    fun dueOccurrences(
        rule: ScheduleRule,
        now: LocalDateTime,
        maxCatchUp: Int = MAX_CATCH_UP,
    ): DueOccurrences {
        if (!rule.enabled || rule.reachedEnd) return DueOccurrences(emptyList(), 0)

        val all = sequenceFor(rule)
            .takeWhile { !it.isAfter(now.toLocalDate()) }
            .filter { date -> isDueAt(date, rule.timeOfDay, now) }
            .filter { date -> rule.lastGeneratedDate == null || date.isAfter(rule.lastGeneratedDate) }
            .toList()

        if (all.size <= maxCatchUp) return DueOccurrences(all, 0)
        return DueOccurrences(all.takeLast(maxCatchUp), all.size - maxCatchUp)
    }

    /**
     * 下一个**尚未处理**的计划时刻；规则已结束或禁用时返回 null。
     */
    fun nextOccurrence(rule: ScheduleRule, now: LocalDateTime): LocalDateTime? {
        if (!rule.enabled || rule.reachedEnd) return null
        val next = sequenceFor(rule)
            .filter { date -> rule.lastGeneratedDate == null || date.isAfter(rule.lastGeneratedDate) }
            .filter { date -> occurrenceTimeOf(date, rule.timeOfDay).isAfter(now) }
            .firstOrNull()
        return next?.let { occurrenceTimeOf(it, rule.timeOfDay) }
    }

    /** 某一期次的绝对时刻。 */
    fun occurrenceTimeOf(date: LocalDate, time: LocalTime): LocalDateTime = date.atTime(time)

    /** 换算成毫秒时间戳（入账用 `occurredAt`）。 */
    fun occurrenceMillis(date: LocalDate, time: LocalTime, zone: ZoneId = ZoneId.systemDefault()): Long =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    /** 该期次在 [now] 之前是否已经到点（今天要比较时分）。 */
    private fun isDueAt(date: LocalDate, time: LocalTime, now: LocalDateTime): Boolean {
        val today = now.toLocalDate()
        return when {
            date.isBefore(today) -> true
            date.isAfter(today) -> false
            else -> !time.isAfter(now.toLocalTime())
        }
    }

    /**
     * 规则的计划日期序列（已应用开始/结束日期与结束期数上限）。
     *
     * 这是纯函数，输入相同就得到相同的序列；调用方自己 take 需要的长度。
     */
    fun sequenceFor(rule: ScheduleRule): Sequence<LocalDate> {
        val start = rule.startDate
        val interval = rule.interval.coerceAtLeast(1).toLong()

        val raw: Sequence<LocalDate> = when (rule.frequency) {
            ScheduleFrequency.DAILY ->
                generateSequence(start) { it.plusDays(interval) }

            ScheduleFrequency.WEEKLY -> {
                val weekStart = start.with(DayOfWeek.MONDAY)
                val days = rule.weekdays.ifEmpty { setOf(start.dayOfWeek) }
                    .sortedBy { it.value }
                generateSequence(weekStart) { it.plusWeeks(interval) }
                    .flatMap { week ->
                        days.asSequence().map { day -> week.plusDays((day.value - 1).toLong()) }
                    }
            }

            ScheduleFrequency.MONTHLY ->
                generateSequence(start.withDayOfMonth(1)) { it.plusMonths(interval) }
                    .map { month -> dateInMonth(month, rule.dayOfMonth) }

            ScheduleFrequency.YEARLY ->
                generateSequence(start.withDayOfYear(1)) { it.plusYears(interval) }
                    .map { yearStart -> dateInYear(yearStart.year, rule.monthOfYear, rule.dayOfMonth) }
        }

        // 必须用 takeWhile 而不是 filter：序列是无限的，
        // 结束日期之后如果只是被 filter 掉，消费方（count / firstOrNull）会一直往下算不返回。
        return raw
            .takeWhile { rule.endDate == null || !it.isAfter(rule.endDate) }
            .filter { it.isBefore(start).not() }
            .take(MAX_ITERATIONS)
    }

    /** 指定月份的第 N 天，超出当月天数时收敛到月末。 */
    fun dateInMonth(monthStart: LocalDate, dayOfMonth: Int): LocalDate {
        val ym = YearMonth.from(monthStart)
        val day = dayOfMonth.coerceIn(1, ym.lengthOfMonth())
        return ym.atDay(day)
    }

    /** 指定年份的某月某日，2 月 29 日在平年收敛到 2 月 28 日。 */
    fun dateInYear(year: Int, monthOfYear: Int, dayOfMonth: Int): LocalDate {
        val ym = YearMonth.of(year, monthOfYear.coerceIn(1, 12))
        return ym.atDay(dayOfMonth.coerceIn(1, ym.lengthOfMonth()))
    }
}
