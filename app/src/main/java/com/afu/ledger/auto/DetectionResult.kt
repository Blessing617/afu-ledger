package com.afu.ledger.auto

/** 识别结果：命中一条支付草稿，或说明为什么没命中。 */
sealed interface DetectionResult {

    data class Hit(val draft: PaymentDraft) : DetectionResult

    data class Missed(val reason: MissReason) : DetectionResult
}

/** 未命中的原因，写入识别日志方便排查规则失效。 */
enum class MissReason(val label: String) {
    UNSUPPORTED_PACKAGE("非受支持应用"),
    NOT_PAYMENT_PAGE("不是支付页面"),
    EXCLUDED_KEYWORD("命中排除词"),
    NO_AMOUNT("金额解析失败"),
    AMBIGUOUS_AMOUNT("金额歧义"),
    ZERO_AMOUNT("金额为零"),
}
