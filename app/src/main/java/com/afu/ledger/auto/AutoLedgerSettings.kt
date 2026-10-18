package com.afu.ledger.auto

/** 自动记账的持久化配置。默认关闭，必须用户显式开启。 */
data class AutoLedgerSettings(
    val enabled: Boolean = false,
    val defaultAccountId: Long? = null,
    val defaultCategoryId: Long? = null,
    val recognitionLogEnabled: Boolean = false,
)
