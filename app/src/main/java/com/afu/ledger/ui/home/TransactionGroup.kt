package com.afu.ledger.ui.home

import com.afu.ledger.domain.model.Money

/** 按天分组的交易，标题展示日期与当日净额。 */
data class TransactionGroup(
    val dayKey: String,
    val label: String,
    val netAmount: Money,
    val items: List<TransactionItem>,
)
