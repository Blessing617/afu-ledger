package com.afu.ledger.ui.record

import com.afu.ledger.domain.model.Account
import com.afu.ledger.domain.model.Category
import com.afu.ledger.domain.model.Money
import com.afu.ledger.domain.model.TransType

data class RecordUiState(
    val type: TransType = TransType.EXPENSE,
    val amountInput: String = "",
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val selectedCategoryId: Long? = null,
    val selectedAccountId: Long? = null,
    val note: String = "",
    val occurredAt: Long = System.currentTimeMillis(),
    val isEditing: Boolean = false,
    val error: String? = null,
    val finished: Boolean = false,
) {
    val parsedAmount: Money? get() = Money.parseYuan(amountInput)?.takeIf { it.isPositive() }

    val canSave: Boolean
        get() = parsedAmount != null && selectedCategoryId != null && selectedAccountId != null
}
