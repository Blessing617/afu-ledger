package com.afu.ledger.domain.model

/** 一笔交易的来源。手动记的、自动识别记的、定时规则生成的。 */
enum class TransactionSource {
    MANUAL,
    AUTO,
    SCHEDULED,
}

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
    val source: TransactionSource = TransactionSource.MANUAL,
    /** 由定时记账生成时，对应的规则 id。 */
    val scheduleRuleId: Long? = null,
    /** 是否属于事后补记（设备当时没执行）。 */
    val backfilled: Boolean = false,
)
