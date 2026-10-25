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

        // ①.5 **聊天 / 会话页一级排除**（必须排在结果页判定之前）。
        //
        // 真机事故：打开与好友的聊天界面，聊天里的转账提示（27 元）被当成一笔交易弹出确认卡片，
        // 金额还被 OCR 读成 2700；联系人昵称被当成商户名。原因是聊天里的转账 / 收款卡片
        // 自带「转账」「已收款」「已存入零钱」，这些正是转账结果页的状态词，
        // 而原来只有「结果页」内部一道聊天检查，靠 OCR 读出输入栏的「按住 说话」——
        // 截图没读到它就直接穿过去了。
        //
        // 例外：真正的转账账单详情页也会出现「转账给」。所以命中聊天卡片类词时再看强字段，
        // 够 [MIN_BILL_FIELD_HITS] 个就认为它是账单页，继续往下走。
        if (PaymentPageRules.containsChatEvidence(pageText)) {
            val strongFieldHits = PaymentPageRules.billStrongFields.count { fields.hasLabel(it) }
            if (strongFieldHits < PaymentPageRules.MIN_BILL_FIELD_HITS) {
                return DetectionResult.Missed(MissReason.CHAT_PAGE)
            }
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
        if (!isResultPage && !isBillDetailPage(fields, lines)) {
            return DetectionResult.Missed(MissReason.NOT_PAYMENT_PAGE)
        }

        // ⑤ 金额与方向。结果页额外要求金额带小数点：OCR 丢小数点会把 27.00 读成 2700。
        val amountResult = AmountParser.parseTransaction(
            lines,
            allowBareAmount = true,
            requireDecimal = isResultPage,
        )
        val (amount, amountDirection, amountLine) = when (amountResult) {
            AmountParser.Result.None -> return DetectionResult.Missed(MissReason.NO_AMOUNT)
            is AmountParser.Result.Ambiguous ->
                return DetectionResult.Missed(MissReason.AMBIGUOUS_AMOUNT)
            is AmountParser.Result.MissingDecimal ->
                return DetectionResult.Missed(MissReason.AMOUNT_LOST_DECIMAL)
            is AmountParser.Result.Ok -> Triple(
                amountResult.amount,
                amountResult.direction,
                amountResult.line,
            )
        }
        if (!amount.isPositive()) return DetectionResult.Missed(MissReason.ZERO_AMOUNT)

        // ⑤.5 结果页的结构判据：**金额必须是全页字号的峰值**。
        //
        // 这条是为真机事故补的：用户在微信里付了 27.00（正常弹窗入账），随后点开与好友的聊天界面，
        // 又弹出一张卡片，金额还是 2700。词表拦不住它——聊天正文里既没有「转账」也没有「已收款」，
        // 页面又缺少账单字段，于是被当成「支付结果页」放行了。
        //
        // 但支付结果页有一个聊天页永远不具备的特征：金额是全页最大的字
        // （微信支付成功页的金额约是正文的两三倍）。OCR 给得出每行的高度，就用它做判据。
        // 高度信息缺失时（节点通道、纯文本测试）跳过这条，不用「猜不到」去否掉识别。
        if (isResultPage && !amountIsMostProminent(snapshot, amountLine)) {
            return DetectionResult.Missed(MissReason.AMOUNT_NOT_PROMINENT)
        }

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
                pageHeadline = headlineOf(lines),
            ),
        )
    }

    /**
     * 页面文本摘要：前 3 行、每行最多 24 字、总长不超过 96 字。
     *
     * 只给日志排查用。误识别（尤其「聊天页为什么被判成支付页」）没有这份摘要就只能靠猜，
     * 有它就能直接看到是哪个词凑出的证据。
     */
    private fun headlineOf(lines: List<String>): String =
        lines.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(HEADLINE_LINES)
            .map { it.take(HEADLINE_CHARS) }
            .joinToString(" | ")
            .take(HEADLINE_TOTAL)

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
        if (timestampLines >= PaymentPageRules.LIST_PAGE_MIN_TIMESTAMP_LINES) return true

        // 微信会话列表：每行右侧只有「14:58」这种时分，没有日期。
        // 真机事故就是它被当成支付结果页放行的（会话列表里「微信支付 · 已支付¥27.00」那一行）。
        val clockLines = lines.count { PaymentPageRules.clockOnlyPattern.containsMatchIn(it) }
        return clockLines >= PaymentPageRules.CLOCK_LINES_MIN
    }

    /**
     * 页面是不是账单详情页。
     *
     * 两条路径任一成立即可：
     * - 标题（**只扫页面前几行**）命中「账单详情」这类词
     * - 专属字段（交易单号 / 收单机构 / 商户全称…）命中 ≥ [PaymentPageRules.MIN_BILL_FIELD_HITS] 个
     *
     * 标题只在页首匹配：真机踩过「微信支付公众号会话里每条记录都带一个『账单详情 >』链接」，
     * 按整页包含来判，整个会话页都会被当成账单详情页。
     *
     * 只靠「交易 / 订单 / 金额」这类通用词凑数**不放行** ——
     * 那正是「支付宝我的页被误判」的根因：那个页面同时出现「账单」「交易」两个词。
     */
    private fun isBillDetailPage(fields: BillFieldParser, lines: List<String>): Boolean {
        // 标题必须「自己是一行标题」：以关键词开头，且不带「>」这类跳转标记。
        // 微信支付公众号会话里每条记录下面都挂一个「账单详情 >」链接，
        // 按「页面上出现过这个词」来判，整个会话页都会被当成账单页。
        val titled = lines.take(BILL_TITLE_SCAN_LINES).any { line ->
            val trimmed = line.trim()
            PaymentPageRules.billTitleKeywords.any { keyword ->
                trimmed.startsWith(keyword) && !trimmed.contains('>') && !trimmed.contains('›')
            }
        }
        if (titled) return true
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

        // 微信支付公众号会话页：底部挂着「我的账单 / 支付服务」菜单，
        // 里面的消费记录卡片也是大字金额，长得像结果页。菜单是它独有的、稳定的区别。
        val normalizedPage = PaymentPageRules.normalizeForMatch(pageText)
        if (PaymentPageRules.wechatPayConversationKeywords.any {
                normalizedPage.contains(PaymentPageRules.normalizeForMatch(it))
            }
        ) {
            return false
        }

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

    /**
     * 金额行是不是页面上字号的峰值。
     *
     * 判据：金额行的高度 ≥ 其它行高度中位数的 [PROMINENT_RATIO] 倍。
     * 支付结果页的金额字号远大于正文，聊天正文里的数字则与正文同高。
     *
     * 返回 `true` 表示通过（或无法判定）：**只有明确「不够大」才拒绝**，
     * 避免因为 OCR 行高抖动把正常账单页挡掉。
     */
    private fun amountIsMostProminent(snapshot: PageSnapshot, amountLine: String): Boolean {
        val sized = snapshot.items.filter { it.height > 0 }
        if (sized.size < 3) return true // 高度信息不足，不做判断
        val amountItem = sized.firstOrNull { item ->
            val text = item.text.trim()
            text.isNotEmpty() && (text.contains(amountLine.trim()) || amountLine.trim().contains(text))
        } ?: return true
        val others = sized.filterNot { it === amountItem }.map { it.height }
        if (others.isEmpty()) return true
        // 用「其它行里的最大字号」而不是中位数：会话列表里状态栏那种小字会把中位数拉低，
        // 于是列表正文的金额也能通过 1.5 倍中位数。支付结果页的金额连**最大**的那行都要压过去。
        val tallestOther = others.max()
        return amountItem.height.toFloat() >= tallestOther * PROMINENT_RATIO
    }

    private fun isSupportedPackage(packageName: String): Boolean =
        packageName in PaymentPageRules.supportedPackages ||
            (allowDebugRules && packageName == debugPackageName)

    private companion object {
        const val HEADLINE_LINES = 3
        const val HEADLINE_CHARS = 24
        const val HEADLINE_TOTAL = 96

        /** 金额行相对**其它行最大字高**的倍数下限。支付页通常是 2 倍以上，这里留足余量。 */
        const val PROMINENT_RATIO = 1.2f

        /** 账单标题只在页首这几行内匹配：标题本来就在顶部，靠后的「账单详情」多半是链接。 */
        const val BILL_TITLE_SCAN_LINES = 5
    }
}
