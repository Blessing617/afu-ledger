package com.afu.ledger.domain.budget

import com.afu.ledger.domain.model.Budget
import com.afu.ledger.domain.model.BudgetPeriodType
import com.afu.ledger.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** 预算周期计算：月末收敛、跨年、周边界与自定义区间。 */
class BudgetPeriodTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun budget(
        type: BudgetPeriodType,
        startDay: Int = 1,
        customStart: Long? = null,
        customEnd: Long? = null,
        threshold: Int = 80,
    ) = Budget(
        name = "测试",
        amount = Money(300_000),
        periodType = type,
        monthStartDay = startDay,
        customStartEpochDay = customStart,
        customEndEpochDay = customEnd,
        alertThresholdPercent = threshold,
    )

    @Test
    fun monthlyDefaultStartsAtFirstDay() {
        val window = BudgetPeriod.windowOf(
            budget(BudgetPeriodType.MONTHLY),
            LocalDate.of(2026, 10, 21),
            zone,
        )
        assertEquals(LocalDate.of(2026, 10, 1), window.startDay)
        assertEquals(LocalDate.of(2026, 10, 31), window.endDay)
    }

    @Test
    fun monthlyCustomStartDayAfterStart() {
        // 10 月 20 日，起始日 15 → 本期 10/15 – 11/15（含 11/14 这一天）
        val window = BudgetPeriod.windowOf(
            budget(BudgetPeriodType.MONTHLY, startDay = 15),
            LocalDate.of(2026, 10, 20),
            zone,
        )
        assertEquals(LocalDate.of(2026, 10, 15), window.startDay)
        assertEquals(LocalDate.of(2026, 11, 14), window.endDay)
    }

    @Test
    fun monthlyCustomStartDayBeforeStart() {
        // 10 月 10 日还没到 15 号，应落到上一期 9/15 – 10/15
        val window = BudgetPeriod.windowOf(
            budget(BudgetPeriodType.MONTHLY, startDay = 15),
            LocalDate.of(2026, 10, 10),
            zone,
        )
        assertEquals(LocalDate.of(2026, 9, 15), window.startDay)
        assertEquals(LocalDate.of(2026, 10, 14), window.endDay)
    }

    @Test
    fun monthStart31ConvergesInFebruary() {
        // 2 月没有 31 号：起点收敛到 2/28，下一期 3/31
        val window = BudgetPeriod.windowOf(
            budget(BudgetPeriodType.MONTHLY, startDay = 31),
            LocalDate.of(2026, 2, 15),
            zone,
        )
        assertEquals(LocalDate.of(2026, 1, 31), window.startDay)
        assertEquals(LocalDate.of(2026, 2, 27), window.endDay)

        val afterStart = BudgetPeriod.windowOf(
            budget(BudgetPeriodType.MONTHLY, startDay = 31),
            LocalDate.of(2026, 2, 28),
            zone,
        )
        assertEquals(LocalDate.of(2026, 2, 28), afterStart.startDay)
        assertEquals(LocalDate.of(2026, 3, 30), afterStart.endDay)
    }

    @Test
    fun weeklyStartsOnMonday() {
        // 2026-10-21 是周三，本周应为 10/19（周一）– 10/25（周日）
        val window = BudgetPeriod.windowOf(
            budget(BudgetPeriodType.WEEKLY),
            LocalDate.of(2026, 10, 21),
            zone,
        )
        assertEquals(LocalDate.of(2026, 10, 19), window.startDay)
        assertEquals(LocalDate.of(2026, 10, 25), window.endDay)
    }

    @Test
    fun yearlyCoversCalendarYear() {
        val window = BudgetPeriod.windowOf(
            budget(BudgetPeriodType.YEARLY),
            LocalDate.of(2026, 10, 21),
            zone,
        )
        assertEquals(LocalDate.of(2026, 1, 1), window.startDay)
        assertEquals(LocalDate.of(2026, 12, 31), window.endDay)
    }

    @Test
    fun customRangeIncludesEndDay() {
        val window = BudgetPeriod.windowOf(
            budget(
                BudgetPeriodType.CUSTOM,
                customStart = LocalDate.of(2026, 10, 1).toEpochDay(),
                customEnd = LocalDate.of(2026, 10, 31).toEpochDay(),
            ),
            LocalDate.of(2026, 10, 21),
            zone,
        )
        assertEquals(LocalDate.of(2026, 10, 1), window.startDay)
        assertEquals(LocalDate.of(2026, 10, 31), window.endDay)
        // 右开区间：终点是 11 月 1 日 0 点
        assertEquals(
            LocalDate.of(2026, 11, 1).atStartOfDay(zone).toInstant().toEpochMilli(),
            window.endMillisExclusive,
        )
    }

    @Test
    fun describeMonthlyWithCustomStart() {
        val b = budget(BudgetPeriodType.MONTHLY, startDay = 15)
        val window = BudgetPeriod.windowOf(b, LocalDate.of(2026, 10, 20), zone)
        val text = BudgetPeriod.describe(b, window, LocalDate.of(2026, 10, 20))
        assertEquals(true, text.contains("15"))
        assertEquals(true, text.contains("10 月 15 日"))
    }

    @Test
    fun alertLevelsFollowThreshold() {
        val b = budget(BudgetPeriodType.MONTHLY, threshold = 80)
        fun level(used: Long) =
            BudgetProgress(budget = b, window = BudgetPeriod.windowOf(b, LocalDate.of(2026, 10, 21), zone), usedCents = used).alertLevel

        assertEquals(BudgetAlertLevel.SAFE, level(30_000))
        assertEquals(BudgetAlertLevel.NOTICE, level(200_000))
        assertEquals(BudgetAlertLevel.WARNING, level(250_000))
        assertEquals(BudgetAlertLevel.OVER, level(320_000))
    }
}
