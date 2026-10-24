package com.afu.ledger.ui.home

import com.afu.ledger.domain.model.Transaction

/** 列表中的一条交易（已关联分类与账户的展示信息）。 */
data class TransactionItem(
    val transaction: Transaction,
    val categoryName: String,
    val categoryIcon: String,
    val categoryColorArgb: Long,
    val accountName: String,
    /** 资产转移的转入账户名；非转移为 null。 */
    val transferToAccountName: String? = null,
) {
    val isTransfer: Boolean get() = transaction.transferAccountId != null

    /** 列表标题：转移显示「工资卡 → 消费卡」，其它显示分类名。 */
    val title: String
        get() = if (isTransfer) "$accountName → ${transferToAccountName ?: "已删除账户"}" else categoryName
}
