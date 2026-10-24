package com.afu.ledger.domain.model

/** 一笔交易的来源。手动记的、自动识别记的、定时规则生成的。 */
enum class TransactionSource {
    MANUAL,
    AUTO,
    SCHEDULED,
}

/** 一笔收入、支出或资产转移。 */
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
    /**
     * 资产转移的**转入账户**。`type == TRANSFER` 时非空，`accountId` 为转出账户；
     * 其它类型为 null。
     *
     * 单条记录带两个账户，而不是「一笔支出 + 一笔收入」：编辑、删除、备份都是原子的，
     * 不会出现只删掉半边、账户余额一边回退一边没回退的状态。
     */
    val transferAccountId: Long? = null,
)
