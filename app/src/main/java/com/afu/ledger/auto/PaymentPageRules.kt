package com.afu.ledger.auto

/**
 * 支付页面识别规则。
 *
 * 全部集中在这里：微信/支付宝改版时只改本文件，不散落到 Service 或 UI。
 * 用「文本特征」而不是 resource-id —— 两家应用的控件 id 不对外稳定。
 */
object PaymentPageRules {

    const val WECHAT_PACKAGE = "com.tencent.mm"
    const val ALIPAY_PACKAGE = "com.eg.android.AlipayGphone"

    val supportedPackages: Set<String> = setOf(WECHAT_PACKAGE, ALIPAY_PACKAGE)

    /** 支付结果页特征词。 */
    val resultKeywords: List<String> = listOf(
        "支付成功",
        "付款成功",
        "交易成功",
        "已支付",
        "支付完成",
        "付款完成",
    )

    /** 账单详情页特征词（debug 专用）。 */
    val billKeywords: List<String> = listOf(
        "账单详情",
        "交易单号",
        "商户单号",
        "账单分类",
    )

    /**
     * 命中任意一个就放弃识别。
     *
     * 「退款」单独出现也算排除：退款成功页、已退款详情都不是新支出。
     */
    val excludedKeywords: List<String> = listOf(
        "待支付",
        "待付款",
        "确认付款",
        "立即支付",
        "去支付",
        "将于",
        "预计扣款",
        "退款",
        "交易关闭",
        "已关闭",
        "已取消",
        "收款到账",
        "已收款",
    )

    /** 规则按顺序匹配，顺序即优先级。 */
    val rules: List<PageRule> = listOf(
        PageRule(PaymentPageType.PAYMENT_RESULT, resultKeywords),
        PageRule(PaymentPageType.BILL_DETAIL, billKeywords, debugOnly = true),
    )
}

/** 单条页面规则。 */
data class PageRule(
    val type: PaymentPageType,
    val requiredKeywords: List<String>,
    /** 只在 debug 构建启用，发布版不加载。 */
    val debugOnly: Boolean = false,
)
