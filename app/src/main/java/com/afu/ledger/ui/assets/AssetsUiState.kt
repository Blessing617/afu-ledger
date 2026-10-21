package com.afu.ledger.ui.assets

import com.afu.ledger.domain.model.AccountWithBalance
import com.afu.ledger.domain.model.Money

data class AssetsUiState(
    val active: List<AccountWithBalance> = emptyList(),
    val archived: List<AccountWithBalance> = emptyList(),
    val totalAssets: Money = Money.ZERO,
    val totalLiabilities: Money = Money.ZERO,
    val netWorth: Money = Money.ZERO,
    /** 隐私遮罩：为 true 时资产页所有金额显示为 `¥••••`（账户行 `•••`）。 */
    val amountHidden: Boolean = false,
    val loaded: Boolean = false,
)
