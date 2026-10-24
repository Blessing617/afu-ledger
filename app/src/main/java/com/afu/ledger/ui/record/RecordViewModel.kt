package com.afu.ledger.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afu.ledger.data.repository.AccountRepository
import com.afu.ledger.data.repository.CategoryRepository
import com.afu.ledger.data.repository.TransactionRepository
import com.afu.ledger.domain.model.Category
import com.afu.ledger.domain.model.Money
import com.afu.ledger.domain.model.Transaction
import com.afu.ledger.domain.model.TransType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 记账面板的状态机。
 *
 * ## 「完成」为什么是一次性事件而不是状态位
 *
 * 面板的 ViewModel 挂在 Activity 的 ViewModelStore 上，面板关掉并不会销毁它。
 * 早先用 `finished: Boolean` 表达「已保存」，保存完就是 `true` 且再也没人清回来 ——
 * 下次打开面板，界面一组合就看到 `finished == true`，立刻播放关闭动画，
 * 用户看到的现象就是「点记一笔毫无反应」。
 *
 * 现在改成 [completed] 通道：事件被消费一次就没了，重复打开不受影响。
 * 同时 [reset] 在面板关闭时把输入清干净，避免上一次的金额/备注带进新一笔。
 */
class RecordViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val editingTransactionId: Long? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordUiState(isEditing = editingTransactionId != null))
    val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()

    private val completedChannel = Channel<Unit>(Channel.BUFFERED)

    /** 保存 / 删除完成后发一次；界面收到就关闭面板。 */
    val completed: Flow<Unit> = completedChannel.receiveAsFlow()

    private var allCategories: List<Category> = emptyList()

    /** 提交闸门：主按钮连点、键盘长按重复触发都只能产生一笔。 */
    private var saving = false

    init {
        observeAccounts()
        observeCategories()
        editingTransactionId?.let { loadForEdit(it) }
    }

    private fun observeAccounts() {
        viewModelScope.launch {
            accountRepository.observeActive().collect { accounts ->
                _uiState.update { state ->
                    val stillValid = accounts.any { it.id == state.selectedAccountId }
                    val transferStillValid = accounts.any { it.id == state.transferAccountId }
                    state.copy(
                        accounts = accounts,
                        selectedAccountId = if (stillValid) state.selectedAccountId else accounts.firstOrNull()?.id,
                        transferAccountId = if (transferStillValid) state.transferAccountId else null,
                    )
                }
            }
        }
    }

    private fun observeCategories() {
        viewModelScope.launch {
            categoryRepository.observeAll().collect { categories ->
                allCategories = categories
                applyCategoryFilter()
            }
        }
    }

    private fun loadForEdit(id: Long) {
        viewModelScope.launch {
            val transaction = transactionRepository.findById(id) ?: return@launch
            _uiState.update {
                it.copy(
                    type = transaction.type,
                    amountInput = Money(transaction.amount.cents).let { amount ->
                        val yuan = amount.cents / 100
                        val cents = amount.cents % 100
                        if (cents == 0L) yuan.toString() else "$yuan.${cents.toString().padStart(2, '0')}"
                    },
                    selectedCategoryId = transaction.categoryId,
                    selectedAccountId = transaction.accountId,
                    transferAccountId = transaction.transferAccountId,
                    note = transaction.note,
                    occurredAt = transaction.occurredAt,
                )
            }
            applyCategoryFilter()
        }
    }

    fun setType(type: TransType) {
        _uiState.update {
            it.copy(
                type = type,
                selectedCategoryId = null,
                transferAccountId = null,
                error = null,
            )
        }
        applyCategoryFilter()
    }

    fun setAmountInput(value: String) {
        _uiState.update { it.copy(amountInput = value, error = null) }
    }

    fun setCategory(categoryId: Long) {
        _uiState.update { it.copy(selectedCategoryId = categoryId, error = null) }
    }

    fun setAccount(accountId: Long) {
        _uiState.update { it.copy(selectedAccountId = accountId, error = null) }
    }

    /** 选转账的**转入**账户。 */
    fun setTransferAccount(accountId: Long) {
        _uiState.update { it.copy(transferAccountId = accountId, error = null) }
    }

    fun setNote(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    fun setOccurredAt(millis: Long) {
        _uiState.update { it.copy(occurredAt = millis) }
    }

    fun save() {
        if (saving) return
        val state = _uiState.value
        val amount = state.parsedAmount
        val accountId = state.selectedAccountId
        val transferTo = state.transferAccountId
        val categoryId = state.selectedCategoryId

        val invalid = when {
            amount == null -> "请输入有效金额"
            accountId == null -> "请选择账户"
            state.isTransfer && transferTo == null -> "请选择转入账户"
            state.isTransfer && transferTo == accountId -> "转出与转入账户不能相同"
            !state.isTransfer && categoryId == null -> "请选择分类"
            else -> null
        }
        if (invalid != null) {
            _uiState.update { it.copy(error = invalid) }
            return
        }

        saving = true
        _uiState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val resolvedCategoryId = if (state.isTransfer) {
                    // 转移没有消费分类，但 categoryId 是 NOT NULL：用内置「转账」分类兜住
                    categoryRepository.transferCategoryId()
                } else {
                    categoryId
                }
                if (resolvedCategoryId == null) {
                    _uiState.update { it.copy(error = "分类不可用，请重试") }
                    return@launch
                }

                val target = Transaction(
                    accountId = accountId!!,
                    categoryId = resolvedCategoryId,
                    type = state.type,
                    amount = amount!!,
                    note = state.note.trim(),
                    occurredAt = state.occurredAt,
                    createdAt = now,
                    updatedAt = now,
                    transferAccountId = if (state.isTransfer) transferTo else null,
                )

                if (state.isEditing && editingTransactionId != null) {
                    val existing = transactionRepository.findById(editingTransactionId) ?: return@launch
                    transactionRepository.update(
                        target.copy(
                            id = existing.id,
                            createdAt = existing.createdAt,
                            source = existing.source,
                            scheduleRuleId = existing.scheduleRuleId,
                            backfilled = existing.backfilled,
                        ),
                    )
                } else {
                    transactionRepository.add(target)
                }
                completedChannel.send(Unit)
            } finally {
                saving = false
                _uiState.update { it.copy(saving = false) }
            }
        }
    }

    fun delete() {
        val id = editingTransactionId ?: return
        if (saving) return
        saving = true
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                transactionRepository.delete(id)
                completedChannel.send(Unit)
            } finally {
                saving = false
                _uiState.update { it.copy(saving = false) }
            }
        }
    }

    /**
     * 面板关闭时调用：把状态清回「新的一笔」。
     *
     * **只清用户输入，不动账户与分类列表。** 它们是数据而不是输入：
     * `observeActive()` 这类 Flow 只在数据变化时重新发射，把它们一起清空之后
     * 就不会再自己长回来 —— 下一次打开面板会看到「还没有可用账户」，
     * 明明资产页里账户都在。
     *
     * 编辑模式下重新拉一次那笔交易，否则下次打开会看到空表单。
     */
    fun reset() {
        saving = false
        _uiState.update { state ->
            val categoryId = state.categories.firstOrNull()?.id
            RecordUiState(
                accounts = state.accounts,
                categories = state.categories,
                selectedAccountId = state.accounts.firstOrNull()?.id,
                selectedCategoryId = categoryId,
                isEditing = editingTransactionId != null,
            )
        }
        editingTransactionId?.let { loadForEdit(it) }
    }

    /** 面板打开时补一次数据：Flow 只在变化时发射，冷启动首帧可能还没到。 */
    fun refresh() {
        viewModelScope.launch {
            val accounts = accountRepository.observeActive().first()
            _uiState.update { state ->
                val stillValid = accounts.any { it.id == state.selectedAccountId }
                state.copy(
                    accounts = accounts,
                    selectedAccountId = if (stillValid) state.selectedAccountId else accounts.firstOrNull()?.id,
                )
            }
        }
    }

    private fun applyCategoryFilter() {
        _uiState.update { state ->
            // 转账没有分类可选：分类区在界面上被转出/转入账户选择替代
            if (state.type == TransType.TRANSFER) {
                return@update state.copy(categories = emptyList(), selectedCategoryId = null)
            }
            val filtered = allCategories.filter { it.type == state.type }
            val stillValid = filtered.any { it.id == state.selectedCategoryId }
            state.copy(
                categories = filtered,
                selectedCategoryId = if (stillValid) state.selectedCategoryId else filtered.firstOrNull()?.id,
            )
        }
    }
}
