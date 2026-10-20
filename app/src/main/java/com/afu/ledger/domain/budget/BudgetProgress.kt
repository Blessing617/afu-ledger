package com.afu.ledger.domain.budget

import com.afu.ledger.domain.model.Budget

/** 预算的预警档位。 */
enum class BudgetAlertLevel {
    /** 用得很省。 */
    SAFE,

    /** 过半了，正常提醒。 */
    NOTICE,

    /** 达到用户设置的阈值。 */
    WARNING,

    /** 已经超支。 */
    OVER,
}

/** 一个预算在「当前这一期」的进度快照。 */
data class BudgetProgress(
    val budget: Budget,
    val window: BudgetWindow,
    val usedCents: Long,
) {
    val totalCents: Long get() = budget.amount.cents

    /** 剩余（可为负，表示超支金额）。 */
    val remainingCents: Long get() = totalCents - usedCents

    /** 使用率，0f..∞。 */
    val ratio: Float get() = if (totalCents <= 0L) 1f else usedCents.toFloat() / totalCents.toFloat()

    /** 进度条用的比例（0..1，超支时封顶 1，避免圆角被撑破）。 */
    val barFraction: Float get() = ratio.coerceIn(0f, 1f)

    val alertLevel: BudgetAlertLevel
        get() = when {
            ratio >= 1f -> BudgetAlertLevel.OVER
            ratio * 100f >= budget.alertThresholdPercent -> BudgetAlertLevel.WARNING
            ratio >= 0.6f -> BudgetAlertLevel.NOTICE
            else -> BudgetAlertLevel.SAFE
        }

    /** 展示用百分比文本，超支时也显示真实百分比。 */
    val percentText: String get() = "${(ratio * 100).toInt()}%"
}
