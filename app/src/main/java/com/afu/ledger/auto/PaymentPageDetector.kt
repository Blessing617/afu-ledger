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
 * 强排除 → 列表页 → 主界面（辅助）→ **支付结果页** →（不是结果页时）**账单页正向准入** → 金额与方向 → 字段
 *
 * 结果页必须先于正向准入判定：微信支付成功页只有「支付成功 / ¥98.00 / 完成」，
 * 凑不出任何账单强字段，放在准入之后就会永远识别不到。
 *
 * ## 结果页与账单详情页都认
 *
 * 支付结果页（「付款成功」那种）与账单详情页都会产出 [PaymentDraft]，只是 `pageType` 不同。
 * 同一笔付款在结果页先命中时立即弹窗；随后账单详情页命中会被会话去重挡住（或合并进同一张卡片），
 * 不会记成两笔。结果页没有交易单号与账单时间，指纹与字段都更弱，这是已知且可接受的代价。
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

        val tradeNo = TradeNoExtractor.extract(fields)

        // ④ 支付结果页（「付款成功 / 支付成功」）先判 —— **必须先于账单页正向准入**。
        //
        //     10-20 把结果页整体排除，用来消除「同一笔付款在两页各记一次」；真机用一天后
        //     的副作用更严重：支付完成那一刻识别不到，必须再点进账单详情页才弹窗 —— 等于
        //     「支付过程中没有识别」。10-21 恢复结果页识别时把它放在正向准入之后，于是
        //     支付宝（付款成功页自带「当前状态」「付款方式」两个强字段）能识别，而微信
        //     支付成功页（只有「支付成功 / ¥98.00 / 完成」）在准入那一行就被判「不是支付页」，
        //     结果页规则永远执行不到 —— 真机表现就是「微信支付过程中不弹窗」。
        //
        //     判据刻意保守：**没有交易单号、没有账单标题、没有任何账单时间字段**才算结果页，
        //     再加行数上限与聊天/内容流特征词两道防线（聊天里别人发的「支付成功 ¥25.50」截图
        //     不能被当成自己的付款）。支付宝旧版账单详情页常常不渲染单号，但它一定有「支付时间」，
        //     因此不会被误判成结果页。
        val isResultPage = isPaymentResultPage(fields, pageText, lines, tradeNo)

        // ⑤ 正向准入：不是结果页时，页面必须自己证明它是账单详情页
        if (!isResultPage && !isBillDetailPage(fields, pageText)) {
            return DetectionResult.Missed(MissReason.NOT_PAYMENT_PAGE)
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

        // ⑥ 账单时间：结果页没有时间字段，只能回退识别时刻并标记来源
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
                pageType = if (isResultPage) PaymentPageType.PAYMENT_RESULT else PaymentPageType.BILL_DETAIL,
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
     * - 文本行数不超过 [PaymentPageRules.MAX_RESULT_PAGE_LINES]（聊天记录 / 文章能读成几十行）
     * - 不含聊天 / 内容流特征词（「按住 说话」这种输入栏文案不会出现在结果页上）
     */
    private fun isPaymentResultPage(
        fields: BillFieldParser,
        pageText: String,
        lines: List<String>,
        tradeNo: String?,
    ): Boolean {
        if (tradeNo != null) return false
        if (PaymentPageRules.billTitleKeywords.any { pageText.contains(it) }) return false
        if (BillTimeExtractor.labels.any { fields.hasLabel(it) }) return false
        if (lines.size > PaymentPageRules.MAX_RESULT_PAGE_LINES) return false
        if (PaymentPageRules.chatOrFeedKeywords.any { pageText.contains(it) }) return false

        // 商户支付成功页：状态词本身就是强证据
        if (PaymentPageRules.resultKeywords.any { pageText.contains(it) }) return true

        // 红包 / 转账完成页：状态词换成「已存入零钱 / 已收款 / 转账成功」，
        // 但必须先有「红包 / 转账」场景词兜底，否则零钱充值、理财收益这些页面会被误记。
        // 真机文案：收红包后是「已存入零钱」，收款方转账页是「已收款」，
        // 转出方是「转账成功 / 已存入对方零钱」。
        val hasTransferScene = PaymentPageRules.transferSceneKeywords.any { pageText.contains(it) }
        if (!hasTransferScene) return false
        return PaymentPageRules.incomeResultKeywords.any { pageText.contains(it) } ||
            PaymentPageRules.outgoingTransferKeywords.any { pageText.contains(it) }
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
