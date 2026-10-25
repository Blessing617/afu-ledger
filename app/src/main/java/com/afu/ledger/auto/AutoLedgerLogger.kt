package com.afu.ledger.auto

/**
 * 识别日志的写入口，包一层开关判断与长度截断。
 *
 * 日志会落盘（见 [RecognitionLog]），但**不保存整页文本** —— [detail] 会被截断，
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
        direction: String? = null,
        timeSource: String? = null,
        fingerprint: String? = null,
        pageHeadline: String? = null,
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
                direction = direction,
                timeSource = timeSource,
                fingerprint = fingerprint,
                pageHeadline = pageHeadline?.take(PAGE_HEADLINE_LIMIT),
            ),
        )
    }

    /** 记一笔「识别到了什么」，把方向、时间来源、指纹与页面摘要一起写进去。 */
    fun logDraft(draft: PaymentDraft, outcome: String, detail: String? = null) {
        log(
            packageName = draft.sourcePackage,
            outcome = outcome,
            amountCents = draft.amount.cents,
            merchant = draft.merchant,
            detail = detail,
            direction = draft.direction.name,
            timeSource = draft.occurredAtSource.name,
            fingerprint = draft.fingerprint(),
            pageHeadline = draft.pageHeadline.takeIf { it.isNotBlank() },
        )
    }

    fun logMiss(packageName: String, reason: MissReason, detail: String? = null) {
        if (reason == MissReason.UNSUPPORTED_PACKAGE) return
        log(packageName, reason.label, detail = detail)
    }

    companion object {
        const val DETAIL_LIMIT: Int = 200

        /** 页面摘要上限：与 [PaymentPageDetector] 的截断规则保持一致（3 行 / 96 字）。 */
        const val PAGE_HEADLINE_LIMIT: Int = 96
    }
}
