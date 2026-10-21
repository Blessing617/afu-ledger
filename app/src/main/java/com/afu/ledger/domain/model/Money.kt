package com.afu.ledger.domain.model

/**
 * 金额值对象。
 *
 * 以「分」为最小单位存储为 [Long]，彻底避免 Double/Float 带来的精度误差。
 * 所有金额运算都应通过本类型完成，不要在外层直接用 Long 加减。
 */
@JvmInline
value class Money(val cents: Long) : Comparable<Money> {

    operator fun plus(other: Money): Money = Money(cents + other.cents)

    operator fun minus(other: Money): Money = Money(cents - other.cents)

    operator fun unaryMinus(): Money = Money(-cents)

    override fun compareTo(other: Money): Int = cents.compareTo(other.cents)

    fun abs(): Money = Money(if (cents < 0) -cents else cents)

    /** 该金额是否为正数（用于区分收入/支出合计）。 */
    fun isPositive(): Boolean = cents > 0

    companion object {
        val ZERO: Money = Money(0)

        /** 金额输入的整数位上限。 */
        const val MAX_INTEGER_DIGITS = 9

        /** 金额输入的小数位上限。 */
        const val MAX_FRACTION_DIGITS = 2

        /** 允许输入的最大金额：999,999,999.99 元。 */
        val MAX: Money = Money(999_999_999_99L)

        /**
         * 从用户输入的元字符串解析。
         *
         * 容错用户可能带进来的格式：首尾空格、千分位逗号（只允许出现在整数部分）、前导 `+`。
         *
         * 上限为 [MAX_INTEGER_DIGITS] 位整数、[MAX_FRACTION_DIGITS] 位小数。超限的输入直接返回 null，
         * 绝不截断或回绕 —— 回绕会让一笔账变成另一个数（甚至正负颠倒）。
         *
         * 返回值：解析失败（空串、非数字、超限）时为 null。
         */
        fun parseYuan(input: String): Money? {
            var trimmed = input.trim()
            if (trimmed.isEmpty()) return null

            // 微信/支付宝页面上的收入金额会带前导 `+`，这里一并容错
            if (trimmed.startsWith("+")) trimmed = trimmed.substring(1)
            if (trimmed.isEmpty()) return null

            val negative = trimmed.startsWith("-")
            val body = if (negative) trimmed.substring(1) else trimmed
            if (body.isEmpty()) return null

            val parts = body.split('.')
            if (parts.size > 2) return null

            // 整数部分允许千分位逗号；小数部分不允许（`12.3,4` 是手误，不该被当成 12.34）
            val integerPart = parts[0].replace(",", "")
            if (integerPart.isNotEmpty() && !integerPart.all { it.isDigit() }) return null

            val fractionRaw = if (parts.size == 2) parts[1] else ""
            if (fractionRaw.any { !it.isDigit() }) return null
            if (fractionRaw.length > MAX_FRACTION_DIGITS) return null

            if (integerPart.trimStart('0').length > MAX_INTEGER_DIGITS) return null

            val integerValue = if (integerPart.isEmpty()) 0L else integerPart.toLongOrNull() ?: return null
            val fractionValue = when (fractionRaw.length) {
                0 -> 0L
                1 -> fractionRaw.toLong() * 10
                else -> fractionRaw.toLong()
            }

            // 到这里 integerValue ≤ 999_999_999，乘 100 不可能溢出 Long
            val total = integerValue * 100 + fractionValue
            return Money(if (negative) -total else total)
        }
    }
}
