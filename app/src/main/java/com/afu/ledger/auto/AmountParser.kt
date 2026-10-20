package com.afu.ledger.auto

import com.afu.ledger.domain.model.Money
import com.afu.ledger.domain.model.TransType

/**
 * 从页面文本里提取金额**和它的收支方向**。
 *
 * ## 方向为什么必须一起解析
 *
 * 真机实测：支付宝账单页的无障碍文本把金额口语化成 `支出25元`、`支出29.74元` ——
 * 方向就在「支出」两个字里；微信红包收入页则是 `+128.50`。
 * 旧实现把所有结果统一 `.abs()`，`RecordDetectedPaymentUseCase` 又硬编码成支出，
 * 于是入账账单必然被记成支出，红包收入干脆因为不认 `+` 号而漏记。
 *
 * ## 优先级（从高到低）
 *
 * | 级别 | 形态 | 例子 | 说明 |
 * |---|---|---|---|
 * | 3 | 带方向前缀 | `支出17499.78元`、`收入128.50元` | 语义最强，同时给了金额和方向 |
 * | 2 | 整行带符号金额 | `-23.90`、`+128.50` | 符号即方向 |
 * | 1 | 带货币符号/元的金额 | `¥25.50`、`100元` | 方向可能缺失 |
 * | 0 | 裸数字（仅账单页启用） | `17500.00` | 最容易是原价/优惠，最弱 |
 *
 * 取「最高级别的那一组」：组内只出现一个金额就采纳，出现多个不同金额才判歧义。
 * 这样 `订单金额 17500.00` 与 `立减金 -0.22` 不会再把 `支出17499.78元` 拖进歧义 ——
 * 它们级别更低，直接落选。
 */
object AmountParser {

    private val symbolPattern = Regex("""[¥￥]\s*([+-]?\d[\d,]*(?:\.\d{1,3})?)""")
    private val yuanPattern = Regex("""([+-]?\d[\d,]*(?:\.\d{1,3})?)\s*元""")

    /**
     * 裸金额：整条文本就是一个金额。
     *
     * 三个约束都来自真机踩坑：
     * - **必须带两位小数**：不带的话交易单号（28 位纯数字）会被当成金额；
     * - **接受前导 `+`**：微信红包收入页的金额就是 `+128.50`，旧版本只认 `-`，
     *   导致这类账单永远解析不出金额；
     * - **整数部分不超过 7 位**：挡住「订单号被读成整数」这类长数字。
     */
    private val bareAmountPattern = Regex("""^[+-]?(?:\d{1,3}(?:,\d{3})+|\d{1,7})\.\d{2}$""")

    /** 方向前缀。取值必须指向「这一笔收/支」，不能是页面上的其它词。 */
    private val expensePrefixes = listOf("支出", "付款", "消费")
    private val incomePrefixes = listOf("收入", "入账", "收款", "退款")

    /** 带方向前缀的金额，如 `支出17499.78元`、`收入 128.50元`。 */
    private val prefixedAmount = Regex(
        """(支出|付款|消费|收入|入账|收款|退款)\s*[¥￥]?\s*([+-]?\d[\d,]*(?:\.\d{1,2})?)\s*元?""",
    )

    /** 金额候选。 */
    private data class Candidate(
        val amount: Money,
        val direction: TransType?,
        val line: String,
        val priority: Int,
    )

    /** 解析结果。 */
    sealed interface Result {
        /** 唯一金额。方向可能为 null（需要调用方用状态词兜底）。 */
        data class Ok(val amount: Money, val direction: TransType?, val line: String) : Result

        /** 没有任何可解析的金额。 */
        data object None : Result

        /** 同一优先级上出现多个互不相同的金额。 */
        data class Ambiguous(val amounts: List<Money>) : Result
    }

