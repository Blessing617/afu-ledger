package com.afu.ledger.auto

/**
 * 从页面文本里猜商户名。
 *
 * 这是「尽力而为」：读不到就返回 null，绝不编一个。策略是取金额文本相邻的短文本，
 * 再按噪声词、纯数字、日期时间等规则过滤掉按钮与元信息。
 */
object MerchantExtractor {

    private const val MIN_LENGTH = 2
    private const val MAX_LENGTH = 20

    private val noise: Set<String> = setOf(
        "支付成功", "付款成功", "交易成功", "已支付", "支付完成", "付款完成",
        "完成", "确定", "返回", "关闭", "分享", "详情", "账单详情", "查看账单详情",
        "查看更多", "查看详情", "联系商家", "对此订单有疑问",
    )

    private val bannedFragments: List<String> = listOf(
        "支付", "付款", "交易单号", "商户单号", "订单号", "支付方式", "当前状态",
        "账单分类", "支付时间", "创建时间", "账单", "查看", "优惠", "退款",
        "余额", "零钱", "银行卡", "花呗", "成功", "合计",
        // 金额行本身（支付宝账单页会渲染成「支出25元」）
        "支出", "收入",
        // 订单状态文案，常与金额相邻
        "等待确认收货", "确认收货", "待收货", "已发货", "待发货",
        "已完成", "待付款", "已关闭", "已取消", "待使用", "待评价",
        // 页面区块标题
        "交易", "详情", "更多",
    )

    private val amountLike = Regex("""^[¥￥]?\s*-?\d[\d,]*(?:\.\d{1,2})?\s*元?$""")
    private val timeLike = Regex("""\d{1,2}:\d{2}""")
    private val dateLike = Regex("""\d{4}[-/年]\d{1,2}[-/月]\d{1,2}""")

    /**
     * 在金额附近找商户名。
     *
     * 只看金额前后各两条，**不做全页扫描**：全页扫描会抓到「我的消费图鉴」这类按钮文案，
     * 与其写错一个商户名，不如留空让用户自己补。
     *
     * @param texts 整页文本（已按节点顺序）
     * @param amount 已解析出的金额，用于定位金额所在行
     */
    fun extract(texts: List<String>, amount: com.afu.ledger.domain.model.Money): String? {
        val amountIndexes = texts.indices.filter { index ->
            AmountParser.parseOne(texts[index])?.abs() == amount ||
                AmountParser.parseBare(texts[index])?.abs() == amount
        }

        val candidates = buildList {
            amountIndexes.forEach { index ->
                // 由近及远：相邻一条优先，再往外一条
                listOf(-1, 1, -2, 2).forEach { offset -> add(index + offset) }
            }
        }

        return candidates.firstNotNullOfOrNull { index ->
            texts.getOrNull(index)?.trim()?.takeIf(::isMerchantLike)
        }
    }

    private fun isMerchantLike(text: String): Boolean {
        if (text.length !in MIN_LENGTH..MAX_LENGTH) return false
        if (text in noise) return false
        if (text.any { it == '¥' || it == '￥' }) return false
        if (amountLike.matches(text)) return false
        if (timeLike.containsMatchIn(text) || dateLike.containsMatchIn(text)) return false
        if (bannedFragments.any { text.contains(it) }) return false
        // 至少得有一个字母或汉字，纯符号/数字不要
        if (text.none { it.isLetter() }) return false
        return true
    }
}
