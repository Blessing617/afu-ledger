package com.afu.ledger.ui.home

import com.afu.ledger.domain.model.Money
import java.time.LocalDate
import java.time.YearMonth

data class HomeUiState(
    val groups: List<TransactionGroup> = emptyList(),
    val monthIncome: Money = Money.ZERO,
    val monthExpense: Money = Money.ZERO,
    val hasAccounts: Boolean = false,
    val loaded: Boolean = false,
    /** 月历当前显示的月份。 */
    val calendarMonth: YearMonth = YearMonth.now(),
    /** 被选中的日期；非空时列表只显示这一天。 */
    val selectedDate: LocalDate? = null,
    /** 月历格子里要显示的每日收支。 */
    val calendarDays: Map<Int, DaySummary> = emptyMap(),
) {
    val isEmpty: Boolean get() = groups.isEmpty()

    /** 汇总卡标题：跟着月历走，避免「标题说本月、数字是别的月」。 */
    val summaryTitle: String get() = "${calendarMonth.monthValue} 月"
}
