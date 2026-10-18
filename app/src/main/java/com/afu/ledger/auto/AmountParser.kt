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

    /**
     * 解析整页金额，**先排除明显不是「这笔交易金额」的行**，再返回去重后的集合。
     *
     * ## 为什么需要这一步
     *
     * 真实支付宝「余额明细详情」页实测的 33 行 OCR 结果里有三个像金额的东西：
     *
     * ```
     * 5.24          ← 状态栏网速（单位 KB/S 被 OCR 切到了相邻行）
     * -25.00        ← 真正的交易金额
     * ¥217.01       ← 账户余额（上一行是「余额」）
     * ```
     *
     * 三个都被解析出来后，`parseAll` 报 `AMBIGUOUS_AMOUNT`，整页放弃 —— 结果就是
     * 「页面明明有金额，却永远不记账」。这不是保守，这是失效。
     *
     * 处理方式是**只做排除，不做挑选**：把上下文明确表明「这不是交易金额」的行剔掉，
     * 剩下的如果仍有多个不同金额，依然按 `AMBIGUOUS_AMOUNT` 放弃。
     * 这样既解决真实误报，又守住「宁可漏记也不记错」的底线。
     *
     * 排除的上下文：
     * - 余额类：本行或相邻行出现「余额 / 可用余额 / 账户余额」（那是对账余额，不是支出）
     * - 单位类：相邻行出现 `KB/S`、`℃`（状态栏读数）
     *
     * @param allowBareAmount 是否接受不带货币符号的裸金额（仅账单详情页规则开启）
     */
    fun parseTransactionAmounts(
        texts: List<String>,
        allowBareAmount: Boolean = false,
    ): List<Money> {
        val candidates = mutableListOf<Money>()
        texts.forEachIndexed { index, text ->
            val amount = parseOne(text) ?: (if (allowBareAmount) parseBare(text) else null)
                ?: return@forEachIndexed

            if (isNonTransactionContext(texts, index)) return@forEachIndexed
            candidates.add(amount.abs())
        }
        return candidates.distinct()
    }

    /** 这一行的金额是否明显「不是本次交易金额」。 */
    private fun isNonTransactionContext(texts: List<String>, index: Int): Boolean {
        // 同一行直接带余额字样
        if (balanceKeywords.any { texts[index].contains(it) }) return true

        // 看前后各两行。
        //
        // 只看 ±1 不够：真实微信账单页的状态栏被 OCR 切成三行
        // （`0.11` / `0:07` / `KB/s`），网速值的相邻行是时间，
        // 单位在隔一行的地方，只查 ±1 会把 `0.11` 当成交易金额，
        // 和真正的 `-26.80` 一起构成「金额歧义」，整页放弃。
        val neighbors = buildList {
            for (offset in listOf(-1, 1, -2, 2)) {
                texts.getOrNull(index + offset)?.let { add(it) }
            }
        }

        // 相邻行是「余额」类标签，说明这一行是余额数值
        if (neighbors.any(::isBalanceLabel)) return true

        // 相邻行是单位，说明这一行是状态栏读数
        if (neighbors.any { line -> unitKeywords.any { line.contains(it, ignoreCase = true) } }) {
            return true
        }
        return false
    }

    /**
     * 这一行是不是「余额」标签。
     *
     * 注意要先把「余额宝」剔掉：它是支付宝的一种**支付方式**，不是账户余额。
     * 真实付款成功页常有「支付方式 余额宝」这一行，如果直接按「含余额」判断，
     * 就会把紧邻的金额（其实就是本次支付金额）当成余额而丢弃 —— 实测踩过，
     * 表现为「付款成功页反而识别不到金额」。
     */
    private fun isBalanceLabel(line: String): Boolean {
        val stripped = line.replace("余额宝", "")
        return balanceKeywords.any { stripped.contains(it) }
    }

    /** 余额类标签：它们邻接的金额是账户余额，不是本次支出。 */
    private val balanceKeywords: List<String> = listOf(
        "余额", "余額", "可用余额", "账户余额", "零钱余额",
    )

    /** 单位类词：邻接这些词的数字多半是状态栏读数。 */
    private val unitKeywords: List<String> = listOf(
        "KB/S", "KB/s", "M/S", "℃", "°C",
    )

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
