package com.afu.ledger.domain.schedule

import com.afu.ledger.domain.model.Money
import com.afu.ledger.domain.model.TransType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** 重复的周期类型。 */
enum class ScheduleFrequency {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
}

/** 到期行为。 */
enum class ScheduleBehavior {
    /** 到点直接入账。 */
    AUTO_POST,

    /** 先生成待确认，用户确认后才入账。 */
    NEED_CONFIRM,
}

/**
 * 一条定时记账规则。
 *
 * 日期一律用 [LocalDate] 的**本地语义**保存，不保存换算好的绝对时间戳 ——
 * 时区或夏令时变化后要按新时区重新换算（见 [ScheduleCalculator]）。
 *
 * @param weekdays 仅 WEEKLY 使用；为空时取 [startDate] 的星期
 * @param dayOfMonth 仅 MONTHLY / YEARLY 使用；1–31，超出当月天数时收敛到月末
 * @param monthOfYear 仅 YEARLY 使用
 * @param lastGeneratedDate 幂等游标：最近一次已处理（入账或忽略）的期次日期
 * @param pausedReason 非空表示规则被系统暂停（账户/分类不可用），UI 要显示原因
 */
data class ScheduleRule(
    val id: Long = 0L,
    val accountId: Long,
    val categoryId: Long,
    val type: TransType,
    val amount: Money,
    val note: String = "",
    val frequency: ScheduleFrequency = ScheduleFrequency.MONTHLY,
    val interval: Int = 1,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val dayOfMonth: Int = 1,
    val monthOfYear: Int = 1,
    val timeOfDay: LocalTime = LocalTime.of(9, 0),
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null,
    val maxOccurrences: Int? = null,
    val behavior: ScheduleBehavior = ScheduleBehavior.AUTO_POST,
    val enabled: Boolean = true,
    val lastGeneratedDate: LocalDate? = null,
    val generatedCount: Int = 0,
    val pausedReason: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {

    /** 期数是否已达上限。 */
    val reachedEnd: Boolean
        get() = maxOccurrences != null && generatedCount >= maxOccurrences

    /** 规则的显示名：优先备注，其次分类占位。 */
    fun displayName(categoryName: String?): String =
        note.takeIf { it.isNotBlank() } ?: categoryName ?: "定时记账"
}
