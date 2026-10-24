package com.afu.ledger.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afu.ledger.auto.AutoLedgerSettingsRepository
import com.afu.ledger.auto.PendingBillStore
import com.afu.ledger.auto.ProcessedBillStore
import com.afu.ledger.auto.RecordDetectedPaymentUseCase
import com.afu.ledger.auto.RecordResult
import com.afu.ledger.data.repository.AccountRepository
import com.afu.ledger.data.repository.BudgetRepository
import com.afu.ledger.data.repository.CategoryRepository
import com.afu.ledger.data.repository.TransactionRepository
import com.afu.ledger.domain.model.Account
import com.afu.ledger.domain.model.Category
import com.afu.ledger.domain.budget.BudgetProgress
import com.afu.ledger.domain.model.Money
import com.afu.ledger.domain.model.Transaction
import com.afu.ledger.domain.model.TransType
import com.afu.ledger.util.DateFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * 首页与明细页共用的 ViewModel。
 *
 * 两个页面读同一份交易流，但产出两份**互不影响**的状态：
 * - [LedgerUiState.home] 只算今天
 * - [LedgerUiState.history] 算月历与历史分组
 *
 * 放在一个 ViewModel 里是因为它们订阅的是同一份数据；拆成两个会重复订阅、
 * 也会在「记一笔」后出现一边更新一边没更新的时间差。
 */
class HomeViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    /** 首页预算卡片：预算本身 + 本期已用。 */
    private val budgetRepository: BudgetRepository,
    /** 待确认账单：弹窗被系统拦截时，用户从这里补记。 */
    private val pendingBillStore: PendingBillStore,
    private val processedBillStore: ProcessedBillStore,
    private val settingsRepository: AutoLedgerSettingsRepository,
    private val recordDetectedPayment: RecordDetectedPaymentUseCase,
    /** 只用于清「待确认」通知。 */
    private val appContext: android.content.Context,
) : ViewModel() {

    /** 明细页的月份 + 选中日期。合在一起是为了少占一个 combine 位。 */
    private data class DateFilter(
        val month: YearMonth = YearMonth.now(),
        val selected: LocalDate? = null,
    )

    private val filter = MutableStateFlow(DateFilter())

    /**
     * 首页预算进度。
     *
     * 交易一变就重算「本期已用」—— 记完一笔立刻能在卡片上看到剩余减少，
     * 不用等下一次冷启动或切页。
     */
    val budgetProgress: StateFlow<List<BudgetProgress>> = combine(
        budgetRepository.observeEnabled(),
        transactionRepository.observeAll(),
    ) { budgets, _ -> budgets }
        .mapLatest { budgets ->
            withContext(Dispatchers.Default) {
                budgets.map { budgetRepository.progress(it) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** 待确认账单（DataStore 不是 Flow，手动刷新）。 */
    private val pending = MutableStateFlow<List<PendingBillStore.PendingBill>>(emptyList())

    init {
        refreshPending()
    }

    /** 重新读待确认列表；进入首页、确认或忽略之后都调一次。 */
    fun refreshPending() {
        viewModelScope.launch {
            pending.value = runCatching { pendingBillStore.list() }.getOrDefault(emptyList())
        }
    }

    /**
     * 补确认一条待确认账单。
     *
     * 用当前设置的默认账户与默认分类入账 —— 补确认的场景用户不想再走一遍选择；
     * **交易时间仍然用账单上的时间**，不是现在。
     */
    fun confirmPending(fingerprint: String) {
        viewModelScope.launch {
            val bill = pending.value.firstOrNull { it.fingerprint == fingerprint } ?: return@launch
            val settings = settingsRepository.settings.first()
            val accounts = accountRepository.observeActive().first()
            val accountId = settings.defaultAccountId
                ?.takeIf { id -> accounts.any { it.id == id } }
                ?: accounts.firstOrNull()?.id
                ?: return@launch
            val categories = categoryRepository.observeAll().first()
                .filter { it.type == bill.direction }
            val categoryId = settings.defaultCategoryId
                ?.takeIf { id -> categories.any { it.id == id } }
                ?: categories.firstOrNull { it.name == RecordDetectedPaymentUseCase.FALLBACK_CATEGORY_NAME }?.id
                ?: categories.firstOrNull()?.id
                ?: return@launch

            val result = recordDetectedPayment.execute(bill.toDraft(), accountId, categoryId)
            if (result is RecordResult.Success) {
                // 入账成功才清掉待确认；失败留在列表里让用户重试
                processedBillStore.mark(fingerprint)
                pendingBillStore.remove(fingerprint)
                refreshPending()
            }
        }
    }

    /** 忽略一条待确认账单：同样写进已处理指纹，之后不再弹。 */
    fun ignorePending(fingerprint: String) {
        viewModelScope.launch {
            processedBillStore.mark(fingerprint)
            pendingBillStore.remove(fingerprint)
            refreshPending()
        }
    }

    val uiState: StateFlow<LedgerUiState> = combine(
        transactionRepository.observeAll(),
        categoryRepository.observeAll(),
        accountRepository.observeActive(),
        filter,
        pending,
    ) { transactions, categories, accounts, currentFilter, pendingBills ->
        val state = buildState(transactions, categories, accounts, currentFilter)
        state.copy(
            home = state.home.copy(
                pendingBills = pendingBills.map { bill ->
                    PendingBillView(
                        fingerprint = bill.fingerprint,
                        amount = Money(bill.amountCents),
                        directionLabel = if (bill.direction == TransType.INCOME) "收入" else "支出",
                        merchant = bill.merchant,
                        source = bill.source,
                        billTimeText = DateFormatter.billTimeLabel(bill.occurredAt),
                    )
                },
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LedgerUiState(),
    )

    fun deleteTransaction(id: Long) {
        viewModelScope.launch { transactionRepository.delete(id) }
    }

    /** 选中某一天；传 null 表示取消筛选。只影响明细页。 */
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
    ): LedgerUiState {
        val categoryById = categories.associateBy { it.id }
        val accountById = accounts.associateBy { it.id }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val todayKey = DateFormatter.dayKey(today.atStartOfDay(zone).toInstant().toEpochMilli())

        fun toItem(transaction: Transaction): TransactionItem {
            val category = categoryById[transaction.categoryId]
            return TransactionItem(
                transaction = transaction,
                categoryName = category?.name ?: "未分类",
                categoryIcon = category?.iconKey ?: "more_horiz",
                categoryColorArgb = category?.colorArgb ?: 0xFF90A4AE,
                accountName = accountById[transaction.accountId]?.name ?: "已删除账户",
                transferToAccountName = transaction.transferAccountId?.let { id ->
                    accountById[id]?.name ?: "已删除账户"
                },
            )
        }

        // —— 首页：只有今天 ——
        val todayTransactions = transactions
            .filter { DateFormatter.dayKey(it.occurredAt) == todayKey }
            .sortedByDescending { it.occurredAt }
        val todayExpense = todayTransactions
            .filter { it.type == TransType.EXPENSE }
            .sumOf { it.amount.cents }
        val todayIncome = todayTransactions
            .filter { it.type == TransType.INCOME }
            .sumOf { it.amount.cents }

        val home = HomeUiState(
            todayExpense = Money(todayExpense),
            todayIncome = Money(todayIncome),
            items = todayTransactions.map(::toItem),
            hasAccounts = accounts.isNotEmpty(),
            loaded = true,
            todayLabel = todayLabel(today),
        )

        // —— 明细：月份 + 选中日 ——
        val visible = filter.selected?.let { date ->
            val dayKey = DateFormatter.dayKey(date.atStartOfDay(zone).toInstant().toEpochMilli())
            transactions.filter { DateFormatter.dayKey(it.occurredAt) == dayKey }
        } ?: transactions

        val groups = visible
            .groupBy { DateFormatter.dayKey(it.occurredAt) }
            .toSortedMap(compareByDescending { it })
            .map { (dayKey, dayTransactions) ->
                // 资产转移不计入当日结余：钱没花掉，只是换了个账户
                val net = dayTransactions.fold(0L) { acc, t ->
                    acc + when (t.type) {
                        TransType.INCOME -> t.amount.cents
                        TransType.EXPENSE -> -t.amount.cents
                        TransType.TRANSFER -> 0L
                    }
                }
                TransactionGroup(
                    dayKey = dayKey,
                    label = DateFormatter.friendlyDayLabel(dayTransactions.first().occurredAt),
                    netAmount = Money(net),
                    items = dayTransactions
                        .sortedByDescending { it.occurredAt }
                        .map(::toItem),
                )
            }

        val monthTransactions = transactions.filter {
            YearMonth.from(Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate()) == filter.month
        }
        val income = monthTransactions.filter { it.type == TransType.INCOME }.sumOf { it.amount.cents }
        val expense = monthTransactions.filter { it.type == TransType.EXPENSE }.sumOf { it.amount.cents }

        val history = HistoryUiState(
            groups = groups,
            monthIncome = Money(income),
            monthExpense = Money(expense),
            loaded = true,
            calendarMonth = filter.month,
            selectedDate = filter.selected,
            calendarDays = buildCalendarDays(monthTransactions, zone),
        )

        return LedgerUiState(home = home, history = history)
    }

    /** 「10 月 19 日 · 周日」。 */
    private fun todayLabel(today: LocalDate): String {
        val weekday = today.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA)
        return "${today.monthValue} 月 ${today.dayOfMonth} 日 · $weekday"
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
            result[day] = when (transaction.type) {
                TransType.INCOME -> current.copy(incomeCents = current.incomeCents + transaction.amount.cents)
                TransType.EXPENSE -> current.copy(expenseCents = current.expenseCents + transaction.amount.cents)
                // 转移既不进也不出，日历上的收支格子不该有它
                TransType.TRANSFER -> current
            }
        }
        return result
    }
}
