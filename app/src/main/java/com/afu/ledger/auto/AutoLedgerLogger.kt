package com.afu.ledger.auto

/**
 * 识别日志的写入口，包一层开关判断与长度截断。
 *
 * 日志只存在内存里，且**不保存整页文本** —— [detail] 会被截断，
 * 因为「不落盘原始页面（或截图）文本」是这个能力的隐私底线。
 */
class AutoLedgerLogger(
    private val recognitionLog: RecognitionLog,
    private val enabledProvider: () -> Boolean,
    private val detailLimit: Int = DETAIL_LIMIT,
) {

    fun log(
        packageName: String,
        outcome: String,
        amountCents: Long? = null,
        merchant: String? = null,
        detail: String? = null,
    ) {
        if (!enabledProvider()) return
        recognitionLog.record(
            RecognitionEntry(
                at = System.currentTimeMillis(),
                packageName = packageName,
                outcome = outcome,
                amountCents = amountCents,
                merchant = merchant,
                detail = detail?.take(detailLimit),
            ),
        )
    }

    fun logMiss(packageName: String, reason: MissReason, detail: String? = null) {
        if (reason == MissReason.UNSUPPORTED_PACKAGE) return
        log(packageName, reason.label, detail = detail)
    }

    companion object {
        const val DETAIL_LIMIT: Int = 200
    }
}
