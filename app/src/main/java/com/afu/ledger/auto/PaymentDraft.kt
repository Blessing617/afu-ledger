package com.afu.ledger.auto

import com.afu.ledger.domain.model.Money

/** 识别到的页面类型。仅用于日志与调试，不改变入账行为。 */
enum class PaymentPageType {
    /** 支付成功结果页。 */
    PAYMENT_RESULT,

    /** 账单详情页 —— 当前的主力识别目标。 */
    BILL_DETAIL,
}

/** 一次成功识别出的支付草稿。确认前不会写入数据库。 */
data class PaymentDraft(
    val amount: Money,
    val merchant: String?,
    val sourcePackage: String,
    val pageType: PaymentPageType,
    val capturedAt: Long,
    /** 账单交易单号；取不到时为 null，去重退化为金额 + 商户 + 日期。 */
    val tradeNo: String? = null,
    /** 来源应用的人话名字（微信 / 支付宝）。 */
    val source: String = PaymentPageRules.sourceLabel(sourcePackage),
) {

    /**
     * 同一张账单的身份指纹。
     *
     * 有交易单号时**只认单号**：金额与商户名由 OCR 现猜，同一张账单重开一次就可能变，
     * 用它们做指纹会让「忽略」形同虚设。没有单号时才退化为金额 + 商户 + 日期。
     */
    fun fingerprint(): String {
        val no = tradeNo?.takeIf { it.isNotBlank() }
        if (no != null) return "$sourcePackage|$no"

        val day = java.util.Calendar.getInstance().apply { timeInMillis = capturedAt }
        val date = "%04d%02d%02d".format(
            day.get(java.util.Calendar.YEAR),
            day.get(java.util.Calendar.MONTH) + 1,
            day.get(java.util.Calendar.DAY_OF_MONTH),
        )
        val merchant = merchant?.trim()?.lowercase().orEmpty()
        return "$sourcePackage|${amount.cents}|$merchant|$date"
    }
}
