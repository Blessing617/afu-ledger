package com.afu.ledger.domain.model

/** 账户 + 当前余额，用于资产页展示。 */
data class AccountWithBalance(
    val account: Account,
    val balance: Money,
)
