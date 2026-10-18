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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class HomeViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        transactionRepository.observeAll(),
        categoryRepository.observeAll(),
        accountRepository.observeActive(),
    ) { transactions, categories, accounts ->
        buildState(transactions, categories, accounts)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun deleteTransaction(id: Long) {
        viewModelScope.launch { transactionRepository.delete(id) }
    }

    private fun buildState(
        transactions: List<Transaction>,
        categories: List<Category>,
        accounts: List<Account>,
    ): HomeUiState {
        val categoryById = categories.associateBy { it.id }
        val accountById = accounts.associateBy { it.id }

        val groups = transactions
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

        val (monthIncome, monthExpense) = currentMonthTotals(transactions)

        return HomeUiState(
            groups = groups,
            monthIncome = monthIncome,
            monthExpense = monthExpense,
            hasAccounts = accounts.isNotEmpty(),
            loaded = true,
        )
    }

    private fun currentMonthTotals(transactions: List<Transaction>): Pair<Money, Money> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val nextMonthStart = today.withDayOfMonth(1).plusMonths(1)
            .atStartOfDay(zone).toInstant().toEpochMilli()

        val inMonth = transactions.filter { it.occurredAt in monthStart until nextMonthStart }
        val income = inMonth.filter { it.type == TransType.INCOME }.sumOf { it.amount.cents }
        val expense = inMonth.filter { it.type == TransType.EXPENSE }.sumOf { it.amount.cents }
        return Money(income) to Money(expense)
    }
}
