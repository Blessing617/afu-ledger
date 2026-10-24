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
    /** 转账的转入账户；非转账类型时为 null。 */
    val transferAccountId: Long? = null,
    val note: String = "",
    val occurredAt: Long = System.currentTimeMillis(),
    val isEditing: Boolean = false,
    /** 提交中：主按钮置灰并拒绝重复提交。 */
    val saving: Boolean = false,
    val error: String? = null,
) {
    val isTransfer: Boolean get() = type == TransType.TRANSFER

    val parsedAmount: Money? get() = Money.parseYuan(amountInput)?.takeIf { it.isPositive() }

    /** 转出与转入不能是同一个账户。 */
    val transferInvalid: Boolean
        get() = isTransfer && transferAccountId != null && transferAccountId == selectedAccountId

    val canSave: Boolean
        get() = !saving &&
            parsedAmount != null &&
            selectedAccountId != null &&
            if (isTransfer) {
                transferAccountId != null && !transferInvalid
            } else {
                selectedCategoryId != null
            }
}
