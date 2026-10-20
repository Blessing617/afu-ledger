package com.afu.ledger.auto

import com.afu.ledger.domain.model.TransType
import java.time.ZoneId

/**
 * 判定当前页面是不是**账单详情页**，并提取金额、方向、账单时间、商户与交易单号。
 *
 * 纯 Kotlin，无 Android 依赖，可直接单测。
 *
 * ## 判定顺序（见 [PaymentPageRules] 的说明）
 *
 * 强排除 → 列表页 → 主界面（辅助）→ **正向准入：必须是账单详情页** → 金额与方向 → 字段
 *
 * ## 本期只认账单详情页
 *
 * 支付结果页（「付款成功」那种）**不再参与判定**。用户明确要求「当前只想让它在账单界面
 * 点进账单才识别」，成熟产品（钱迹 / iCost）的识别范围也正是账单详情页。
 * [PaymentPageType.PAYMENT_RESULT] 保留给后续变更重新启用。
 *
 * @param debugPackageName debug 构建传自己的 applicationId，用于放行内置模拟页面；
 *   发布版传 null，此时只认微信与支付宝。
 */
class PaymentPageDetector(
    private val debugPackageName: String? = null,
    /** 解析账单时间用的时区；测试注入固定时区以获得稳定断言。 */
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    private val allowDebugRules: Boolean get() = debugPackageName != null

    fun detect(snapshot: PageSnapshot): DetectionResult {
        if (!isSupportedPackage(snapshot.packageName)) {
            return DetectionResult.Missed(MissReason.UNSUPPORTED_PACKAGE)
        }

        val fields = BillFieldParser.parse(snapshot)
        val lines = fields.lineTexts
        val pageText = lines.joinToString(separator = "\n")

        // ① 排除：明确指向「另一笔交易」的状态词
        if (PaymentPageRules.excludedKeywords.any { pageText.contains(it) }) {
            return DetectionResult.Missed(MissReason.EXCLUDED_KEYWORD)
        }

        // ② 排除：列表页。词表 + 「多行日期时间」双判据。
        if (isListPage(lines, pageText)) {
            return DetectionResult.Missed(MissReason.MAIN_SCREEN)
        }

        // ③ 排除：主界面 / 我的页（辅助判据，不再是唯一防线）
        val homeHits = PaymentPageRules.homeOrMyKeywords.count { pageText.contains(it) }
        if (homeHits >= PaymentPageRules.MIN_HOME_SCREEN_HITS) {
            return DetectionResult.Missed(MissReason.MAIN_SCREEN)
        }

        // ④ 正向准入：页面必须自己证明它是账单详情页
        if (!isBillDetailPage(fields, pageText)) {
            return DetectionResult.Missed(MissReason.NOT_PAYMENT_PAGE)
        }

        val tradeNo = TradeNoExtractor.extract(fields)

        // ④.5 支付结果页不参与入账。
        //     支付宝的「支付成功」页会带「付款方式」等字段，能凑够强证据门槛，
        //     但它不是账单详情页 —— 同一笔付款在「聚合收款」与「支付成功」两页各命中一次，
        //     正是 2026-10-20 重复记账的直接原因之一。结果页不弹，账单详情页照常。
        //
        //     判据刻意保守：**没有交易单号、没有账单标题、也没有任何账单时间字段**才算结果页。
        //     支付宝旧版账单详情页常常不渲染单号，但它一定有「支付时间」，因此不会被误杀。
        if (isPaymentResultPage(fields, pageText, tradeNo)) {
            return DetectionResult.Missed(MissReason.PAYMENT_RESULT_PAGE)
        }

        // ⑤ 金额与方向
        val amountResult = AmountParser.parseTransaction(lines, allowBareAmount = true)
        val (amount, amountDirection, amountLine) = when (amountResult) {
            AmountParser.Result.None -> return DetectionResult.Missed(MissReason.NO_AMOUNT)
            is AmountParser.Result.Ambiguous ->
                return DetectionResult.Missed(MissReason.AMBIGUOUS_AMOUNT)
            is AmountParser.Result.Ok -> Triple(
                amountResult.amount,
                amountResult.direction,
                amountResult.line,
            )
        }
        if (!amount.isPositive()) return DetectionResult.Missed(MissReason.ZERO_AMOUNT)

        // 方向：金额自身给不出时，用页面状态词兜底；都判不出就放弃，绝不猜
        val direction = amountDirection ?: statusDirection(pageText)
            ?: return DetectionResult.Missed(MissReason.UNKNOWN_DIRECTION)

        // ⑥ 账单时间：解析失败才回退识别时刻，并标记来源
        val time = BillTimeExtractor.extract(fields, zone)
        val occurredAt = time?.epochMillis ?: System.currentTimeMillis()
        val occurredAtSource = if (time != null) TimeSource.PAGE else TimeSource.FALLBACK

        return DetectionResult.Hit(
            PaymentDraft(
                amount = amount,
                direction = direction,
                merchant = MerchantExtractor.extract(fields, amount),
                tradeNo = tradeNo,
                pageCategory = fields.valueOf("账单分类", "交易分类", "分类"),
                source = PaymentPageRules.sourceLabel(snapshot.packageName),
                sourcePackage = snapshot.packageName,
                pageType = PaymentPageType.BILL_DETAIL,
                occurredAt = occurredAt,
                occurredAtSource = occurredAtSource,
                capturedAt = System.currentTimeMillis(),
                amountLine = amountLine,
            ),
        )
    }

    /**
     * 列表页判定。
     *
     * 两个信号任一成立即算列表页：
     * - 列表页专属文案命中 ≥ [PaymentPageRules.MIN_LIST_PAGE_HITS] 个
     * - 页面里出现 ≥ [PaymentPageRules.LIST_PAGE_MIN_TIMESTAMP_LINES] 行「日期 + 时间」
     */
    private fun isListPage(lines: List<String>, pageText: String): Boolean {
        val keywordHits = PaymentPageRules.listPageKeywords.count { pageText.contains(it) }
        if (keywordHits >= PaymentPageRules.MIN_LIST_PAGE_HITS) return true

        val timestampLines = lines.count { PaymentPageRules.timestampLinePattern.containsMatchIn(it) }
        return timestampLines >= PaymentPageRules.LIST_PAGE_MIN_TIMESTAMP_LINES
    }

    /**
     * 页面是不是账单详情页。
     *
     * 两条路径任一成立即可：
     * - 标题命中「账单详情」这类词
     * - 专属字段（交易单号 / 收单机构 / 商户全称…）命中 ≥ [PaymentPageRules.MIN_BILL_FIELD_HITS] 个
     *
     * 只靠「交易 / 订单 / 金额」这类通用词凑数**不放行** ——
     * 那正是「支付宝我的页被误判」的根因：那个页面同时出现「账单」「交易」两个词。
     */
    private fun isBillDetailPage(fields: BillFieldParser, pageText: String): Boolean {
        if (PaymentPageRules.billTitleKeywords.any { pageText.contains(it) }) return true
        val fieldHits = PaymentPageRules.billStrongFields.count { fields.hasLabel(it) }
        return fieldHits >= PaymentPageRules.MIN_BILL_FIELD_HITS
    }

    /**
     * 是不是「支付结果页」（付款成功那种）。
     *
     * 判据刻意保守，避免误杀旧版支付宝账单详情页（它经常不渲染交易单号）：
     * - 必须命中成功类状态词
     * - 没有账单标题
     * - 没有任何交易单号
     * - **也没有任何账单时间字段**（旧版账单一定有「支付时间」，所以不会被误杀）
     */
    private fun isPaymentResultPage(
        fields: BillFieldParser,
        pageText: String,
        tradeNo: String?,
    ): Boolean {
        if (tradeNo != null) return false
        if (PaymentPageRules.billTitleKeywords.any { pageText.contains(it) }) return false
        if (BillTimeExtractor.labels.any { fields.hasLabel(it) }) return false
        return PaymentPageRules.resultKeywords.any { pageText.contains(it) }
    }

    /** 用页面状态词兜底判断方向。 */
    private fun statusDirection(pageText: String): TransType? {
        if (PaymentPageRules.incomeStatusKeywords.any { pageText.contains(it) }) {
            return TransType.INCOME
        }
        if (PaymentPageRules.expenseStatusKeywords.any { pageText.contains(it) }) {
            return TransType.EXPENSE
        }
        return null
    }

    private fun isSupportedPackage(packageName: String): Boolean =
        packageName in PaymentPageRules.supportedPackages ||
            (allowDebugRules && packageName == debugPackageName)
}
