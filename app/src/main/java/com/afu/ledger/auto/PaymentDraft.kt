package com.afu.ledger.auto

import com.afu.ledger.domain.model.Money
import com.afu.ledger.domain.model.TransType
import java.time.LocalDateTime
import java.time.ZoneId

/** 识别到的页面类型。仅用于日志与调试，不改变入账行为。 */
enum class PaymentPageType {
    /** 支付成功结果页。本期已停用，保留类型供后续变更重新启用。 */
    PAYMENT_RESULT,

    /** 账单详情页 —— 当前唯一会入账的识别目标。 */
    BILL_DETAIL,
}

/** 账单时间的来源。 */
enum class TimeSource {
    /** 从页面上的时间字段解析得到。 */
    PAGE,

    /** 页面上取不到，回退成识别时刻。日志里必须标注。 */
    FALLBACK,
}

/**
 * 一次成功识别出的账单草稿。确认前不会写入数据库。
 *
 * @param amount 金额的绝对值
 * @param direction 收支方向。**不再默认支出** —— 入账账单必须能记成收入
 * @param occurredAt 账单上这笔交易真实发生的时间，作为入账交易的 `occurredAt`
 * @param occurredAtSource 时间是从页面读的还是回退的，写进日志供排查
 * @param pageCategory 页面自带的分类文案（如支付宝「餐饮美食」），用于映射账本分类
 */
data class PaymentDraft(
    val amount: Money,
    val direction: TransType,
    val merchant: String?,
    val sourcePackage: String,
    val pageType: PaymentPageType,
    val occurredAt: Long,
    val occurredAtSource: TimeSource,
    val capturedAt: Long,
    /** 账单交易单号；取不到时为 null，去重退化为「金额 + 方向 + 商户 + 账单时间」。 */
    val tradeNo: String? = null,
    /** 页面自带的分类文案；映射不到账本分类时由调用方回退。 */
    val pageCategory: String? = null,
    /** 命中为交易金额的那一行原文，只用于日志排查（不落库）。 */
    val amountLine: String = "",
    /** 来源应用的人话名字（微信 / 支付宝）。 */
    val source: String = PaymentPageRules.sourceLabel(sourcePackage),
) {

    /**
     * 同一张账单的身份指纹。
     *
     * 有交易单号时**只认单号**：金额与商户名由 OCR 现猜，同一张账单重开一次就可能变，
     * 用它们做指纹会让「忽略」形同虚设。
     *
     * 没有单号时退化为「包名 + 金额 + 方向 + 商户 + 账单时间（到分钟）」。
     * 时间必须用**账单时间**而不是识别时刻 —— 支付宝旧版布局不渲染交易单号，
     * 退化指纹是这个场景的常态，而账单时间才能区分「同一商户同金额的两笔」。
     *
     * **商户名不参与指纹。** 它是从页面文本猜出来的，同一张账单两次 OCR
     * 可能读成「阿佑鲜烫牛內米线」和「阿佑鮮烫牛内米线」（简繁/形近字差异），
     * 带进指纹就会变成两个不同指纹 → 同一张账单反复弹窗。
     * 「包名 + 金额 + 方向 + 账单时间（到分钟）」已经足够唯一。
     */
    fun fingerprint(): String {
        val no = tradeNo?.takeIf { it.isNotBlank() }
        if (no != null) return "$sourcePackage|$no"

        val minute = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(occurredAt),
            ZoneId.systemDefault(),
        ).let { "%04d%02d%02d%02d%02d".format(it.year, it.monthValue, it.dayOfMonth, it.hour, it.minute) }
        return "$sourcePackage|${amount.cents}|${direction.name}|$minute"
    }
}
