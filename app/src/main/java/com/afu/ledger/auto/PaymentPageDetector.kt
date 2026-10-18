package com.afu.ledger.auto

/**
 * 判定当前页面是不是**账单详情页 / 支付结果页**，并提取金额、商户名与交易单号。
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

        val texts = snapshot.texts
        val pageText = texts.joinToString(separator = "\n")

        // ① 排除：明确指向「另一笔交易」的状态词
        if (PaymentPageRules.excludedKeywords.any { pageText.contains(it) }) {
            return DetectionResult.Missed(MissReason.EXCLUDED_KEYWORD)
        }

        // ② 排除：列表页。词表 + 「多行日期时间」双判据。
        //    列表页上每一笔交易都带金额与日期，只要放行必然记错账。
        if (isListPage(texts, pageText)) {
            return DetectionResult.Missed(MissReason.MAIN_SCREEN)
        }

        // ③ 排除：主界面 / 我的页。这些页面的「最近消息」「入口九宫格」会混进历史通知，
        //    真机上表现为「停在支付宝我的页却弹出 ¥25.00，商户名是广告文案」。
        val homeHits = PaymentPageRules.homeOrMyKeywords.count { pageText.contains(it) }
        if (homeHits >= PaymentPageRules.MIN_HOME_SCREEN_HITS) {
            return DetectionResult.Missed(MissReason.MAIN_SCREEN)
        }

        // ④ 强证据：必须是账单详情页，或明确的支付结果页
        val pageType = classifyPage(pageText)
            ?: return DetectionResult.Missed(MissReason.NOT_PAYMENT_PAGE)

        // ⑤ 金额唯一才命中 —— 本项目的安全底线：宁可漏记也不记错。
        val amounts = AmountParser.parseTransactionAmounts(
            texts = texts,
            allowBareAmount = pageType == PaymentPageType.BILL_DETAIL,
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
                        merchant = MerchantExtractor.extract(texts, amount),
                        tradeNo = TradeNoExtractor.extract(texts),
                        source = PaymentPageRules.sourceLabel(snapshot.packageName),
                        sourcePackage = snapshot.packageName,
                        pageType = pageType,
                        capturedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    /**
     * 列表页判定。
     *
     * 两个信号任一成立即算列表页：
     * - 列表页专属文案命中 ≥ [PaymentPageRules.MIN_LIST_PAGE_HITS] 个
     * - 页面里出现 ≥ [PaymentPageRules.LIST_PAGE_MIN_TIMESTAMP_LINES] 行「日期 + 时间」
     */
    private fun isListPage(texts: List<String>, pageText: String): Boolean {
        val keywordHits = PaymentPageRules.listPageKeywords.count { pageText.contains(it) }
        if (keywordHits >= PaymentPageRules.MIN_LIST_PAGE_HITS) return true

        val timestampLines = texts.count { PaymentPageRules.timestampLinePattern.containsMatchIn(it) }
        return timestampLines >= PaymentPageRules.LIST_PAGE_MIN_TIMESTAMP_LINES
    }

    /**
     * 判断这是什么页面，返回 null 表示不像账单/支付页面。
     *
     * 账单详情页必须由**专属证据**证明：
     * - 标题命中「账单详情」这类词，或
     * - 专属字段（交易单号 / 商户单号 / 收单机构…）命中 ≥ 2 个
     *
     * 只靠「交易 / 订单 / 金额」这类通用词凑数**不再放行** ——
     * 那正是「支付宝我的页被误判」的根因：那个页面同时出现「账单」「交易」两个词。
     */
    private fun classifyPage(pageText: String): PaymentPageType? {
        if (PaymentPageRules.billTitleKeywords.any { pageText.contains(it) }) {
            return PaymentPageType.BILL_DETAIL
        }
        val fieldHits = PaymentPageRules.billStrongFields.count { pageText.contains(it) }
        if (fieldHits >= PaymentPageRules.MIN_BILL_FIELD_HITS) {
            return PaymentPageType.BILL_DETAIL
        }
        if (PaymentPageRules.resultKeywords.any { pageText.contains(it) }) {
            return PaymentPageType.PAYMENT_RESULT
        }
        return null
    }

    private fun isSupportedPackage(packageName: String): Boolean =
        packageName in PaymentPageRules.supportedPackages ||
            (allowDebugRules && packageName == debugPackageName)
}
