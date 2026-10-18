package com.afu.ledger.auto

import com.afu.ledger.domain.model.Money

/** 识别到的页面类型。仅用于日志与调试，不改变入账行为。 */
enum class PaymentPageType {
    /** 支付成功结果页。 */
    PAYMENT_RESULT,

    /** 账单详情页。只在 debug 构建启用，用于不花钱验证真实页面。 */
    BILL_DETAIL,
}

/** 一次成功识别出的支付草稿。确认前不会写入数据库。 */
data class PaymentDraft(
    val amount: Money,
    val merchant: String?,
    val sourcePackage: String,
    val pageType: PaymentPageType,
    val capturedAt: Long,
)
