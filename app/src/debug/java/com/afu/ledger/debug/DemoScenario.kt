package com.afu.ledger.debug

/**
 * 模拟页面场景。文案与单测样本保持一致，便于对照规则。
 *
 * [canvasRendered] 为 true 的场景用 Canvas 绘制文字，**不产生无障碍文本节点** ——
 * 这正是微信的自绘行为，也是验证截图 OCR 通道的前提。
 * 为 false 的场景用 Compose Text 渲染，节点可读，用来验证无障碍通道。
 */
enum class DemoScenario(
    val label: String,
    val texts: List<String>,
    val canvasRendered: Boolean = false,
    val darkBackground: Boolean = false,
) {
    WECHAT_SUCCESS(
        label = "微信支付成功",
        texts = listOf("支付成功", "¥25.50", "瑞幸咖啡", "完成"),
    ),
    /** 没有「当前状态 / 支付方式」强字段的微信支付成功页（10-22 修复的正是这一类）。 */
    WECHAT_SUCCESS_MINIMAL(
        label = "微信支付成功（极简，无强字段）",
        texts = listOf("支付成功", "瑞幸咖啡", "¥25.50", "完成"),
        canvasRendered = true,
    ),
    /** 生活缴费版：主金额带空格，另有原价与优惠两个金额。 */
    WECHAT_SUCCESS_DISCOUNT(
        label = "微信支付成功（原价 + 优惠）",
        texts = listOf(
            "支付成功", "上海一网通办缴费平台", "¥ 98.00",
            "原价 ¥100.00", "优惠 河南农信储蓄卡¥2.00", "完成",
        ),
        canvasRendered = true,
    ),
    /** 红包领取完成页：无单号无时间，靠「红包 + 已存入零钱」判为收入。 */
    WECHAT_RED_PACKET(
        label = "微信红包（已存入零钱）",
        texts = listOf("微信红包", "¥128.50", "已存入零钱", "完成"),
        canvasRendered = true,
    ),
    /** 转账转出完成页：无单号无时间，靠「转账成功」判为支出。 */
    WECHAT_TRANSFER_OUT(
        label = "微信转账成功",
        texts = listOf("转账", "¥100.00", "转账成功", "已存入对方零钱", "完成"),
        canvasRendered = true,
    ),
    /** 负样本：聊天里别人发的支付成功截图，不该弹窗。 */
    WECHAT_CHAT_SHOT(
        label = "聊天页里的支付成功截图（应不识别）",
        texts = listOf("群友", "支付成功", "¥25.50", "看看这个截图", "按住 说话", "表情"),
        canvasRendered = true,
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

    // ---- 以下场景用 Canvas 自绘，专门验证截图 OCR 通道 ----

    WECHAT_OCR_FULL(
        label = "微信支付成功（自绘，仅 OCR 可见）",
        texts = listOf("微信支付", "支付成功", "¥25.50", "瑞幸咖啡", "完成"),
        canvasRendered = true,
    ),
    OCR_NOISE_O(
        label = "OCR 噪声：-42.OO",
        texts = listOf("账单详情", "-42.OO", "地铁乘车码", "交易单号 2026101822001"),
        canvasRendered = true,
    ),
    OCR_NOISE_BAD(
        label = "OCR 噪声：¥4Z.5O（应放弃）",
        texts = listOf("账单详情", "¥4Z.5O", "地铁乘车码", "交易单号 2026101822001"),
        canvasRendered = true,
    ),
    OCR_LOW_CONTRAST(
        label = "低对比度自绘页",
        texts = listOf("支付成功", "¥19.90", "便利店"),
        canvasRendered = true,
        darkBackground = true,
    ),
    OCR_CHAT(
        label = "聊天页（含金额但非支付页）",
        texts = listOf("微信", "文件传输助手", "今天 12:30", "¥25.50", "在吗"),
        canvasRendered = true,
    ),
    ;

    companion object {
        fun from(value: String?): DemoScenario =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: WECHAT_SUCCESS
    }
}
