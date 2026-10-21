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

    // 这里曾有 formatCompactYuan / formatCompactWithSymbol（万 / 亿 / 万亿缩写）。
    // 已经移除：大额显示问题由布局解决（字号分档 + 允许折行），而不是把数字换成单位，
    // 否则资产页会出现「4.70万」这种读不出真实余额的形态。资产页的隐私遮罩是唯一的缩短形态。
}
