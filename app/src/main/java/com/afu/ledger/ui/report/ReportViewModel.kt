package com.afu.ledger.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afu.ledger.data.repository.CategoryRepository
import com.afu.ledger.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.YearMonth

/** 报表页状态。 */
data class ReportUiState(
    val report: MonthlyReport? = null,
    val canGoNext: Boolean = false,
)

class ReportViewModel(
    transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())

    val uiState: StateFlow<ReportUiState> = combine(
        transactionRepository.observeAll(),
        categoryRepository.observeAll(),
        month,
    ) { transactions, categories, selected ->
        ReportUiState(
            report = ReportAggregator.build(transactions, categories, selected),
            canGoNext = selected.isBefore(YearMonth.now()),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReportUiState(),
    )

    fun previousMonth() {
        month.update { it.minusMonths(1) }
    }

    fun nextMonth() {
        // 不允许翻到未来的月份：那里永远没有数据，只会让用户以为坏了
        month.update { current ->
            if (current.isBefore(YearMonth.now())) current.plusMonths(1) else current
        }
    }

    fun goToCurrentMonth() {
        month.value = YearMonth.now()
    }
}
