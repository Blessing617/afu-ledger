package com.afu.ledger.data.repository.mapper

import com.afu.ledger.data.local.dao.AccountBalance
import com.afu.ledger.data.local.entity.AccountEntity
import com.afu.ledger.data.local.entity.CategoryEntity
import com.afu.ledger.data.local.entity.TransactionEntity
import com.afu.ledger.domain.model.Account
import com.afu.ledger.domain.model.AccountType
import com.afu.ledger.domain.model.AccountWithBalance
import com.afu.ledger.domain.model.Category
import com.afu.ledger.domain.model.Money
import com.afu.ledger.domain.model.Transaction
import com.afu.ledger.domain.model.TransactionSource
import com.afu.ledger.domain.model.TransType

/**
 * Entity ↔ domain model 映射。
 *
 * 集中放在这里，Repository 只做「取数据 + 调映射」，不在 UI 层出现 Entity。
 * 枚举字段以 name 字符串入库，解析失败时回落到安全的默认值，避免历史脏数据导致崩溃。
 */

fun AccountEntity.toDomain(): Account = Account(
    id = id,
    name = name,
    type = parseAccountType(type),
    initialBalance = Money(initialBalanceCents),
    colorArgb = colorArgb,
    iconKey = iconKey,
    archived = archived,
    sortOrder = sortOrder,
    createdAt = createdAt,
)

fun Account.toEntity(): AccountEntity = AccountEntity(
    id = id,
    name = name,
    type = type.name,
    initialBalanceCents = initialBalance.cents,
    colorArgb = colorArgb,
    iconKey = iconKey,
    archived = archived,
    sortOrder = sortOrder,
    createdAt = createdAt,
)

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    type = parseTransType(type),
    iconKey = iconKey,
    colorArgb = colorArgb,
    sortOrder = sortOrder,
    preset = preset,
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    type = type.name,
    iconKey = iconKey,
    colorArgb = colorArgb,
    sortOrder = sortOrder,
    preset = preset,
)

fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    accountId = accountId,
    categoryId = categoryId,
    type = parseTransType(type),
    amount = Money(amountCents),
    note = note,
    occurredAt = occurredAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    source = parseSource(sourceType),
    scheduleRuleId = scheduleRuleId,
    backfilled = backfilled,
)

fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    accountId = accountId,
    categoryId = categoryId,
    type = type.name,
    amountCents = amount.cents,
    note = note,
    occurredAt = occurredAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    sourceType = source.name,
    scheduleRuleId = scheduleRuleId,
    backfilled = backfilled,
)

fun AccountBalance.toDomain(): AccountWithBalance = AccountWithBalance(
    account = Account(
        id = id,
        name = name,
        type = parseAccountType(type),
        initialBalance = Money(initialBalanceCents),
        colorArgb = colorArgb,
        iconKey = iconKey,
        archived = archived,
        sortOrder = sortOrder,
        createdAt = 0L,
    ),
    balance = Money(balanceCents),
)

private fun parseAccountType(raw: String): AccountType =
    runCatching { AccountType.valueOf(raw) }.getOrDefault(AccountType.WECHAT)

private fun parseTransType(raw: String): TransType =
    runCatching { TransType.valueOf(raw) }.getOrDefault(TransType.EXPENSE)

private fun parseSource(raw: String): TransactionSource =
    runCatching { TransactionSource.valueOf(raw) }.getOrDefault(TransactionSource.MANUAL)
