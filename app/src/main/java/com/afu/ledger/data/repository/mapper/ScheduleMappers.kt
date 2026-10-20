package com.afu.ledger.data.repository.mapper

import com.afu.ledger.data.local.entity.ScheduleRuleEntity
import com.afu.ledger.domain.model.Money
import com.afu.ledger.domain.model.TransType
import com.afu.ledger.domain.schedule.ScheduleBehavior
import com.afu.ledger.domain.schedule.ScheduleFrequency
import com.afu.ledger.domain.schedule.ScheduleRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * 定时记账规则的 Entity ↔ domain 映射。
 *
 * 日期与时间按「本地语义」往返：epochDay ↔ LocalDate、分钟数 ↔ LocalTime。
 * 枚举解析失败时回落到安全默认值，避免脏数据导致整个列表崩掉。
 */
fun ScheduleRuleEntity.toDomain(): ScheduleRule = ScheduleRule(
    id = id,
    accountId = accountId,
    categoryId = categoryId,
    type = parseTransType(type),
    amount = Money(amountCents),
    note = note,
    frequency = runCatching { ScheduleFrequency.valueOf(frequency) }
        .getOrDefault(ScheduleFrequency.MONTHLY),
    interval = interval.coerceAtLeast(1),
    weekdays = parseWeekdays(weekdays),
    dayOfMonth = dayOfMonth.coerceIn(1, 31),
    monthOfYear = monthOfYear.coerceIn(1, 12),
    timeOfDay = minuteToTime(minuteOfDay),
    startDate = LocalDate.ofEpochDay(startEpochDay),
    endDate = endEpochDay?.let(LocalDate::ofEpochDay),
    maxOccurrences = maxOccurrences,
    behavior = runCatching { ScheduleBehavior.valueOf(behavior) }
        .getOrDefault(ScheduleBehavior.AUTO_POST),
    enabled = enabled,
    lastGeneratedDate = lastGeneratedEpochDay?.let(LocalDate::ofEpochDay),
    generatedCount = generatedCount,
    pausedReason = pausedReason,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ScheduleRule.toEntity(): ScheduleRuleEntity = ScheduleRuleEntity(
    id = id,
    accountId = accountId,
    categoryId = categoryId,
    type = type.name,
    amountCents = amount.cents,
    note = note,
    frequency = frequency.name,
    interval = interval.coerceAtLeast(1),
    weekdays = weekdays.sortedBy { it.value }.joinToString(",") { it.value.toString() },
    dayOfMonth = dayOfMonth.coerceIn(1, 31),
    monthOfYear = monthOfYear.coerceIn(1, 12),
    minuteOfDay = timeOfDay.hour * 60 + timeOfDay.minute,
    startEpochDay = startDate.toEpochDay(),
    endEpochDay = endDate?.toEpochDay(),
    maxOccurrences = maxOccurrences,
    behavior = behavior.name,
    enabled = enabled,
    lastGeneratedEpochDay = lastGeneratedDate?.toEpochDay(),
    generatedCount = generatedCount,
    pausedReason = pausedReason,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun parseWeekdays(raw: String): Set<DayOfWeek> =
    raw.split(',')
        .mapNotNull { it.trim().toIntOrNull() }
        .mapNotNull { value -> runCatching { DayOfWeek.of(value) }.getOrNull() }
        .toSet()

private fun minuteToTime(minuteOfDay: Int): LocalTime {
    val clamped = minuteOfDay.coerceIn(0, 24 * 60 - 1)
    return LocalTime.of(clamped / 60, clamped % 60)
}

private fun parseTransType(raw: String): TransType =
    runCatching { TransType.valueOf(raw) }.getOrDefault(TransType.EXPENSE)
