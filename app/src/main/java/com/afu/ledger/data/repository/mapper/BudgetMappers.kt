package com.afu.ledger.data.repository.mapper

import com.afu.ledger.data.local.entity.BudgetEntity
import com.afu.ledger.domain.model.Budget
import com.afu.ledger.domain.model.BudgetPeriodType
import com.afu.ledger.domain.model.Money

fun BudgetEntity.toDomain(): Budget = Budget(
    id = id,
    name = name,
    amount = Money(amountCents),
    periodType = runCatching { BudgetPeriodType.valueOf(periodType) }
        .getOrDefault(BudgetPeriodType.MONTHLY),
    monthStartDay = monthStartDay,
    customStartEpochDay = customStartEpochDay,
    customEndEpochDay = customEndEpochDay,
    categoryIds = categoryIds
        .split(',')
        .mapNotNull { it.trim().toLongOrNull() }
        .distinct(),
    alertThresholdPercent = alertThresholdPercent,
    enabled = enabled,
    sortOrder = sortOrder,
)

fun Budget.toEntity(now: Long = System.currentTimeMillis()): BudgetEntity = BudgetEntity(
    id = id,
    name = name,
    amountCents = amount.cents,
    periodType = periodType.name,
    monthStartDay = monthStartDay,
    customStartEpochDay = customStartEpochDay,
    customEndEpochDay = customEndEpochDay,
    categoryIds = categoryIds.joinToString(","),
    alertThresholdPercent = alertThresholdPercent,
    enabled = enabled,
    sortOrder = sortOrder,
    createdAt = now,
    updatedAt = now,
)
