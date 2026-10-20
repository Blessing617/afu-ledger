package com.afu.ledger.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afu.ledger.data.repository.BudgetRepository
import com.afu.ledger.data.repository.CategoryRepository
import com.afu.ledger.data.repository.TransactionRepository
import com.afu.ledger.domain.budget.BudgetProgress
import com.afu.ledger.domain.model.Budget
import com.afu.ledger.domain.model.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BudgetViewModel(
    private val budgetRepository: BudgetRepository,
    categoryRepository: CategoryRepository,
    transactionRepository: TransactionRepository,
) : ViewModel() {

    val budgets: StateFlow<List<Budget>> = budgetRepository.observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val categories: StateFlow<List<Category>> = categoryRepository.observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    /** 每个预算的本期进度，按 id 索引；交易一变就重算。 */
    val progressByBudget: StateFlow<Map<Long, BudgetProgress>> = combine(
        budgetRepository.observeAll(),
        transactionRepository.observeAll(),
    ) { list, _ -> list }
        .mapLatest { list ->
            withContext(Dispatchers.Default) {
                list.associate { it.id to budgetRepository.progress(it) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap(),
        )

    fun save(budget: Budget) {
        viewModelScope.launch {
            if (budget.id == 0L) budgetRepository.create(budget) else budgetRepository.save(budget)
        }
    }

    fun delete(budget: Budget) {
        viewModelScope.launch { budgetRepository.delete(budget.id) }
    }

    fun setEnabled(budget: Budget, enabled: Boolean) {
        viewModelScope.launch { budgetRepository.save(budget.copy(enabled = enabled)) }
    }
}
