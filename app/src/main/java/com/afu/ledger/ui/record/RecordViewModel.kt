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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RecordViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val editingTransactionId: Long? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordUiState(isEditing = editingTransactionId != null))
    val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()

    private var allCategories: List<Category> = emptyList()

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
                    state.copy(
                        accounts = accounts,
                        selectedAccountId = if (stillValid) state.selectedAccountId else accounts.firstOrNull()?.id,
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
                    note = transaction.note,
                    occurredAt = transaction.occurredAt,
                )
            }
            applyCategoryFilter()
        }
    }

    fun setType(type: TransType) {
        _uiState.update { it.copy(type = type, selectedCategoryId = null) }
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

    fun setNote(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    fun setOccurredAt(millis: Long) {
        _uiState.update { it.copy(occurredAt = millis) }
    }

    fun save() {
        val state = _uiState.value
        val amount = state.parsedAmount
        val categoryId = state.selectedCategoryId
        val accountId = state.selectedAccountId
        if (amount == null || categoryId == null || accountId == null) {
            _uiState.update { it.copy(error = "请填写有效金额，并选择分类与账户") }
            return
        }

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (state.isEditing && editingTransactionId != null) {
                val existing = transactionRepository.findById(editingTransactionId)
                    ?: return@launch
                transactionRepository.update(
                    existing.copy(
                        accountId = accountId,
                        categoryId = categoryId,
                        type = state.type,
                        amount = amount,
                        note = state.note.trim(),
                        occurredAt = state.occurredAt,
                        updatedAt = now,
                    ),
                )
            } else {
                transactionRepository.add(
                    Transaction(
                        accountId = accountId,
                        categoryId = categoryId,
                        type = state.type,
                        amount = amount,
                        note = state.note.trim(),
                        occurredAt = state.occurredAt,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
            _uiState.update { it.copy(finished = true) }
        }
    }

    fun delete() {
        val id = editingTransactionId ?: return
        viewModelScope.launch {
            transactionRepository.delete(id)
            _uiState.update { it.copy(finished = true) }
        }
    }

    private fun applyCategoryFilter() {
        _uiState.update { state ->
            val filtered = allCategories.filter { it.type == state.type }
            val stillValid = filtered.any { it.id == state.selectedCategoryId }
            state.copy(
                categories = filtered,
                selectedCategoryId = if (stillValid) state.selectedCategoryId else filtered.firstOrNull()?.id,
            )
        }
    }
}
