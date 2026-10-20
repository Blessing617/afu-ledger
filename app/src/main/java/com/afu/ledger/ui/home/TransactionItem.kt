package com.afu.ledger.ui.home

import com.afu.ledger.domain.model.Transaction

/** 列表中的一条交易（已关联分类与账户的展示信息）。 */
data class TransactionItem(
    val transaction: Transaction,
    val categoryName: String,
    val categoryIcon: String,
    val categoryColorArgb: Long,
    val accountName: String,
)
