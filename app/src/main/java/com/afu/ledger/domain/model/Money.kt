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

        /**
         * 从用户输入的元字符串解析，最多两位小数。
         *
         * 返回值：解析失败（空串、非数字、超过两位小数）时为 null。
         */
        fun parseYuan(input: String): Money? {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return null

            val negative = trimmed.startsWith("-")
            val body = if (negative) trimmed.substring(1) else trimmed
            if (body.isEmpty()) return null

            val parts = body.split('.')
            if (parts.size > 2) return null

            val integerPart = parts[0]
            if (integerPart.isNotEmpty() && !integerPart.all { it.isDigit() }) return null

            val fractionRaw = if (parts.size == 2) parts[1] else ""
            if (fractionRaw.any { !it.isDigit() }) return null
            if (fractionRaw.length > 2) return null

            val integerValue = if (integerPart.isEmpty()) 0L else integerPart.toLongOrNull() ?: return null
            val fractionValue = when (fractionRaw.length) {
                0 -> 0L
                1 -> fractionRaw.toLong() * 10
                else -> fractionRaw.toLong()
            }

            val total = integerValue * 100 + fractionValue
            return Money(if (negative) -total else total)
        }
    }
}
