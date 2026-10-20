package com.afu.ledger.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 预算。
 *
 * 维度用「分类列表」表达：
 * - `categoryIds` 为空 → 总预算（统计所有支出）
 * - 非空 → 分类预算（只统计这些分类）
 *
 * 周期用 [periodType] + 各周期的参数列表达，计算集中在 `domain.budget.BudgetPeriod`。
 * 金额一律存「分」，与交易表保持一致，避免浮点误差。
 */
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val amountCents: Long,
    /** MONTHLY / WEEKLY / YEARLY / CUSTOM */
    val periodType: String,
    /** 月度预算的起始日 1–28（只对 MONTHLY 生效；大于当月天数时收敛到月末）。 */
    val monthStartDay: Int = 1,
    /** 自定义区间起点（epochDay，含当天），仅 CUSTOM 使用。 */
    val customStartEpochDay: Long? = null,
    /** 自定义区间终点（epochDay，含当天），仅 CUSTOM 使用。 */
    val customEndEpochDay: Long? = null,
    /** 逗号分隔的分类 id；空串表示总预算。 */
    val categoryIds: String = "",
    /** 预警阈值（百分比 1–100），达到该使用率开始橙色提示。 */
    val alertThresholdPercent: Int = 80,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
