package com.afu.ledger.ui.report

import com.afu.ledger.domain.model.Category
import com.afu.ledger.domain.model.TransType
import com.afu.ledger.domain.model.Transaction
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** 分类占比里的一项。 */
data class CategorySlice(
    val name: String,
    val iconKey: String,
    val colorArgb: Long,
    val amountCents: Long,
    val count: Int,
    /** 占本期支出的比例，0f..1f。 */
    val ratio: Float,
)

/** 趋势图上的一个点。 */
data class TrendPoint(
    val label: String,
    val amountCents: Long,
)

/** 单笔排行里的一条。 */
data class TopTransaction(
    val categoryName: String,
    val iconKey: String,
    val colorArgb: Long,
    val note: String,
    val amountCents: Long,
    val dayOfMonth: Int,
)

/** 一个月的完整报表。 */
data class MonthlyReport(
    val yearMonth: YearMonth,
    val incomeCents: Long,
    val expenseCents: Long,
    val balanceCents: Long,
    val previousBalanceCents: Long,
    val trend: List<TrendPoint>,
    val slices: List<CategorySlice>,
    val topTransactions: List<TopTransaction>,
    val maxDayExpenseCents: Long,
    val maxDayExpenseDay: Int,
    val averageDailyExpenseCents: Long,
    val expenseCount: Int,
) {
    val isEmpty: Boolean get() = incomeCents == 0L && expenseCents == 0L
}

/**
 * 把交易聚合成月度报表。
 *
 * 纯函数、无 Android 依赖，方便单测：报表是「数字必须对」的地方，
 * 一旦口径错了，用户看到的所有结论都是错的。
 */
object ReportAggregator {

    /** 「未分类」的显示名：分类被删掉后历史交易仍然要计入合计。 */
    const val UNCATEGORIZED = "未分类"

    fun build(
        transactions: List<Transaction>,
        categories: List<Category>,
        yearMonth: YearMonth,
        today: LocalDate = LocalDate.now(),
    ): MonthlyReport {
        val zone = ZoneId.systemDefault()
        val categoryById = categories.associateBy { it.id }

        val monthStart = yearMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val monthEnd = yearMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val inMonth = transactions.filter { it.occurredAt in monthStart until monthEnd }
        val income = inMonth.filter { it.type == TransType.INCOME }.sumOf { it.amount.cents }
        val expense = inMonth.filter { it.type == TransType.EXPENSE }.sumOf { it.amount.cents }

        // 上月结余 = 本月之前所有交易的净额（含更早月份）；资产转移不影响结余，跳过
        val previousBalance = transactions
            .filter { it.occurredAt < monthStart }
            .sumOf {
                when (it.type) {
                    TransType.INCOME -> it.amount.cents
                    TransType.EXPENSE -> -it.amount.cents
                    TransType.TRANSFER -> 0L
                }
            }

        val expenseTransactions = inMonth.filter { it.type == TransType.EXPENSE }
        val trend = buildTrend(expenseTransactions, yearMonth, zone)
        val peak = trend.maxByOrNull { it.amountCents }

        return MonthlyReport(
            yearMonth = yearMonth,
            incomeCents = income,
            expenseCents = expense,
            balanceCents = income - expense,
            previousBalanceCents = previousBalance,
            trend = trend,
            slices = buildSlices(expenseTransactions, categoryById, expense),
            topTransactions = buildTop(expenseTransactions, categoryById),
            maxDayExpenseCents = peak?.amountCents ?: 0L,
            maxDayExpenseDay = peak?.label?.toIntOrNull() ?: 0,
            averageDailyExpenseCents = averageDaily(expense, yearMonth, today),
            expenseCount = expenseTransactions.size,
        )
    }

    /** 每天一个点。当月按到「今天」为止算平均，不把未来几天摊薄。 */
    private fun buildTrend(
        expenseTransactions: List<Transaction>,
        yearMonth: YearMonth,
        zone: ZoneId,
    ): List<TrendPoint> {
        val byDay = expenseTransactions.groupBy {
            java.time.Instant.ofEpochMilli(it.occurredAt).atZone(zone).dayOfMonth
        }
        return (1..yearMonth.lengthOfMonth()).map { day ->
            TrendPoint(
                label = day.toString(),
                amountCents = byDay[day]?.sumOf { it.amount.cents } ?: 0L,
            )
        }
    }

    private fun buildSlices(
        expenseTransactions: List<Transaction>,
        categoryById: Map<Long, Category>,
        totalExpense: Long,
    ): List<CategorySlice> {
        val grouped = expenseTransactions.groupBy { it.categoryId }
        return grouped.map { (categoryId, list) ->
            val category = categoryById[categoryId]
            val amount = list.sumOf { it.amount.cents }
            CategorySlice(
                name = category?.name ?: UNCATEGORIZED,
                iconKey = category?.iconKey ?: "more_horiz",
                colorArgb = category?.colorArgb ?: UNCATEGORIZED_COLOR,
                amountCents = amount,
                count = list.size,
                ratio = if (totalExpense > 0) amount.toFloat() / totalExpense else 0f,
            )
        }.sortedByDescending { it.amountCents }
    }

    private fun buildTop(
        expenseTransactions: List<Transaction>,
        categoryById: Map<Long, Category>,
    ): List<TopTransaction> {
        val zone = ZoneId.systemDefault()
        return expenseTransactions
            .sortedByDescending { it.amount.cents }
            .take(TOP_COUNT)
            .map { transaction ->
                val category = categoryById[transaction.categoryId]
                TopTransaction(
                    categoryName = category?.name ?: UNCATEGORIZED,
                    iconKey = category?.iconKey ?: "more_horiz",
                    colorArgb = category?.colorArgb ?: UNCATEGORIZED_COLOR,
                    note = transaction.note,
                    amountCents = transaction.amount.cents,
                    dayOfMonth = java.time.Instant.ofEpochMilli(transaction.occurredAt)
                        .atZone(zone).dayOfMonth,
                )
            }
    }

    /** 平均每日支出。当月按已过天数算，历史月份按整月天数算。 */
    private fun averageDaily(totalExpense: Long, yearMonth: YearMonth, today: LocalDate): Long {
        if (totalExpense == 0L) return 0L
        val days = when {
            yearMonth.isAfter(YearMonth.from(today)) -> return 0L
            yearMonth == YearMonth.from(today) -> today.dayOfMonth
            else -> yearMonth.lengthOfMonth()
        }
        return if (days <= 0) 0L else totalExpense / days
    }

    private const val TOP_COUNT = 5
    private const val UNCATEGORIZED_COLOR = 0xFF90A4AE
}
