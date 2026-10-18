package com.afu.ledger.auto

/**
 * 从账单页文本里取「交易单号」。
 *
 * 交易单号是**同一张账单最稳定的身份**：金额、商户名都可能被 OCR 读错，
 * 而单号一旦取到，同一张账单无论打开多少次都是同一个值。
 * 去重用它做指纹，才能做到「忽略过就真的不再弹」。
 *
 * 只从标签附近取，不做全页盲搜：账单页里还有商品编号、商户单号等长数字，
 * 盲搜很容易取错，而**取错单号的后果是去重失效**（宁可返回 null）。
 */
object TradeNoExtractor {

    private val labels: List<String> = listOf(
        "交易单号",
        "商户单号",
        "商戶单号",
        "商家订单号",
        "订单号",
    )

    /** 纯数字串，至少 12 位 —— 短于这个长度的数字基本都是日期或序号。 */
    private val digits = Regex("""\d{12,}""")

    /** 兜底接受的最小长度：比交易单号短的数字串多半是时间戳或商品编号。 */
    private const val FALLBACK_MIN_LENGTH = 16

    fun extract(texts: List<String>): String? {
        texts.forEachIndexed { index, line ->
            if (labels.none { line.contains(it) }) return@forEachIndexed
            val compact = line.replace(" ", "")
            digits.find(compact)?.let { return it.value }
            // 标签与数值常被 OCR 拆成两行：微信账单页就是「交易单号」+ 下一行 28 位数字
            for (offset in 1..2) {
                val next = texts.getOrNull(index + offset)?.replace(" ", "") ?: continue
                digits.find(next)?.let { return it.value }
            }
        }

        // 兜底：整页只有一个长数字串时它只可能是单号。
        val all = texts
            .asSequence()
            .map { it.replace(" ", "") }
            .flatMap { cleaned -> digits.findAll(cleaned).map { it.value } }
            .distinct()
            .toList()
        return all.singleOrNull()?.takeIf { it.length >= FALLBACK_MIN_LENGTH }
    }
}
