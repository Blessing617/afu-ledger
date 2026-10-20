package com.afu.ledger.data.backup

import com.afu.ledger.data.local.entity.AccountEntity
import com.afu.ledger.data.local.entity.BudgetEntity
import com.afu.ledger.data.local.entity.CategoryEntity
import com.afu.ledger.data.local.entity.ScheduleRuleEntity
import com.afu.ledger.data.local.entity.TransactionEntity
import kotlinx.serialization.Serializable

/**
 * 备份文件的格式定义。
 *
 * 这些 DTO **刻意与 Room Entity 分开**：数据库加列、改列名都不应该让旧备份失效，
 * 备份格式只随 [VERSION] 变化。
 */
object BackupFormat {
    const val FORMAT = "afu-ledger-backup"

    /**
     * 备份格式版本。
     *
     * v1：账户 / 分类 / 交易 / 定时规则
     * v2：新增 budget 数组（导入侧同时接受 v1，预算按空处理）
     */
    const val VERSION = 2
}

@Serializable
data class BackupCounts(
    val accounts: Int = 0,
    val categories: Int = 0,
    val transactions: Int = 0,
    val scheduleRules: Int = 0,
    val budgets: Int = 0,
)

@Serializable
data class BackupAccount(
    val id: Long,
    val name: String,
    val type: String,
    val initialBalanceCents: Long,
    val colorArgb: Long,
    val iconKey: String,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = 0L,
) {
    fun toEntity() = AccountEntity(
        id = id,
        name = name,
        type = type,
        initialBalanceCents = initialBalanceCents,
        colorArgb = colorArgb,
        iconKey = iconKey,
        archived = archived,
        sortOrder = sortOrder,
        createdAt = createdAt,
    )

    companion object {
        fun from(entity: AccountEntity) = BackupAccount(
            id = entity.id,
            name = entity.name,
            type = entity.type,
            initialBalanceCents = entity.initialBalanceCents,
            colorArgb = entity.colorArgb,
            iconKey = entity.iconKey,
            archived = entity.archived,
            sortOrder = entity.sortOrder,
            createdAt = entity.createdAt,
        )
    }
}

@Serializable
data class BackupCategory(
    val id: Long,
    val name: String,
    val type: String,
    val iconKey: String,
    val colorArgb: Long,
    val sortOrder: Int = 0,
    val preset: Boolean = false,
) {
    fun toEntity() = CategoryEntity(
        id = id,
        name = name,
        type = type,
        iconKey = iconKey,
        colorArgb = colorArgb,
        sortOrder = sortOrder,
        preset = preset,
    )

    companion object {
        fun from(entity: CategoryEntity) = BackupCategory(
            id = entity.id,
            name = entity.name,
            type = entity.type,
            iconKey = entity.iconKey,
            colorArgb = entity.colorArgb,
            sortOrder = entity.sortOrder,
            preset = entity.preset,
        )
    }
}

@Serializable
data class BackupTransaction(
    val id: Long,
    val accountId: Long,
    val categoryId: Long,
    val type: String,
    val amountCents: Long,
    val note: String = "",
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val sourceType: String = "MANUAL",
    val scheduleRuleId: Long? = null,
    val backfilled: Boolean = false,
) {
    fun toEntity() = TransactionEntity(
        id = id,
        accountId = accountId,
        categoryId = categoryId,
        type = type,
        amountCents = amountCents,
        note = note,
        occurredAt = occurredAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        sourceType = sourceType,
        scheduleRuleId = scheduleRuleId,
        backfilled = backfilled,
    )

    companion object {
        fun from(entity: TransactionEntity) = BackupTransaction(
            id = entity.id,
            accountId = entity.accountId,
            categoryId = entity.categoryId,
            type = entity.type,
            amountCents = entity.amountCents,
            note = entity.note,
            occurredAt = entity.occurredAt,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            sourceType = entity.sourceType,
            scheduleRuleId = entity.scheduleRuleId,
            backfilled = entity.backfilled,
        )
    }
}

