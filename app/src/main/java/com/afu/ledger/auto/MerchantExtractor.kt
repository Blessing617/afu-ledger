package com.afu.ledger.auto

import com.afu.ledger.domain.model.Money

/**
 * 从页面文本里猜商户名。
 *
 * 这是「尽力而为」：读不到就返回 null，绝不编一个。
 *
 * 取法按可信度递减：
 * 1. **金额所在视觉行及其邻近行的单个 cell** —— 账单页顶部的大字商户名就在金额旁边，
 *    这也是用户眼里「这笔是跟谁交易的」的答案（微信是「美团」，不是
 *    「上海米哈游影铁科技有限公司」这种主体全称）。
 * 2. **商户字段** —— `收款方全称` / `商户全称` / `对方账户` 这类标签后面的值。
 *    真机踩到的场景：支付宝「扫收钱码付款」详情页的商户名在节点树里排到了第 22 行，
 *    按邻近根本取不到，卡片上只能显示「未识别到商户」。
 *
 * 两步都失败就返回 null。
 *
 * ## 为什么必须按 cell 取，不能按整行文本取
 *
 * 同一个视觉行上经常并排放着两个按钮。真机证据（2026-10-20 的自动记账备注）：
 * 备注写成了「回首页 返回商家」「返回 按钮」「返回 按钮 完成」——
 * 这些正是把一行里的多个按钮 cell 拼成整行后当成商户名的结果。
 * 现在逐 cell 判断，并单独排除按钮 / 活动文案。
 */
object MerchantExtractor {

    private const val MIN_LENGTH = 2
    private const val MAX_LENGTH = 24

    /** 整段文本正好是这些词时不可能是商户名。 */
    private val noise: Set<String> = setOf(
        "支付成功", "付款成功", "交易成功", "已支付", "支付完成", "付款完成",
        "完成", "确定", "返回", "关闭", "分享", "详情", "账单详情", "查看账单详情",
        "查看更多", "查看详情", "联系商家", "对此订单有疑问", "本服务由财付通提供",
        "回首页", "返回商家", "返回上一页", "忽略", "取消",
    )

    /** 文本只要包含其中任意片段就排除。 */
    private val bannedFragments: List<String> = listOf(
        "支付", "付款", "交易单号", "商户单号", "订单号", "转账单号", "支付方式", "当前状态",
        "账单分类", "支付时间", "创建时间", "收款时间", "转账时间", "账单", "查看", "优惠",
        "退款", "余额", "零钱", "银行卡", "花呗", "成功", "合计", "收起", "更多",
        // 金额行本身（支付宝账单页会渲染成「支出25元」）
        "支出", "收入", "入账", "收款",
        // 订单状态文案，常与金额相邻
        "等待确认收货", "确认收货", "待收货", "已发货", "待发货",
        "已完成", "待付款", "已关闭", "已取消", "待使用", "待评价",
        // 页面区块标题
        "交易", "详情", "账单服务", "商家小程序",
        // 外币与优惠
        "标价", "汇率", "美元", "立减",
        // 按钮 / 页面操作：真机把「回首页 返回商家」「返回 按钮」「完成」写进了备注
        "按钮", "回首页", "返回商家", "返回上一页", "忽略", "去参与", "一键领",
        // 支付结果页的活动与广告区块
        "支付有礼", "体验金", "账单贴纸", "碰友节", "红包", "补贴", "去开通", "立即领取",
    )

    /** 空格/点分隔的多段文本里，每一段都是这类操作词时，整段不是商户名。 */
    private val actionTokens: Set<String> = setOf(
        "返回", "回首页", "返回商家", "返回上一页", "完成", "确定", "取消", "关闭", "分享",
        "忽略", "按钮", "更多", "收起", "展开", "参与", "去参与", "一键领", "领取",
        "首页", "商家", "详情", "查看", "首页", "返回首页",
    )

    private val tokenSeparator = Regex("""[\s·|]+""")

    private val amountLike = Regex("""^[¥￥]?\s*[+-]?\d[\d,]*(?:\.\d{1,2})?\s*元?$""")
    private val timeLike = Regex("""\d{1,2}:\d{2}""")
    private val dateLike = Regex("""\d{4}[-/年]\d{1,2}[-/月]\d{1,2}""")
    private val longDigits = Regex("""\d{6,}""")

    /**
     * 兼容旧调用的入口：直接给文本行（无坐标），内部会合成顺序坐标。
     */
    fun extract(texts: List<String>, amount: Money): String? =
        extract(BillFieldParser.parse(PageSnapshot.ofTexts("", texts)), amount)

    /**
     * @param fields 已还原成视觉行的页面文本
     * @param amount 已解析出的金额，用于定位金额所在行
     */
    fun extract(fields: BillFieldParser, amount: Money): String? {
        val rows = fields.rows

        // 金额可能不是单独成行：支付宝账单页把「支出25元」放在一个 cell 里。
        // 先找出所有「包含这个金额」的视觉行。
        val amountRowIndexes = rows.indices.filter { index ->
            rows[index].cells.any { cell -> cellMatchesAmount(cell, amount) }
        }

        // 候选顺序：同行其它 cell → 下一行 → 上一行 → 再远一行。
        // 顺序本身有意义：「金额紧邻」比「隔一行」更可信。
        val candidateRows = buildList {
            amountRowIndexes.forEach { index ->
                add(index)
                add(index + 1)
                add(index - 1)
                add(index + 2)
                add(index - 2)
            }
        }

        candidateRows.forEach { rowIndex ->
            val row = rows.getOrNull(rowIndex) ?: return@forEach
            row.cells.forEach { cell ->
                val candidate = cell.trim()
                if (candidate.isNotEmpty() && isMerchantLike(candidate)) return candidate
            }
        }

        return extractFromMerchantField(fields)
    }

    /** 商户字段标签后面（同行右侧、或同一行冒号后、或下一行）的值。 */
    private fun extractFromMerchantField(fields: BillFieldParser): String? {
        for (label in PaymentPageRules.merchantLabels) {
            val value = fields.valueOf(label) ?: continue
            val inline = value.substringAfter(':', "").substringAfter('：', "").trim()
            val candidate = if (inline.isNotEmpty() && isMerchantLike(inline)) inline else value.trim()
            if (isMerchantLike(candidate)) return candidate
        }
        return null
    }

    private fun cellMatchesAmount(cell: String, amount: Money): Boolean =
        AmountParser.parseOne(cell)?.abs() == amount || AmountParser.parseBare(cell)?.abs() == amount

    private fun isMerchantLike(text: String): Boolean {
        if (text.length !in MIN_LENGTH..MAX_LENGTH) return false
        if (text in noise) return false
        if (text.any { it == '¥' || it == '￥' }) return false
        if (amountLike.matches(text)) return false
        if (timeLike.containsMatchIn(text) || dateLike.containsMatchIn(text)) return false
        if (bannedFragments.any { text.contains(it) }) return false
        // 订单号/单号类的长数字串不是商户名
        if (longDigits.containsMatchIn(text)) return false
        // 「回首页 返回商家」「返回 按钮 完成」：多段操作词拼在一起的整行
        if (isOnlyActionWords(text)) return false
        // 至少得有一个字母或汉字，纯符号/数字不要
        if (text.none { it.isLetter() }) return false
        return true
    }

    private fun isOnlyActionWords(text: String): Boolean {
        val tokens = text.split(tokenSeparator).filter { it.isNotBlank() }
        if (tokens.size < 2) return false
        return tokens.all { token -> token in actionTokens || token in noise }
    }
}
