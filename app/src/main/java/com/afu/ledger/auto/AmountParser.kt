package com.afu.ledger.auto

import com.afu.ledger.domain.model.Money

/**
 * 从页面文本里提取金额。
 *
 * 只认带货币符号或「元」结尾的数字，避免把订单号、时间、验证码当成金额；
 * 清洗掉千分位逗号与空格后交给 [Money.parseYuan]，保证与手动记账同一套解析规则。
 */
object AmountParser {

    private val symbolPattern = Regex("""[¥￥]\s*(-?\d[\d,]*(?:\.\d{1,2})?)""")
    private val yuanPattern = Regex("""(-?\d[\d,]*(?:\.\d{1,2})?)\s*元""")

    /**
     * 裸金额：整条文本就是一个带两位小数的数字。
     *
     * 账单详情页的金额常常是「-25.50」，不带货币符号。只接受「整条且两位小数」，
     * 这样单号（长整数）、时间（12:30）、日期都不会被误认。
     */
    private val bareAmountPattern = Regex("""^-?(?:\d{1,3}(?:,\d{3})*|\d+)\.\d{2}$""")

    /** 解析单条文本，取第一条命中的金额。 */
    fun parseOne(text: String): Money? {
        val match = symbolPattern.find(text) ?: yuanPattern.find(text) ?: return null
        // groupValues[0] 是整段匹配（可能带 ¥），金额在捕获组 1
        val raw = match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        return toMoney(raw)
    }

    /**
     * 解析整页文本，返回去重后的金额集合（取绝对值）。
     *
     * 调用方据此判断是否存在歧义：超过一个互不相同的金额时不猜，直接放弃。
     *
     * @param allowBareAmount 是否接受不带货币符号的裸金额（仅账单详情页规则开启）
     */
    fun parseAll(texts: List<String>, allowBareAmount: Boolean = false): List<Money> =
        texts.mapNotNull { text ->
            parseOne(text) ?: if (allowBareAmount) parseBare(text) else null
        }.map { it.abs() }.distinct()

    /** 解析整条即为金额的文本（如账单页的「-25.50」）。 */
    fun parseBare(text: String): Money? {
        val trimmed = text.trim()
        if (!bareAmountPattern.matches(trimmed)) return null
        return toMoney(trimmed)
    }

    private fun toMoney(raw: String): Money? {
        val cleaned = raw.replace(",", "").replace(" ", "").trim()
        return Money.parseYuan(cleaned)?.abs()
    }
}
