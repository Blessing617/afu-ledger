package com.afu.ledger.data.local.dao

/** 账户 + 实时计算出的当前余额，用于资产页展示。 */
data class AccountBalance(
    val id: Long,
    val name: String,
    val type: String,
    val initialBalanceCents: Long,
    val colorArgb: Long,
    val iconKey: String,
    val archived: Boolean,
    val sortOrder: Int,
    /** 初始余额 + 收入合计 − 支出合计。 */
    val balanceCents: Long,
)
