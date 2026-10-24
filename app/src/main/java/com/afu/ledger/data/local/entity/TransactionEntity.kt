package com.afu.ledger.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("accountId"),
        Index("categoryId"),
        Index("occurredAt"),
        // 定时记账的幂等键：同一规则同一期次只允许一笔。
        // SQLite 唯一索引允许多个 NULL，所以手动 / 自动记账（scheduleRuleId 为 NULL）不受影响。
        Index(value = ["scheduleRuleId", "occurredAt"], unique = true),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val accountId: Long,
    val categoryId: Long,
    val type: String,
    val amountCents: Long,
    val note: String = "",
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    /** MANUAL / AUTO / SCHEDULED。 */
    @ColumnInfo(defaultValue = "MANUAL") val sourceType: String = "MANUAL",
    val scheduleRuleId: Long? = null,
    @ColumnInfo(defaultValue = "0") val backfilled: Boolean = false,
    /**
     * 资产转移的转入账户；非转移交易为 NULL。
     *
     * 不加外键：账户被删除时，转移由账户删除的事务（它本来就会级联删掉名下交易）一并清理，
     * 再叠一个外键只会让「删账户」的路径多一种失败方式。
     */
    val transferAccountId: Long? = null,
)
