package com.afu.ledger.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 日期分组与展示。 */
object DateFormatter {

    private val dayFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val monthDayFormat = DateTimeFormatter.ofPattern("M月d日")
    private val fullFormat = DateTimeFormatter.ofPattern("yyyy年M月d日")

    private fun zone(): ZoneId = ZoneId.systemDefault()

    fun toLocalDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone()).toLocalDate()

    /** 用于分组的稳定 key，例："2026-10-18"。 */
    fun dayKey(millis: Long): String = dayFormat.format(toLocalDate(millis))

    /** 某天的起始毫秒（本地时区）。 */
    fun startOfDay(millis: Long): Long =
        toLocalDate(millis).atStartOfDay(zone()).toInstant().toEpochMilli()

    /** 某天的结束毫秒（不含），即次日零点。 */
    fun endOfDayExclusive(millis: Long): Long =
        toLocalDate(millis).plusDays(1).atStartOfDay(zone()).toInstant().toEpochMilli()

    /** 友好日期标签：今天 / 昨天 / 10月18日 / 2025年10月18日。 */
    fun friendlyDayLabel(millis: Long): String {
        val date = toLocalDate(millis)
        val today = LocalDate.now(zone())
        return when (date) {
            today -> "今天"
            today.minusDays(1) -> "昨天"
            else -> if (date.year == today.year) monthDayFormat.format(date) else fullFormat.format(date)
        }
    }
}
