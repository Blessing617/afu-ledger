package com.afu.ledger.ui.home

import com.afu.ledger.domain.model.Money
import java.time.LocalDate
import java.time.YearMonth

/**
 * 首页状态：**只关心今天**。
 *
 * 之前首页把「月度结余 + 整月日历 + 全部历史列表」塞在一屏，打开应用第一眼看到的是
 * 一个月历，而不是「今天花了多少」。现在首页只回答一个问题：今天怎么样。
 */
data class HomeUiState(
    val todayExpense: Money = Money.ZERO,
    val todayIncome: Money = Money.ZERO,
    val items: List<TransactionItem> = emptyList(),
    val hasAccounts: Boolean = false,
    val loaded: Boolean = false,
    val todayLabel: String = "",
    /** 识别到但还没确认的账单（弹窗被系统拦截时只能从这里补记）。 */
    val pendingBills: List<PendingBillView> = emptyList(),
) {
    val net: Money get() = Money(todayIncome.cents - todayExpense.cents)
    val isEmpty: Boolean get() = items.isEmpty()
}

/**
 * 明细页状态：月度概览 + 月历 + 按日分组的历史列表。
 *
 * 它与 [HomeUiState] 是两份独立状态：明细页切到上个月并选中某天，不影响首页的「今天」。
 */
data class HistoryUiState(
    val groups: List<TransactionGroup> = emptyList(),
    val monthIncome: Money = Money.ZERO,
    val monthExpense: Money = Money.ZERO,
    val loaded: Boolean = false,
    /** 月历当前显示的月份。 */
    val calendarMonth: YearMonth = YearMonth.now(),
    /** 被选中的日期；非空时列表只显示这一天。 */
    val selectedDate: LocalDate? = null,
    /** 月历格子里要显示的每日收支。 */
    val calendarDays: Map<Int, DaySummary> = emptyMap(),
) {
    val monthBalance: Money get() = Money(monthIncome.cents - monthExpense.cents)
    val isEmpty: Boolean get() = groups.isEmpty()

    /** 汇总卡标题：跟着月历走，避免「标题说本月、数字是别的月」。 */
    val summaryTitle: String get() = "${calendarMonth.monthValue} 月"
}

/** 首页待确认列表里的一行。 */
data class PendingBillView(
    val fingerprint: String,
    val amount: Money,
    val directionLabel: String,
    val merchant: String?,
    val source: String,
    /** 账单时间，例 `2026-10-18 20:10`。 */
    val billTimeText: String,
)

/** 一次数据变更同时产出首页与明细页的状态。 */
data class LedgerUiState(
    val home: HomeUiState = HomeUiState(),
    val history: HistoryUiState = HistoryUiState(),
)
