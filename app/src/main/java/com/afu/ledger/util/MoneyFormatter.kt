package com.afu.ledger.util

import com.afu.ledger.domain.model.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat

/** 金额展示格式化：内部是「分」，展示为带千分位的元。 */
object MoneyFormatter {

    private val yuanFormat = DecimalFormat("#,##0.00")

    /** 例：1234567 分 → "12,345.67"。 */
    fun formatYuan(cents: Long): String {
        val yuan = BigDecimal(cents).movePointLeft(2).setScale(2, RoundingMode.HALF_UP)
        return yuanFormat.format(yuan)
    }

    /**
     * 带货币符号，例："¥12,345.67"；负数写成 "-¥12,345.67"。
     *
     * 负号必须在货币符号**前面**：`¥-65.90` 是把符号当成数字的一部分，
     * 中文语境下读起来像排版错误（真机上第一眼就是这个观感）。
     */
    fun formatWithSymbol(money: Money): String {
        val negative = money.cents < 0
        val body = formatYuan(money.abs().cents)
        return if (negative) "-¥$body" else "¥$body"
    }

    /** 带正负号，用于收支展示，例：支出 → "-25.50"，收入 → "+25.50"。 */
    fun formatSigned(money: Money, isExpense: Boolean): String {
        val sign = if (isExpense) "-" else "+"
        return sign + formatYuan(money.abs().cents)
    }

    /**
     * 带正负号的**完整**金额（不带货币符号），例：
     * 1234567 分 → "+12,345.67"，-16345 分 → "-163.45"。
     *
     * 明细页月历格子只有 1/7 屏宽，但宽度不是缩写的理由：那里以前把 ≥10000 元写成
     * "1k"、把 100 元以上的小数直接截断，17,165.50 显示成 "+1k"，用户以为记账丢数据了。
     * 现在数字保持完整，适配交给布局（见 CalendarStrip 的字号实测），
     * 与资产页「金额 MUST 完整显示」的原则一致。
     *
     * 用 BigDecimal 取绝对值，Long.MIN_VALUE 也不会溢出。
     */
    fun formatSignedYuan(cents: Long): String {
        val sign = if (cents < 0) "-" else "+"
        val yuan = BigDecimal(cents).abs().movePointLeft(2).setScale(2, RoundingMode.HALF_UP)
        return sign + yuanFormat.format(yuan)
    }

    // 这里曾有 formatCompactYuan / formatCompactWithSymbol（万 / 亿 / 万亿缩写）。
    // 已经移除：大额显示问题由布局解决（字号分档 + 允许折行），而不是把数字换成单位，
    // 否则资产页会出现「4.70万」这种读不出真实余额的形态。资产页的隐私遮罩是唯一的缩短形态。
}
