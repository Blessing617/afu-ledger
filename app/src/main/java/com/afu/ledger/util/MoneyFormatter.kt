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
     * 紧凑金额，带中文单位：万 / 亿 / 万亿。
     *
     * 为什么要它：净资产卡片的大字号放不下 `¥99,999,999,999,956.00`（21 个字符），
     * 真机上要么折成两行、要么被卡片边缘裁掉。改成 `¥99.99万亿` 后长度稳定在 10 个字符左右。
     *
     * 分档按**绝对值**判断，负数同样适用：
     * - `< 1 万` → 与 [formatYuan] 一致（带千分位）
     * - `≥ 1 万` → 两位小数的「万」
     * - `≥ 1 亿` → 两位小数的「亿」
     * - `≥ 1 万亿` → 两位小数的「万亿」
     *
     * 例：`12,345,678` 分 → `123,456.78`；`1_000_000_000` 分 → `1000.00万`。
     */
    fun formatCompactYuan(cents: Long): String {
        val absCents = if (cents == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(cents)
        val (divisor, unit) = when {
            absCents >= WAN_YI_CENTS -> WAN_YI_CENTS to "万亿"
            absCents >= YI_CENTS -> YI_CENTS to "亿"
            absCents >= WAN_CENTS -> WAN_CENTS to "万"
            else -> return formatYuan(cents)
        }
        // 用**截断**而不是四舍五入：`99.9999万亿` 显示成 `100.00万亿` 会夸大成「正好一百万亿」。
        // 少报一点比多报一点安全，完整数字点一下就能看到。
        val value = BigDecimal(cents).divide(BigDecimal(divisor), 2, RoundingMode.DOWN)
        return value.toPlainString() + unit
    }

    /** 紧凑金额 + 货币符号，负数写成 `-¥1.23亿`。 */
    fun formatCompactWithSymbol(money: Money): String {
        val negative = money.cents < 0
        val body = formatCompactYuan(money.abs().cents)
        return if (negative) "-¥$body" else "¥$body"
    }

    /** 1 万元（= 1_000_000 分）。 */
    const val WAN_CENTS: Long = 1_000_000L

    /** 1 亿元（= 10_000_000_000 分）。 */
    const val YI_CENTS: Long = 10_000_000_000L

    /** 1 万亿元（= 100_000_000_000_000 分）。 */
    const val WAN_YI_CENTS: Long = 100_000_000_000_000L
}