    /**
     * 解析整页的**交易金额**。
     *
     * @param lines 整页文本行（视觉行拼合后的），按页面上到下排序
     * @param allowBareAmount 是否接受不带货币符号的裸数字（仅账单详情页开启）
     */
    fun parseTransaction(
        lines: List<String>,
        allowBareAmount: Boolean = false,
    ): Result {
        val candidates = mutableListOf<Candidate>()

        lines.forEachIndexed { index, rawLine ->
            val line = rawLine.replace('\u00A0', ' ').trim()
            if (line.isEmpty()) return@forEachIndexed

            // 同一个视觉行里可能挤着多段内容。真机实测微信红包页：
            // OCR 把「微信红包-来自群友-江浩(DJ)」和「+50.00」合进了一行，
            // 只按整行匹配就永远拿不到金额（整行既不是「支出X元」也不是裸金额）→ miss NO_AMOUNT。
            // 所以整行之外，再按空白切开逐段尝试。
            val pieces = buildList {
                add(line)
                if (line.any { it == ' ' || it == '\u3000' }) {
                    line.split(' ', '\u3000').forEach { piece ->
                        val trimmed = piece.trim()
                        if (trimmed.isNotEmpty() && trimmed != line) add(trimmed)
                    }
                }
            }

            val excluded = isNonTransactionContext(lines, index)

            for (piece in pieces) {
                // 级别 3：带方向前缀，一次拿到金额与方向
                prefixedAmount.find(piece)?.let { match ->
                    val prefix = match.groupValues[1]
                    val value = toMoney(match.groupValues[2]) ?: return@let
                    val direction = when {
                        expensePrefixes.any { prefix.startsWith(it) } -> TransType.EXPENSE
                        incomePrefixes.any { prefix.startsWith(it) } -> TransType.INCOME
                        else -> null
                    }
                    candidates += Candidate(value, direction, piece, PRIORITY_PREFIXED)
                    return@forEachIndexed
                }
            }

            if (excluded) return@forEachIndexed

            for (piece in pieces) {
                val amount = (parseOne(piece) ?: if (allowBareAmount) parseBare(piece) else null)
                    ?: continue

                val priority = when {
                    isBareNumber(piece) && hasSign(piece) -> PRIORITY_SIGNED
                    isBareNumber(piece) -> PRIORITY_BARE
                    else -> PRIORITY_SYMBOL
                }
                candidates += Candidate(amount, signDirection(piece), piece, priority)
            }
        }

        if (candidates.isEmpty()) return Result.None

        val best = candidates.maxOf { it.priority }
        val group = candidates.filter { it.priority == best }
        val distinct = group.map { it.amount }.distinct()

        return when {
            distinct.size > 1 -> Result.Ambiguous(distinct)
            else -> {
                val winner = group.first { it.amount == distinct.single() }
                Result.Ok(winner.amount, winner.direction, winner.line)
            }
        }
    }

    /** 解析单条文本，取第一条命中的金额（不含方向）。 */
    fun parseOne(text: String): Money? {
        val match = prefixedAmount.find(text)
            ?: symbolPattern.find(text)
            ?: yuanPattern.find(text)
            ?: return null
        val group = when {
            match.groups.size > 2 -> match.groupValues[2]
            else -> match.groupValues[1]
        }
        return toMoney(group)
    }

    /** 解析整条即为金额的文本（如账单页的 `-25.50`、`+128.50`）。 */
    fun parseBare(text: String): Money? {
        val trimmed = text.trim()
        if (!bareAmountPattern.matches(trimmed)) return null
        return toMoney(trimmed)
    }

    /** 兼容旧调用：整页金额集合（取绝对值）。 */
    fun parseAll(texts: List<String>, allowBareAmount: Boolean = false): List<Money> =
        texts.mapNotNull { parseOne(it) ?: if (allowBareAmount) parseBare(it) else null }
            .map { it.abs() }
            .distinct()

    /**
     * 兼容旧调用：排除明显不是交易金额的行后，返回去重金额集合。
     *
     * 新代码请用 [parseTransaction]，它还会带上方向并按优先级定夺。
     */
    fun parseTransactionAmounts(
        texts: List<String>,
        allowBareAmount: Boolean = false,
    ): List<Money> {
        val result = mutableListOf<Money>()
        texts.forEachIndexed { index, text ->
            val amount = parseOne(text) ?: (if (allowBareAmount) parseBare(text) else null)
                ?: return@forEachIndexed
            if (isNonTransactionContext(texts, index)) return@forEachIndexed
            result.add(amount.abs())
        }
        return result.distinct()
    }

