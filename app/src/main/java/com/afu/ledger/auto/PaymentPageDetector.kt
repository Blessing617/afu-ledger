package com.afu.ledger.auto

/**
 * 判定当前页面是不是支付结果页，并提取金额与商户名。
 *
 * 纯 Kotlin，无 Android 依赖，可直接单测。
 *
 * @param debugPackageName debug 构建传自己的 applicationId，用于放行内置模拟页面；
 *   发布版传 null，此时只认微信与支付宝。
 */
class PaymentPageDetector(
    private val debugPackageName: String? = null,
) {

    private val allowDebugRules: Boolean get() = debugPackageName != null

    fun detect(snapshot: PageSnapshot): DetectionResult {
        if (!isSupportedPackage(snapshot.packageName)) {
            return DetectionResult.Missed(MissReason.UNSUPPORTED_PACKAGE)
        }

        val pageText = snapshot.texts.joinToString(separator = "\n")
        if (PaymentPageRules.excludedKeywords.any { pageText.contains(it) }) {
            return DetectionResult.Missed(MissReason.EXCLUDED_KEYWORD)
        }

        val rule = PaymentPageRules.rules.firstOrNull { candidate ->
            (!candidate.debugOnly || allowDebugRules) &&
                candidate.requiredKeywords.any { pageText.contains(it) }
        } ?: return DetectionResult.Missed(MissReason.NOT_PAYMENT_PAGE)

        val amounts = AmountParser.parseAll(
            texts = snapshot.texts,
            allowBareAmount = rule.type == PaymentPageType.BILL_DETAIL,
        )
        return when {
            amounts.isEmpty() -> DetectionResult.Missed(MissReason.NO_AMOUNT)
            amounts.size > 1 -> DetectionResult.Missed(MissReason.AMBIGUOUS_AMOUNT)
            !amounts.single().isPositive() -> DetectionResult.Missed(MissReason.ZERO_AMOUNT)
            else -> {
                val amount = amounts.single()
                DetectionResult.Hit(
                    PaymentDraft(
                        amount = amount,
                        merchant = MerchantExtractor.extract(snapshot.texts, amount),
                        sourcePackage = snapshot.packageName,
                        pageType = rule.type,
                        capturedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    private fun isSupportedPackage(packageName: String): Boolean =
        packageName in PaymentPageRules.supportedPackages ||
            (allowDebugRules && packageName == debugPackageName)
}
