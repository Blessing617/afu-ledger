package com.afu.ledger.domain.model

/** 一笔收入或支出。 */
data class Transaction(
    val id: Long = 0L,
    val accountId: Long,
    val categoryId: Long,
    val type: TransType,
    val amount: Money,
    val note: String = "",
    /** 交易发生时间（UTC 毫秒），用于按日分组。 */
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
)
