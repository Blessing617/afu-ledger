package com.afu.ledger.ui.home

import com.afu.ledger.domain.model.Money

data class HomeUiState(
    val groups: List<TransactionGroup> = emptyList(),
    val monthIncome: Money = Money.ZERO,
    val monthExpense: Money = Money.ZERO,
    val hasAccounts: Boolean = false,
    val loaded: Boolean = false,
) {
    val isEmpty: Boolean get() = groups.isEmpty()
}
