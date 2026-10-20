package com.afu.ledger.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afu.ledger.data.repository.AccountRepository
import com.afu.ledger.data.repository.CategoryRepository
import com.afu.ledger.data.repository.ScheduleRuleRepository
import com.afu.ledger.data.repository.TransactionRepository
import com.afu.ledger.domain.model.Account
import com.afu.ledger.domain.model.Category
import com.afu.ledger.domain.model.Money
import com.afu.ledger.domain.model.Transaction
import com.afu.ledger.domain.model.TransactionSource
import com.afu.ledger.domain.schedule.ScheduleRule
import com.afu.ledger.schedule.PendingOccurrenceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 定时记账列表与待确认期次的编排。
 *
 * 所有会改变期次/规则的操作都通过 [onScheduleChanged] 通知外层重排闹钟 ——
 * 否则新建一条规则后要等到下次开机或兜底任务才会真正生效。
 */
class ScheduleListViewModel(
    private val rules: ScheduleRuleRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val transactions: TransactionRepository,
    private val pendingStore: PendingOccurrenceStore,
    private val onScheduleChanged: () -> Unit,
) : ViewModel() {

    data class UiState(
        val rules: List<ScheduleRule> = emptyList(),
        val accounts: List<Account> = emptyList(),
        val categories: List<Category> = emptyList(),
        val pending: List<PendingOccurrenceStore.Occurrence> = emptyList(),
        val loading: Boolean = true,
    ) {
        fun categoryName(id: Long): String? = categories.firstOrNull { it.id == id }?.name
        fun accountName(id: Long): String? = accounts.firstOrNull { it.id == id }?.name
    }

    private val pending = MutableStateFlow<List<PendingOccurrenceStore.Occurrence>>(emptyList())

    val uiState: StateFlow<UiState> = combine(
        rules.observeAll(),
        accounts.observeActive(),
        categories.observeAll(),
        pending,
    ) { ruleList, accountList, categoryList, pendingList ->
        UiState(
            rules = ruleList,
            accounts = accountList,
            categories = categoryList,
            pending = pendingList,
            loading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState(),
    )

    init {
        refreshPending()
    }

    fun refreshPending() {
        viewModelScope.launch {
            pending.value = runCatching { pendingStore.list() }.getOrDefault(emptyList())
        }
    }

    fun create(rule: ScheduleRule) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            rules.create(rule.copy(createdAt = now, updatedAt = now))
            onScheduleChanged()
        }
    }

    fun update(rule: ScheduleRule) {
        viewModelScope.launch {
            rules.save(rule.copy(updatedAt = System.currentTimeMillis()))
            onScheduleChanged()
        }
    }

    fun setEnabled(rule: ScheduleRule, enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                // 恢复时从下一期继续，不补暂停期间的期次（新账户/分类刚换好，
                // 一下子补出两个月账单会把余额和报表全打乱）
                rules.setEnabled(rule.id, true, null)
            } else {
                rules.setEnabled(rule.id, false, rule.pausedReason)
            }
            onScheduleChanged()
        }
    }

    fun delete(rule: ScheduleRule) {
        viewModelScope.launch {
            rules.delete(rule.id)
            onScheduleChanged()
        }
    }

    /** 确认一条待确认期次：按规则快照的账户 / 分类入账。 */
    fun confirm(occurrence: PendingOccurrenceStore.Occurrence) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val today = java.time.LocalDate.now()
            val occurredDate = java.time.Instant.ofEpochMilli(occurrence.occurredAt)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
            transactions.addIgnoringDuplicate(
                Transaction(
                    accountId = occurrence.accountId,
                    categoryId = occurrence.categoryId,
                    type = occurrence.type,
                    amount = Money(occurrence.amountCents),
                    note = occurrence.note,
                    occurredAt = occurrence.occurredAt,
                    createdAt = now,
                    updatedAt = now,
                    source = TransactionSource.SCHEDULED,
                    scheduleRuleId = occurrence.ruleId,
                    backfilled = occurredDate.isBefore(today),
                ),
            )
            pendingStore.remove(occurrence.key)
            refreshPending()
        }
    }

    /** 忽略一条待确认期次：不产生交易，也不再提醒。 */
    fun ignore(occurrence: PendingOccurrenceStore.Occurrence) {
        viewModelScope.launch {
            pendingStore.remove(occurrence.key)
            refreshPending()
        }
    }
}
