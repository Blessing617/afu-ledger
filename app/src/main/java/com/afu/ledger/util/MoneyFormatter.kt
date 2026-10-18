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

    /** 带货币符号，例："¥12,345.67"。 */
    fun formatWithSymbol(money: Money): String = "¥" + formatYuan(money.cents)

    /** 带正负号，用于收支展示，例：支出 → "-25.50"，收入 → "+25.50"。 */
    fun formatSigned(money: Money, isExpense: Boolean): String {
        val sign = if (isExpense) "-" else "+"
        return sign + formatYuan(money.abs().cents)
    }
}