@Serializable
data class BackupScheduleRule(
    val id: Long,
    val accountId: Long,
    val categoryId: Long,
    val type: String,
    val amountCents: Long,
    val note: String = "",
    val frequency: String,
    val interval: Int = 1,
    val weekdays: String = "",
    val dayOfMonth: Int = 1,
    val monthOfYear: Int = 1,
    val minuteOfDay: Int = 540,
    val startEpochDay: Long,
    val endEpochDay: Long? = null,
    val maxOccurrences: Int? = null,
    val behavior: String = "AUTO_POST",
    val enabled: Boolean = true,
    val lastGeneratedEpochDay: Long? = null,
    val generatedCount: Int = 0,
    val pausedReason: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    fun toEntity() = ScheduleRuleEntity(
        id = id,
        accountId = accountId,
        categoryId = categoryId,
        type = type,
        amountCents = amountCents,
        note = note,
        frequency = frequency,
        interval = interval,
        weekdays = weekdays,
        dayOfMonth = dayOfMonth,
        monthOfYear = monthOfYear,
        minuteOfDay = minuteOfDay,
        startEpochDay = startEpochDay,
        endEpochDay = endEpochDay,
        maxOccurrences = maxOccurrences,
        behavior = behavior,
        enabled = enabled,
        lastGeneratedEpochDay = lastGeneratedEpochDay,
        generatedCount = generatedCount,
        pausedReason = pausedReason,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        fun from(entity: ScheduleRuleEntity) = BackupScheduleRule(
            id = entity.id,
            accountId = entity.accountId,
            categoryId = entity.categoryId,
            type = entity.type,
            amountCents = entity.amountCents,
            note = entity.note,
            frequency = entity.frequency,
            interval = entity.interval,
            weekdays = entity.weekdays,
            dayOfMonth = entity.dayOfMonth,
            monthOfYear = entity.monthOfYear,
            minuteOfDay = entity.minuteOfDay,
            startEpochDay = entity.startEpochDay,
            endEpochDay = entity.endEpochDay,
            maxOccurrences = entity.maxOccurrences,
            behavior = entity.behavior,
            enabled = entity.enabled,
            lastGeneratedEpochDay = entity.lastGeneratedEpochDay,
            generatedCount = entity.generatedCount,
            pausedReason = entity.pausedReason,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
    }
}

@Serializable
data class BackupBudget(
    val id: Long,
    val name: String,
    val amountCents: Long,
    val periodType: String,
    val monthStartDay: Int = 1,
    val customStartEpochDay: Long? = null,
    val customEndEpochDay: Long? = null,
    val categoryIds: String = "",
    val alertThresholdPercent: Int = 80,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    fun toEntity() = BudgetEntity(
        id = id,
        name = name,
        amountCents = amountCents,
        periodType = periodType,
        monthStartDay = monthStartDay,
        customStartEpochDay = customStartEpochDay,
        customEndEpochDay = customEndEpochDay,
        categoryIds = categoryIds,
        alertThresholdPercent = alertThresholdPercent,
        enabled = enabled,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        fun from(entity: BudgetEntity) = BackupBudget(
            id = entity.id,
            name = entity.name,
            amountCents = entity.amountCents,
            periodType = entity.periodType,
            monthStartDay = entity.monthStartDay,
            customStartEpochDay = entity.customStartEpochDay,
            customEndEpochDay = entity.customEndEpochDay,
            categoryIds = entity.categoryIds,
            alertThresholdPercent = entity.alertThresholdPercent,
            enabled = entity.enabled,
            sortOrder = entity.sortOrder,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
    }
}

/** 备份文件的头信息（不含实体数组，导出时先写它）。 */
@Serializable
data class BackupMeta(
    val format: String,
    val formatVersion: Int,
    val exportedAt: String,
    val appVersionName: String,
    val appVersionCode: Int,
    val schemaVersion: Int,
    val counts: BackupCounts,
)

/** 完整备份文档（导入时整体反序列化；导出时逐字段流式写，不构造本对象）。 */
@Serializable
data class BackupDocument(
    val format: String = "",
    val formatVersion: Int = 0,
    val exportedAt: String = "",
    val appVersionName: String = "",
    val appVersionCode: Int = 0,
    val schemaVersion: Int = 0,
    val counts: BackupCounts = BackupCounts(),
    val accounts: List<BackupAccount> = emptyList(),
    val categories: List<BackupCategory> = emptyList(),
    val transactions: List<BackupTransaction> = emptyList(),
    val scheduleRules: List<BackupScheduleRule> = emptyList(),
    /** v2 起才有；v1 备份读出来是空列表。 */
    val budgets: List<BackupBudget> = emptyList(),
)
