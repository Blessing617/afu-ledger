package com.afu.ledger.ui.report

import com.afu.ledger.domain.model.Category
import com.afu.ledger.domain.model.Money
import com.afu.ledger.domain.model.TransType
import com.afu.ledger.domain.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * 报表口径回归。
 *
 * 报表是「数字必须对」的地方 —— 口径错了，用户看到的所有结论都是错的，
 * 而且他不会报错，只会觉得这个 App 不可信。
 */
class ReportAggregatorTest {

    private val zone = ZoneId.systemDefault()
    private val month = YearMonth.of(2026, 10)

    private fun at(monthValue: Int, day: Int, hour: Int = 12): Long =
        LocalDateTime.of(2026, monthValue, day, hour, 0)
            .atZone(zone).toInstant().toEpochMilli()

    private fun expense(cents: Long, categoryId: Long, day: Int, note: String = ""): Transaction =
        Transaction(
            accountId = 1L,
            categoryId = categoryId,
            type = TransType.EXPENSE,
            amount = Money(cents),
            note = note,
            occurredAt = at(month.monthValue, day),
            createdAt = at(month.monthValue, day),
            updatedAt = at(month.monthValue, day),
        )

    private fun income(cents: Long, categoryId: Long, day: Int): Transaction =
        Transaction(
            accountId = 1L,
            categoryId = categoryId,
            type = TransType.INCOME,
            amount = Money(cents),
            occurredAt = at(month.monthValue, day),
            createdAt = at(month.monthValue, day),
            updatedAt = at(month.monthValue, day),
        )

    private val categories = listOf(
        Category(id = 1, name = "餐饮", type = TransType.EXPENSE, iconKey = "restaurant", colorArgb = 0xFFEF5350),
        Category(id = 2, name = "交通", type = TransType.EXPENSE, iconKey = "directions_bus", colorArgb = 0xFF42A5F5),
        Category(id = 3, name = "工资", type = TransType.INCOME, iconKey = "payments", colorArgb = 0xFF66BB6A),
    )

    @Test
    fun `本月收支与结余`() {
        val transactions = listOf(
            expense(2550, 1, 3),
            expense(1200, 2, 5),
            income(500000, 3, 10),
        )
        val report = ReportAggregator.build(transactions, categories, month, LocalDate.of(2026, 10, 20))

        assertEquals(2_550L + 1_200L, report.expenseCents)
        assertEquals(500_000L, report.incomeCents)
        assertEquals(500_000L - 3_750L, report.balanceCents)
        assertEquals(2, report.expenseCount)
        assertFalse(report.isEmpty)
    }

    @Test
    fun `分类占比按支出金额降序且总和为一`() {
        val transactions = listOf(
            expense(7500, 1, 3),
            expense(2500, 2, 5),
        )
        val report = ReportAggregator.build(transactions, categories, month, LocalDate.of(2026, 10, 20))

        assertEquals(listOf("餐饮", "交通"), report.slices.map { it.name })
        assertEquals(0.75f, report.slices[0].ratio, 0.001f)
        assertEquals(0.25f, report.slices[1].ratio, 0.001f)
    }

    @Test
    fun `分类被删除后归入未分类但仍计入合计`() {
        val transactions = listOf(expense(3000, 99, 8))
        val report = ReportAggregator.build(transactions, categories, month, LocalDate.of(2026, 10, 20))

        assertEquals(3000L, report.expenseCents)
        assertEquals(ReportAggregator.UNCATEGORIZED, report.slices.single().name)
    }

    @Test
    fun `单笔排行取金额最大的五笔`() {
        val transactions = (1..8).map { expense(it * 1000L, 1, it) }
        val report = ReportAggregator.build(transactions, categories, month, LocalDate.of(2026, 10, 20))

        assertEquals(5, report.topTransactions.size)
        assertEquals(8000L, report.topTransactions.first().amountCents)
        assertEquals(4000L, report.topTransactions.last().amountCents)
    }

    @Test
    fun `上月结余等于此前所有月份的净额`() {
        val transactions = listOf(
            Transaction(
                accountId = 1,
                categoryId = 3,
                type = TransType.INCOME,
                amount = Money(100_000),
                occurredAt = at(9, 15),
                createdAt = at(9, 15),
                updatedAt = at(9, 15),
            ),
            Transaction(
                accountId = 1,
                categoryId = 1,
                type = TransType.EXPENSE,
                amount = Money(20_000),
                occurredAt = at(9, 16),
                createdAt = at(9, 16),
                updatedAt = at(9, 16),
            ),
            expense(5_000, 1, 3),
        )
        val report = ReportAggregator.build(transactions, categories, month, LocalDate.of(2026, 10, 20))

        assertEquals(80_000L, report.previousBalanceCents)
        assertEquals(5_000L, report.expenseCents)
    }

    @Test
    fun `趋势按天聚合且峰值可定位`() {
        val transactions = listOf(
            expense(1000, 1, 3),
            expense(2000, 1, 3),
            expense(9000, 2, 17),
        )
        val report = ReportAggregator.build(transactions, categories, month, LocalDate.of(2026, 10, 20))

        assertEquals(31, report.trend.size)
        assertEquals(3000L, report.trend[2].amountCents)
        assertEquals(9000L, report.trend[16].amountCents)
        assertEquals(9000L, report.maxDayExpenseCents)
        assertEquals(17, report.maxDayExpenseDay)
    }

    @Test
    fun `平均每日支出按已过天数算`() {
        val transactions = listOf(expense(10_000, 1, 3))
        val report = ReportAggregator.build(transactions, categories, month, LocalDate.of(2026, 10, 20))

        // 10 月 1 日到 20 日：10000 / 20 = 500
        assertEquals(500L, report.averageDailyExpenseCents)
    }

    @Test
    fun `历史月份按整月天数算平均`() {
        val transactions = listOf(expense(31_000, 1, 3))
        val report = ReportAggregator.build(
            transactions,
            categories,
            month,
            LocalDate.of(2026, 12, 1),
        )

        assertEquals(1_000L, report.averageDailyExpenseCents)
    }

    @Test
    fun `空月份给出零值而不是 null`() {
        val report = ReportAggregator.build(emptyList(), categories, month, LocalDate.of(2026, 10, 20))

        assertEquals(0L, report.expenseCents)
        assertEquals(0L, report.incomeCents)
        assertTrue(report.isEmpty)
        assertTrue(report.slices.isEmpty())
    }

    @Test
    fun `资产转移不计入收入支出与占比`() {
        val transfer = Transaction(
            accountId = 1L,
            categoryId = 9L, // 内置「转账」分类，不参与任何统计
            type = TransType.TRANSFER,
            amount = Money(556_521),
            note = "",
            occurredAt = at(month.monthValue, 5),
            createdAt = at(month.monthValue, 5),
            updatedAt = at(month.monthValue, 5),
            transferAccountId = 2L,
        )
        val transactions = listOf(expense(3_000, 1L, 6), transfer)

        val report = ReportAggregator.build(transactions, categories, month, LocalDate.of(2026, 10, 20))

        // 支出只有那笔 30.00；转移的 5565.21 一分都不该进报表
        assertEquals(3_000L, report.expenseCents)
        assertEquals(0L, report.incomeCents)
        assertEquals(1, report.expenseCount)
        assertEquals(listOf("餐饮"), report.slices.map { it.name })
        assertEquals(1, report.topTransactions.size)
        assertEquals(0L, report.previousBalanceCents)
    }
}