    /**
     * 这一行的金额是否明显「不是本次交易金额」。
     *
     * 判据分两类、用不同范围，这是真机踩出来的：
     *
     * - **看本行**：余额类、价格标签类、外币单位类。这一行自己写着「订单金额」「6.24美元」，
     *   它当然不是本次实付。
     * - **看邻行**：只查余额类与状态栏单位。**不能查价格标签** ——
     *   微信外币支付页的排版是「-42.00 / 6.24美元 / 标价 / 汇率」，
     *   真实金额下面两行就是「标价」，按邻行查会把真金额排掉，
     *   实测表现是整页 `NO_AMOUNT`、外币账单永不记账。
     *
     * 支付宝的价格标签则排在**值的前面**（「订单金额」→ 下一行「17500.00」），
     * 所以单独处理「上一行是价格标签 → 本行是它的值」。
     */
    private fun isNonTransactionContext(texts: List<String>, index: Int): Boolean {
        val line = texts[index]
        if (balanceKeywords.any { line.contains(it) }) return true
        if (priceLabels.any { line.contains(it) }) return true
        if (foreignCurrencyKeywords.any { line.contains(it) }) return true

        // 上一行是价格标签 → 本行是它的值（支付宝的排布）
        val previous = texts.getOrNull(index - 1)
        if (previous != null && priceLabels.any { previous.contains(it) }) return true

        // 看前后各两行：状态栏被 OCR 切成三行时，单位会隔一行才出现。
        val neighbors = buildList {
            for (offset in listOf(-1, 1, -2, 2)) {
                texts.getOrNull(index + offset)?.let { add(it) }
            }
        }
        if (neighbors.any(::isBalanceLabel)) return true
        if (neighbors.any { line -> unitKeywords.any { line.contains(it, ignoreCase = true) } }) {
            return true
        }
        return false
    }

    /**
     * 这一行是不是「余额」标签。
     *
     * 注意要先把「余额宝」剔掉：它是支付宝的一种**支付方式**，不是账户余额。
     */
    private fun isBalanceLabel(line: String): Boolean {
        val stripped = line.replace("余额宝", "")
        return balanceKeywords.any { stripped.contains(it) }
    }

    private fun hasSign(line: String): Boolean {
        val t = line.trim()
        return t.startsWith("+") || t.startsWith("-")
    }

    private fun signDirection(line: String): TransType? = when {
        line.trim().startsWith("+") -> TransType.INCOME
        line.trim().startsWith("-") -> TransType.EXPENSE
        else -> null
    }

    private fun isBareNumber(line: String): Boolean = bareAmountPattern.matches(line.trim())

    private fun toMoney(raw: String): Money? {
        // 去掉前导 `+`：`Money.parseYuan` 只认 `-`，而微信红包/转账收入页的金额
        // 就是 `+128.50`、`+50.00`。方向由 signDirection 单独判定，这里只要数值。
        val cleaned = raw.replace(",", "").replace(" ", "").trim().removePrefix("+")
        return Money.parseYuan(cleaned)?.abs()
    }

    /** 级别：带方向前缀（最高）。 */
    private const val PRIORITY_PREFIXED = 3

    /** 级别：整行带正负号。 */
    private const val PRIORITY_SIGNED = 2

    /** 级别：不带符号的裸数字。 */
    private const val PRIORITY_BARE = 0

    /** 级别：带货币符号或「元」。 */
    private const val PRIORITY_SYMBOL = 1

    /** 余额类标签：它们邻接的金额是账户余额，不是本次支出。 */
    private val balanceKeywords: List<String> = listOf(
        "余额", "余額", "可用余额", "账户余额", "零钱余额",
    )

    /**
     * 价格类**标签**：这一行（或它下面一行）的数字是原价、优惠，不是本次实付金额。
     *
     * 实测：支付宝扫经营码付款页同时有 `订单金额 17500.00` 与 `工商银行立减金 -0.22`。
     */
    private val priceLabels: List<String> = listOf(
        "订单金额", "商品金额", "原价", "标价", "优惠", "立减", "已省",
        "汇率", "红包金额",
    )

    /**
     * 外币单位：含这些词的那一行本身就是外币标价，不是人民币实付。
     *
     * 只在本行生效 —— 微信外币支付页的「6.24美元」「标价」「汇率」紧贴在真实金额下面，
     * 按邻行排除会把真实金额一起排掉。
     */
    private val foreignCurrencyKeywords: List<String> = listOf(
        "美元", "USD", "日元", "JPY", "欧元", "EUR", "英镑", "GBP", "港币", "HKD",
    )

    /** 单位类词：邻接这些词的数字多半是状态栏读数。 */
    private val unitKeywords: List<String> = listOf(
        "KB/S", "KB/s", "M/S", "℃", "°C",
    )
}
