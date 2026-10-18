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

        // 主界面 / 列表页先排除。
        //
        // 支付宝首页的「最近消息」区会列出历史通知文本（「天猫 / 付款成功 / ¥25.00」），
        // 只看特征词 + 金额会把它误判成支付结果页；支付结果页是独立页面，
        // 不会同时出现「扫一扫 / 收付款 / 卡包」这类主界面固定入口。
        // 这一步必须放在匹配规则**之前**：一旦先命中规则，后面就只会去解析金额与商户名，
        // 而错的是「这是支付页」这个判断本身。
        val mainScreenHits = PaymentPageRules.mainScreenKeywords.count { pageText.contains(it) }
        if (mainScreenHits >= PaymentPageRules.MIN_MAIN_SCREEN_HITS) {
            return DetectionResult.Missed(MissReason.MAIN_SCREEN)
        }

        val pageType = classifyPage(pageText)
            ?: return DetectionResult.Missed(MissReason.NOT_PAYMENT_PAGE)

        // 金额唯一才命中 —— 这是本项目的安全底线：
        // 页面同时出现两个不同金额时直接放弃，宁可漏记也不记错。
        // （Tally 的做法是多金额时按评分选一个，我们刻意不采纳这一条。）
        val amounts = AmountParser.parseTransactionAmounts(
            texts = snapshot.texts,
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
                        merchant = MerchantExtractor.extract(snapshot.texts, amount),
                        sourcePackage = snapshot.packageName,
                        pageType = pageType,
                        capturedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    /**
     * 判断这是什么页面，返回 null 表示不像交易页面。
     *
     * 判据分两级（学 Tally 的「关键词」思路，但保留强/弱特征的区分）：
     *
     * 1. **强特征**：命中「支付成功」「账单详情」这类明确的页面标题词 → 直接认定
     * 2. **弱特征**：命中「交易 / 订单 / 商品说明 / 付款方式」这类通用交易词汇，
     *    且数量达到 [PaymentPageRules.MIN_TRADE_KEYWORD_HITS] → 认为是交易详情页
     *
     * 第 2 级是为了覆盖标题不规范的真实页面：支付宝的「余额明细详情」页
     * 标题既不是「支付成功」也不是「账单详情」，但它有金额、有交易号，
     * 属于应当识别的账单页。
     */
    private fun classifyPage(pageText: String): PaymentPageType? {
        // 强特征：账单详情优先判定。
        //
        // 真实账单详情页里常带「支付成功」这个状态文案，如果先按支付结果页匹配，
        // 就会因为裸金额（`-25.50`）不被接受而漏掉。
        if (allowDebugRules && PaymentPageRules.billKeywords.any { pageText.contains(it) }) {
            return PaymentPageType.BILL_DETAIL
        }
        if (PaymentPageRules.resultKeywords.any { pageText.contains(it) }) {
            return PaymentPageType.PAYMENT_RESULT
        }
        // 发布版不加载账单规则，但「余额明细详情」这类页面对发布版同样有价值，
        // 因此走弱特征路径识别。此时按支付结果页处理（不接受裸金额），更保守。
        val tradeHits = PaymentPageRules.tradeKeywords.count { pageText.contains(it) }
        if (tradeHits >= PaymentPageRules.MIN_TRADE_KEYWORD_HITS) {
            return if (allowDebugRules) PaymentPageType.BILL_DETAIL else PaymentPageType.PAYMENT_RESULT
        }
        return null
    }

    private fun isSupportedPackage(packageName: String): Boolean =
        packageName in PaymentPageRules.supportedPackages ||
            (allowDebugRules && packageName == debugPackageName)
}
