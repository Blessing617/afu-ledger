package com.afu.ledger.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 定时记账规则。
 *
 * 日期一律存**本地日期语义**（epochDay）与「分钟数」，不存换算后的时间戳：
 * 时区或夏令时变化后按新时区重新换算，规则不会整体漂移。
 *
 * @param weekdays ISO 星期（1=周一 … 7=周日），逗号分隔；仅 WEEKLY 使用
 * @param minuteOfDay 0–1439
 * @param lastGeneratedEpochDay 幂等游标：最近一次已处理（入账或忽略）的期次
 */
@Entity(
    tableName = "schedule_rules",
    indices = [Index("accountId"), Index("categoryId")],
)
data class ScheduleRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val accountId: Long,
    val categoryId: Long,
    /** `scheduled_ledger` 只做收入 / 支出，值同 [com.afu.ledger.domain.model.TransType]。 */
    val type: String,
    val amountCents: Long,
    val note: String = "",
    /** DAILY / WEEKLY / MONTHLY / YEARLY。 */
    val frequency: String,
    val interval: Int = 1,
    val weekdays: String = "",
    val dayOfMonth: Int = 1,
    val monthOfYear: Int = 1,
    val minuteOfDay: Int = 540,
    val startEpochDay: Long,
    val endEpochDay: Long? = null,
    val maxOccurrences: Int? = null,
    /** AUTO_POST / NEED_CONFIRM。 */
    val behavior: String = "AUTO_POST",
    val enabled: Boolean = true,
    val lastGeneratedEpochDay: Long? = null,
    val generatedCount: Int = 0,
    /** 非空表示被系统暂停（账户/分类不可用），UI 显示原因。 */
    val pausedReason: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
