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
    MAIN_SCREEN("疑似主界面/列表页"),
    NO_AMOUNT("金额解析失败"),
    AMBIGUOUS_AMOUNT("金额歧义"),
    ZERO_AMOUNT("金额为零"),
    UNKNOWN_DIRECTION("收支方向无法判定"),

    // 截图 OCR 通道专有
    OCR_UNAVAILABLE("OCR 不可用"),
    SCREENSHOT_FAILED("截图失败"),
    OCR_TIMEOUT("OCR 超时"),
    OCR_NO_TEXT("OCR 无文本"),
    OCR_SUSPICIOUS_AMOUNT("金额格式不合规"),
}
