package com.afu.ledger.domain.model

/** 预算周期类型。 */
enum class BudgetPeriodType {
    /** 自然月，起始日可调。 */
    MONTHLY,

    /** ISO 周（周一 00:00 起）。 */
    WEEKLY,

    /** 自然年。 */
    YEARLY,

    /** 用户指定的固定区间（一次性）。 */
    CUSTOM,
}

/**
 * 预算（领域模型）。
 *
 * [categoryIds] 为空表示总预算；非空表示只统计这些分类的支出。
 */
data class Budget(
    val id: Long = 0L,
    val name: String,
    val amount: Money,
    val periodType: BudgetPeriodType,
    val monthStartDay: Int = 1,
    val customStartEpochDay: Long? = null,
    val customEndEpochDay: Long? = null,
    val categoryIds: List<Long> = emptyList(),
    val alertThresholdPercent: Int = 80,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
) {
    val isTotalBudget: Boolean get() = categoryIds.isEmpty()

    /** 使用率（0f..∞），总额为 0 时返回 1f 避免除零。 */
    fun usageRatio(usedCents: Long): Float =
        if (amount.cents <= 0L) 1f else usedCents.toFloat() / amount.cents.toFloat()

    fun remaining(usedCents: Long): Money = Money(amount.cents - usedCents)
}
