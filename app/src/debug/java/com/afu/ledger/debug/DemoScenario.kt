package com.afu.ledger.debug

/** 模拟页面场景。文案与单测样本保持一致，便于对照规则。 */
enum class DemoScenario(
    val label: String,
    val texts: List<String>,
) {
    WECHAT_SUCCESS(
        label = "微信支付成功",
        texts = listOf("支付成功", "¥25.50", "瑞幸咖啡", "完成"),
    ),
    ALIPAY_BILL(
        label = "支付宝账单详情",
        texts = listOf("账单详情", "-25.50", "地铁", "交易单号 2026101822001"),
    ),
    AMBIGUOUS(
        label = "多金额歧义",
        texts = listOf("支付成功", "¥25.50", "优惠 ¥3.00", "完成"),
    ),
    FUTURE(
        label = "未来时态扣款",
        texts = listOf("支付成功", "¥19.90", "将于明日扣款", "完成"),
    ),
    REFUND(
        label = "退款成功",
        texts = listOf("退款成功", "¥25.50", "瑞幸咖啡"),
    ),
    NON_PAYMENT(
        label = "非支付页面",
        texts = listOf("微信", "文件传输助手", "今天 12:30", "扫一扫"),
    ),
    ;

    companion object {
        fun from(value: String?): DemoScenario =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: WECHAT_SUCCESS
    }
}
