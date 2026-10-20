package com.afu.ledger.domain.budget

import com.afu.ledger.domain.model.Budget
import com.afu.ledger.domain.model.BudgetPeriodType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/** 一个预算在当前时刻对应的统计区间（毫秒，右开区间）。 */
data class BudgetWindow(
    val startMillis: Long,
    val endMillisExclusive: Long,
    /** 起止自然日（展示用；[endDay] 是最后一**天**，不是排他边界）。 */
    val startDay: LocalDate,
    val endDay: LocalDate,
)

/**
 * 预算周期计算。
 *
 * 全部用本地日期语义（`LocalDate`）算好区间再转时间戳：直接对毫秒做加减，
 * 遇到夏令时或跨月天数不同就会漂移，而记账的「本月」是日历意义上的本月。
 */
object BudgetPeriod {

    private val dayFormatter = DateTimeFormatter.ofPattern("M 月 d 日")

    fun windowOf(
        budget: Budget,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): BudgetWindow {
        val (start, endExclusive) = when (budget.periodType) {
            BudgetPeriodType.MONTHLY -> monthlyRange(budget.monthStartDay, today)
            BudgetPeriodType.WEEKLY -> weeklyRange(today)
            BudgetPeriodType.YEARLY -> yearlyRange(today)
            BudgetPeriodType.CUSTOM -> customRange(budget, today)
        }
        return BudgetWindow(
            startMillis = start.atStartOfDay(zone).toInstant().toEpochMilli(),
            endMillisExclusive = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli(),
            startDay = start,
            endDay = endExclusive.minusDays(1),
        )
    }

    /**
     * 月度区间。
     *
     * 起始日允许 1–31：某月没有这一天时收敛到月末（2 月的 31 号 → 28/29 号），
     * 这样「每月 31 号」不会在 2 月突然失效。
     */
    fun monthlyRange(startDay: Int, today: LocalDate): Pair<LocalDate, LocalDate> {
        val day = startDay.coerceIn(1, 31)
        val thisMonth = YearMonth.from(today)
        val thisStart = thisMonth.atDay(day.coerceAtMost(thisMonth.lengthOfMonth()))
        return if (!today.isBefore(thisStart)) {
            thisStart to startOfMonth(YearMonth.from(today).plusMonths(1), day)
        } else {
            startOfMonth(YearMonth.from(today).minusMonths(1), day) to thisStart
        }
    }

    private fun startOfMonth(month: YearMonth, day: Int): LocalDate =
        month.atDay(day.coerceAtMost(month.lengthOfMonth()))

    /** ISO 周：周一 00:00 到下一周一 00:00。 */
    fun weeklyRange(today: LocalDate): Pair<LocalDate, LocalDate> {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return monday to monday.plusDays(7)
    }

    fun yearlyRange(today: LocalDate): Pair<LocalDate, LocalDate> {
        val start = today.withDayOfYear(1)
        return start to start.plusYears(1)
    }

    fun customRange(budget: Budget, today: LocalDate): Pair<LocalDate, LocalDate> {
        val startEpoch = budget.customStartEpochDay ?: today.toEpochDay()
        val endEpoch = budget.customEndEpochDay ?: startEpoch
        val start = LocalDate.ofEpochDay(startEpoch)
        // 用户填的结束日含当天
        return start to LocalDate.ofEpochDay(endEpoch).plusDays(1)
    }

    /** 周期的人话描述，用于列表与卡片副标题。 */
    fun describe(budget: Budget, window: BudgetWindow, today: LocalDate): String =
        when (budget.periodType) {
            BudgetPeriodType.MONTHLY ->
                if (budget.monthStartDay == 1) {
                    "${window.startDay.year} 年 ${window.startDay.monthValue} 月"
                } else {
                    "每月 ${budget.monthStartDay} 日起 · ${dayFormatter.format(window.startDay)} – ${dayFormatter.format(window.endDay)}"
                }

            BudgetPeriodType.WEEKLY ->
                "本周 · ${dayFormatter.format(window.startDay)} – ${dayFormatter.format(window.endDay)}"

            BudgetPeriodType.YEARLY -> "${window.startDay.year} 年度"

            BudgetPeriodType.CUSTOM ->
                if (window.endDay.isBefore(today)) {
                    "已结束 · ${dayFormatter.format(window.startDay)} – ${dayFormatter.format(window.endDay)}"
                } else {
                    "${dayFormatter.format(window.startDay)} – ${dayFormatter.format(window.endDay)}"
                }
        }
}
