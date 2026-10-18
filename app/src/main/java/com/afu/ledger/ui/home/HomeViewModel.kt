package com.afu.ledger.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afu.ledger.data.repository.AccountRepository
import com.afu.ledger.data.repository.CategoryRepository
import com.afu.ledger.data.repository.TransactionRepository
import com.afu.ledger.domain.model.Account
import com.afu.ledger.domain.model.Category
import com.afu.ledger.domain.model.Money
import com.afu.ledger.domain.model.Transaction
import com.afu.ledger.domain.model.TransType
import com.afu.ledger.util.DateFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class HomeViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    /** 月历的月份 + 选中日期。合在一起是为了少占一个 combine 位。 */
    private data class DateFilter(
        val month: YearMonth = YearMonth.now(),
        val selected: LocalDate? = null,
    )

    private val filter = MutableStateFlow(DateFilter())

    val uiState: StateFlow<HomeUiState> = combine(
        transactionRepository.observeAll(),
        categoryRepository.observeAll(),
        accountRepository.observeActive(),
        filter,
    ) { transactions, categories, accounts, currentFilter ->
        buildState(transactions, categories, accounts, currentFilter)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun deleteTransaction(id: Long) {
        viewModelScope.launch { transactionRepository.delete(id) }
    }

    /** 选中某一天；传 null 表示取消筛选。 */
    fun selectDate(date: LocalDate?) {
        filter.value = filter.value.copy(selected = date)
    }

    /** 切换月历月份。跨月时清掉选中日期，否则会筛出「上个月的今天」。 */
    fun changeMonth(month: YearMonth) {
        filter.value = DateFilter(month = month, selected = null)
    }

    fun showCurrentMonth() {
        filter.value = DateFilter(month = YearMonth.now(), selected = null)
    }

    private fun buildState(
        transactions: List<Transaction>,
        categories: List<Category>,
        accounts: List<Account>,
        filter: DateFilter,
    ): HomeUiState {
        val categoryById = categories.associateBy { it.id }
        val accountById = accounts.associateBy { it.id }
        val zone = ZoneId.systemDefault()

        // 选中某天时只展示那天的账；否则展示全部（保持原来「往回翻历史」的用法）
        val visible = filter.selected?.let { date ->
            val dayKey = DateFormatter.dayKey(
                date.atStartOfDay(zone).toInstant().toEpochMilli(),
            )
            transactions.filter { DateFormatter.dayKey(it.occurredAt) == dayKey }
        } ?: transactions

        val groups = visible
            .groupBy { DateFormatter.dayKey(it.occurredAt) }
            .toSortedMap(compareByDescending { it })
            .map { (dayKey, dayTransactions) ->
                val items = dayTransactions
                    .sortedByDescending { it.occurredAt }
                    .map { transaction ->
                        val category = categoryById[transaction.categoryId]
                        TransactionItem(
                            transaction = transaction,
                            categoryName = category?.name ?: "未分类",
                            categoryIcon = category?.iconKey ?: "more_horiz",
                            categoryColorArgb = category?.colorArgb ?: 0xFF90A4AE,
                            accountName = accountById[transaction.accountId]?.name ?: "已删除账户",
                        )
                    }
                val net = dayTransactions.fold(0L) { acc, t ->
                    acc + if (t.type == TransType.INCOME) t.amount.cents else -t.amount.cents
                }
                TransactionGroup(
                    dayKey = dayKey,
                    label = DateFormatter.friendlyDayLabel(dayTransactions.first().occurredAt),
                    netAmount = Money(net),
                    items = items,
                )
            }

        val monthTransactions = transactions.filter {
            YearMonth.from(Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate()) == filter.month
        }
        val income = monthTransactions.filter { it.type == TransType.INCOME }.sumOf { it.amount.cents }
        val expense = monthTransactions.filter { it.type == TransType.EXPENSE }.sumOf { it.amount.cents }

        return HomeUiState(
            groups = groups,
            monthIncome = Money(income),
            monthExpense = Money(expense),
            hasAccounts = accounts.isNotEmpty(),
            loaded = true,
            calendarMonth = filter.month,
            selectedDate = filter.selected,
            calendarDays = buildCalendarDays(monthTransactions, zone),
        )
    }

    private fun buildCalendarDays(
        monthTransactions: List<Transaction>,
        zone: ZoneId,
    ): Map<Int, DaySummary> {
        if (monthTransactions.isEmpty()) return emptyMap()
        val result = HashMap<Int, DaySummary>(31)
        monthTransactions.forEach { transaction ->
            val day = Instant.ofEpochMilli(transaction.occurredAt).atZone(zone).dayOfMonth
            val current = result[day] ?: DaySummary()
            result[day] = if (transaction.type == TransType.INCOME) {
                current.copy(incomeCents = current.incomeCents + transaction.amount.cents)
            } else {
                current.copy(expenseCents = current.expenseCents + transaction.amount.cents)
            }
        }
        return result
    }
}
