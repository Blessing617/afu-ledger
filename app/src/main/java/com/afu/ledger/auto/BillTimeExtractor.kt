package com.afu.ledger.auto

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 从账单详情页解析**这笔交易真实发生的时间**。
 *
 * ## 为什么必须解析它
 *
 * 旧实现把识别时刻当成交易时间写库，于是「昨天支付的账单，今天点开确认」就记在了今天。
 * 用户反馈的「识别信息不全，比如昨天的账单记在今天」正是这个原因。
 *
 * 真机实测账单页至少有四种时间标签，不同账单类型用不同的：
 *
 * | 应用 | 场景 | 标签 |
 * |---|---|---|
 * | 支付宝 | 普通支付 | 支付时间 |
 * | 支付宝 | 退款 / 转账收入 | 创建时间 |
 * | 微信 | 普通支付 | 支付时间 |
 * | 微信 | 红包 / 转账收入 | 收款时间（转账页同时还有转账时间） |
 */
object BillTimeExtractor {

    /** 时间字段的标签，按可信度排序：专门的支付/收款时间优先于通用的创建时间。 */
    val labels: List<String> = listOf(
        "支付时间",
        "收款时间",
        "交易时间",
        "转账时间",
        "创建时间",
    )

    /**
     * 账单页的时间格式。
     *
     * 已见：`2026-10-19 18:01:22`、`2026年10月18日 20:10:02`、`2026-10-19 18:01`。
     * 秒可有可无；中英文日期分隔符都接受。
     */
    private val pattern = Regex(
        """(\d{4})\s*[-/年]\s*(\d{1,2})\s*[-/月]\s*(\d{1,2})\s*日?\s*(\d{1,2}):(\d{2})(?::(\d{2}))?""",
    )

    /** 解析结果。 */
    data class Result(
        val epochMillis: Long,
        /** 命中的标签，写进日志方便排查「为什么时间不对」。 */
        val label: String,
        val raw: String,
    )

    /**
     * 按标签依次取值并解析。全部失败返回 null。
     *
     * @param zone 时区，默认系统时区；测试注入固定时区以获得稳定结果
     */
    fun extract(
        fields: BillFieldParser,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Result? {
        for (label in labels) {
            // 逐个候选校验：标签下面紧跟的可能是另一个标签（真机红包页就是
            // 「收款时间」下面是「交易单号」、值反而在上面一行），
            // 所以不能只看第一项，要试到这个值真的像个日期时间为止。
            for (value in fields.valueCandidates(label)) {
                val match = pattern.find(value) ?: continue
                val millis = toMillis(match, zone) ?: continue
                return Result(epochMillis = millis, label = label, raw = value)
            }
        }
        // 兜底：整页扫一遍日期时间格式。仅当页面上恰好只有一个时才采信，
        // 否则无法判断那是交易时间还是别的记录的时间。
        val found = fields.lineTexts.mapNotNull { line ->
            pattern.find(line)?.let { toMillis(it, zone) }
        }.distinct()
        if (found.size == 1) {
            val match = pattern.find(fields.lineTexts.first { line -> pattern.containsMatchIn(line) })
            return Result(epochMillis = found.single(), label = "未标注标签", raw = match?.value.orEmpty())
        }
        return null
    }

    private fun toMillis(match: MatchResult, zone: ZoneId): Long? = runCatching {
        val (year, month, day, hour, minute, second) = match.destructured
        val dateTime = LocalDateTime.of(
            year.toInt(),
            month.toInt(),
            day.toInt(),
            hour.toInt(),
            minute.toInt(),
            second.toIntOrNull() ?: 0,
        )
        dateTime.atZone(zone).toInstant().toEpochMilli()
    }.getOrNull()

    /** 供测试与格式化使用。 */
    fun format(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), zone)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
}
