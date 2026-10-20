package com.afu.ledger.domain.model

/**
 * 资产账户（现金、银行卡、信用卡等）。
 *
 * 注意：[initialBalance] 是账户建立时的余额，账户的「当前余额」不存放在这里，
 * 而是由「初始余额 + 收入合计 − 支出合计」实时计算，避免与交易表不一致。
 */
data class Account(
    val id: Long = 0L,
    val name: String,
    val type: AccountType,
    val initialBalance: Money,
    val colorArgb: Long,
    val iconKey: String,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long,
)
