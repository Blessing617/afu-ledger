package com.afu.ledger.auto

import com.afu.ledger.domain.model.Money
import com.afu.ledger.domain.model.TransType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentPageDetectorTest {

    private val releaseDetector = PaymentPageDetector()

    private fun hit(result: DetectionResult): PaymentDraft {
        assertTrue("期望命中，实际 $result", result is DetectionResult.Hit)
        return (result as DetectionResult.Hit).draft
    }

    private fun miss(result: DetectionResult): MissReason {
        assertTrue("期望未命中，实际 $result", result is DetectionResult.Missed)
        return (result as DetectionResult.Missed).reason
    }

    // ---- 公开常见文案样本（微信 / 支付宝各多套），用于规则校准 ----

    @Test
    fun `微信支付成功页`() {
        val draft = hit(
            releaseDetector.detect(
                PageSnapshot.ofTexts(
                    PaymentPageRules.WECHAT_PACKAGE,
                    listOf(
                        "当前状态", "支付成功", "¥25.50", "瑞幸咖啡",
                        "交易单号", "4500000576202610184019086744",
                    ),
                ),
            ),
        )
        assertEquals(Money(2550), draft.amount)
        assertEquals("瑞幸咖啡", draft.merchant)
        assertEquals(PaymentPageType.BILL_DETAIL, draft.pageType)
    }

    @Test
    fun `微信旧版文案 已支付`() {
        val draft = hit(
            releaseDetector.detect(
                PageSnapshot.ofTexts(
                    PaymentPageRules.WECHAT_PACKAGE,
                    listOf(
                        "当前状态", "已支付", "15.00元", "美团单车",
                        "交易单号", "4500000576202610184019086744",
                    ),
                ),
            ),
        )
        assertEquals(Money(1500), draft.amount)
    }

    @Test
    fun `支付宝付款成功页`() {
        val draft = hit(
            releaseDetector.detect(
                PageSnapshot.ofTexts(
                    PaymentPageRules.ALIPAY_PACKAGE,
                    listOf(
                        "付款成功", "¥120.00", "盒马鲜生", "收单机构",
                        "某支付科技有限公司", "交易单号", "2026101822001",
                    ),
                ),
            ),
        )
        assertEquals(Money(12000), draft.amount)
        assertEquals("盒马鲜生", draft.merchant)
    }

    @Test
    fun `支付宝交易成功页`() {
        val draft = hit(
            releaseDetector.detect(
                PageSnapshot.ofTexts(
                    PaymentPageRules.ALIPAY_PACKAGE,
                    listOf(
                        "交易成功", "¥6.50", "便利蜂", "收单机构",
                        "某支付科技有限公司", "交易单号", "2026101822001",
                    ),
                ),
            ),
        )
        assertEquals(Money(650), draft.amount)
    }

    // ---- 不该触发的情况 ----

    @Test
    fun `支付宝支付成功结果页也入账并标记来源`() {
        // 真机 2026-10-21：结果页最早出现，必须在这里就弹窗。
        // 同一笔随后出现在账单详情页时，由会话去重挡住第二次（见 PaymentSessionDeduperTest）。
        val draft = hit(
            releaseDetector.detect(
                PageSnapshot.ofTexts(
                    PaymentPageRules.ALIPAY_PACKAGE,
                    listOf(
                        "支付成功", "¥20.00", "某人工智能公司", "付款方式",
                        "账户余额", "当前状态", "支付成功", "完成",
                        "支付有礼", "去参与", "回首页", "返回商家",
                    ),
                ),
            ),
        )
        assertEquals(Money(2000), draft.amount)
        assertEquals(TransType.EXPENSE, draft.direction)
        assertEquals(PaymentPageType.PAYMENT_RESULT, draft.pageType)
        assertEquals(TimeSource.FALLBACK, draft.occurredAtSource)
    }

    @Test
    fun `结果页带交易单号仍按账单入账`() {
        // 微信「当前状态 支付成功 + 交易单号」这类页面就是账单详情页，不能被结果页规则误杀
        val draft = hit(
            releaseDetector.detect(
                PageSnapshot.ofTexts(
                    PaymentPageRules.WECHAT_PACKAGE,
                    listOf(
                        "当前状态", "支付成功", "¥25.50", "瑞幸咖啡",
                        "交易单号", "4500000576202610184019086744",
                    ),
                ),
            ),
        )
        assertEquals(Money(2550), draft.amount)
    }

    @Test
    fun `旧版账单无单号但有支付时间仍入账`() {
        // 支付宝旧版账单页不渲染交易单号，但一定有「支付时间」——结果页规则不能误杀它
        val draft = hit(
            releaseDetector.detect(
                PageSnapshot.ofTexts(
                    PaymentPageRules.ALIPAY_PACKAGE,
                    listOf(
                        "账单详情", "天猫", "支出25元", "交易成功",
                        "支付时间", "2026-10-19 18:01:22", "付款方式", "账户余额",
                    ),
                ),
            ),
        )
        assertEquals(Money(2500), draft.amount)
    }

    @Test
    fun `非受支持应用直接跳过`() {
        assertEquals(
            MissReason.UNSUPPORTED_PACKAGE,
            miss(releaseDetector.detect(PageSnapshot.ofTexts("com.example.shop", listOf("支付成功", "¥25.50")))),
        )
    }

    @Test
    fun `聊天列表不是支付页`() {
        assertEquals(
            MissReason.CHAT_PAGE,
            miss(
                releaseDetector.detect(
                    PageSnapshot.ofTexts(PaymentPageRules.WECHAT_PACKAGE, listOf("微信", "文件传输助手", "今天 12:30")),
                ),
            ),
        )
    }

    @Test
    fun `扫码页不是支付页`() {
        assertEquals(
            MissReason.NOT_PAYMENT_PAGE,
            miss(
                releaseDetector.detect(
                    PageSnapshot.ofTexts(PaymentPageRules.WECHAT_PACKAGE, listOf("扫一扫", "相册", "取消")),
                ),
            ),
        )
    }

    @Test
    fun `金额无法解析`() {
        assertEquals(
            MissReason.NO_AMOUNT,
            miss(
                releaseDetector.detect(
                    PageSnapshot.ofTexts(
                        PaymentPageRules.WECHAT_PACKAGE,
                        listOf(
                            "当前状态", "支付成功", "金额",
                            "交易单号", "4500000576202610184019086744",
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `多个金额视为歧义`() {
        assertEquals(
            MissReason.AMBIGUOUS_AMOUNT,
            miss(
                releaseDetector.detect(
                    PageSnapshot.ofTexts(
                        PaymentPageRules.WECHAT_PACKAGE,
                        listOf(
                            "当前状态", "¥25.50", "¥3.00",
                            "交易单号", "4500000576202610184019086744",
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `金额为零放弃`() {
        assertEquals(
            MissReason.ZERO_AMOUNT,
            miss(
                releaseDetector.detect(
                    PageSnapshot.ofTexts(
                        PaymentPageRules.WECHAT_PACKAGE,
                        listOf(
                            "当前状态", "支付成功", "¥0.00",
                            "交易单号", "4500000576202610184019086744",
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `未来时态不记账`() {
        assertEquals(
            MissReason.EXCLUDED_KEYWORD,
            miss(
                releaseDetector.detect(
                    PageSnapshot.ofTexts(PaymentPageRules.WECHAT_PACKAGE, listOf("支付成功", "¥19.90", "将于明日扣款")),
                ),
            ),
        )
    }

    @Test
    fun `退款账单页记为收入`() {
        // 本期支持收入：退款页金额没有方向前缀，靠状态词「退款成功」判成收入。
        val draft = hit(
            releaseDetector.detect(
                PageSnapshot.ofTexts(
                    PaymentPageRules.ALIPAY_PACKAGE,
                    listOf(
                        "账单详情", "退款成功", "100元", "账单分类", "退款",
                        "交易单号", "2026101522001412345678",
                    ),
                ),
            ),
        )
        assertEquals(Money(10000), draft.amount)
        assertEquals(TransType.INCOME, draft.direction)
    }

    @Test
    fun `无强字段的极简支付成功页也按结果页入账`() {
        // 10-22：结果页判定提前到正向准入之前。支付宝/微信的支付成功页都可能只有
        // 「成功词 + 金额 + 完成」，凑不出任何账单强字段 —— 这正是微信支付过程中不弹窗的根因。
        val draft = hit(
            releaseDetector.detect(
                PageSnapshot.ofTexts(
                    PaymentPageRules.ALIPAY_PACKAGE,
                    listOf("付款成功", "¥25.50", "瑞幸咖啡", "完成"),
                ),
            ),
        )
        assertEquals(Money(2550), draft.amount)
        assertEquals(TransType.EXPENSE, draft.direction)
        assertEquals(PaymentPageType.PAYMENT_RESULT, draft.pageType)
    }

    @Test
    fun `微信支付成功页带原价与优惠仍取实付金额`() {
        // 真机样本（小红书同款）：微信生活缴费的支付成功页。
        // 主金额是「¥ 98.00」（¥ 与数字之间有空格），另有「原价 ¥100.00」与「优惠 …¥2.00」。
        val draft = hit(
            releaseDetector.detect(
                PageSnapshot.ofTexts(
                    PaymentPageRules.WECHAT_PACKAGE,
                    listOf(
                        "支付成功", "某缴费平台", "¥ 98.00",
                        "原价 ¥100.00", "优惠 河南农信储蓄卡¥2.00",
                        "为环卫工人送爱心餐", "完成",
                    ),
                ),
            ),
        )
        assertEquals(Money(9800), draft.amount)
        assertEquals(TransType.EXPENSE, draft.direction)
        assertEquals(PaymentPageType.PAYMENT_RESULT, draft.pageType)
        assertEquals(TimeSource.FALLBACK, draft.occurredAtSource)
    }

    @Test
    fun `聊天页里出现支付成功文案不识别`() {
        // 别人发来的「支付成功 ¥25.50」截图不能被当成自己的付款。
        // 微信聊天页底部固定有「按住 说话」「语音输入」这类输入栏文案。
        val texts = listOf(
            "群友", "在吗", "支付成功", "¥25.50", "看看这个截图",
            "按住 说话", "表情", "发送",
        )
        assertEquals(
            MissReason.CHAT_PAGE,
            miss(releaseDetector.detect(PageSnapshot.ofTexts(PaymentPageRules.WECHAT_PACKAGE, texts))),
        )
    }

    @Test
    fun `公众号文章里出现支付成功文案不识别`() {
        val texts = listOf(
            "支付成功", "¥25.50", "教你如何开通自动记账",
            "阅读原文", "点赞", "在看", "分享到朋友圈",
        )
        assertEquals(
            MissReason.NOT_PAYMENT_PAGE,
            miss(releaseDetector.detect(PageSnapshot.ofTexts(PaymentPageRules.WECHAT_PACKAGE, texts))),
        )
    }

    @Test
    fun `超长页面不当作支付结果页`() {
        // 聊天记录能被 OCR 读成几十行；行数上限挡住「长文里恰好出现成功词 + 金额」。
        val texts = buildList {
            add("支付成功")
            add("¥25.50")
            repeat(PaymentPageRules.MAX_RESULT_PAGE_LINES + 5) { add("聊天记录第 $it 条") }
        }
        assertEquals(
            MissReason.NOT_PAYMENT_PAGE,
            miss(releaseDetector.detect(PageSnapshot.ofTexts(PaymentPageRules.WECHAT_PACKAGE, texts))),
        )
    }

    @Test
    fun `微信结果页无单号无时间也可入账`() {
        val draft = hit(
            releaseDetector.detect(
                PageSnapshot.ofTexts(
                    PaymentPageRules.WECHAT_PACKAGE,
                    listOf("支付成功", "¥25.50", "瑞幸咖啡", "当前状态", "支付方式", "零钱"),
                ),
            ),
        )
        assertEquals(Money(2550), draft.amount)
        assertEquals(PaymentPageType.PAYMENT_RESULT, draft.pageType)
        assertEquals(TimeSource.FALLBACK, draft.occurredAtSource)
    }

    // ---- 红包 / 转账完成页（无单号无时间的那一刻）----

    @Test
    fun `微信红包领取页按收入入账`() {
        val draft = hit(
            releaseDetector.detect(
                PageSnapshot.ofTexts(
                    PaymentPageRules.WECHAT_PACKAGE,
                    listOf("微信红包", "¥128.50", "已存入零钱", "红包详情", "看看大家的手气", "完成"),
                ),
            ),
        )
        assertEquals(Money(12850), draft.amount)
        assertEquals(TransType.INCOME, draft.direction)
        assertEquals(PaymentPageType.PAYMENT_RESULT, draft.pageType)
    }

    @Test
    fun `微信转账转出完成页按支出入账`() {
        val draft = hit(
            releaseDetector.detect(
                PageSnapshot.ofTexts(
                    PaymentPageRules.WECHAT_PACKAGE,
                    listOf("转账", "¥100.00", "转账成功", "已存入对方零钱", "完成"),
                ),
            ),
        )
        assertEquals(Money(10000), draft.amount)
        assertEquals(TransType.EXPENSE, draft.direction)
        assertEquals(PaymentPageType.PAYMENT_RESULT, draft.pageType)
    }

    @Test
    fun `微信转账收款页按收入入账`() {
        val draft = hit(
            releaseDetector.detect(
                PageSnapshot.ofTexts(
                    PaymentPageRules.WECHAT_PACKAGE,
                    listOf("转账-来自群友-某好友", "+50.00", "已收款", "已存入零钱", "完成"),
                ),
            ),
        )
        assertEquals(Money(5000), draft.amount)
        assertEquals(TransType.INCOME, draft.direction)
        assertEquals(PaymentPageType.PAYMENT_RESULT, draft.pageType)
    }

    @Test
    fun `零钱充值完成页不识别`() {
        // 「充值成功 + 已存入零钱」是账户内部转移，不是收入。
        // 只加「场景词」限定（红包 / 转账）才能挡住它。
        val texts = listOf("零钱", "充值成功", "¥100.00", "已存入零钱", "完成")
        assertEquals(
            MissReason.NOT_PAYMENT_PAGE,
            miss(releaseDetector.detect(PageSnapshot.ofTexts(PaymentPageRules.WECHAT_PACKAGE, texts))),
        )
    }

    @Test
    fun `理财收益到账页不识别`() {
        val texts = listOf("零钱通", "收益已到账", "¥0.52", "昨日收益", "累计收益 ¥12.30")
        assertEquals(
            MissReason.NOT_PAYMENT_PAGE,
            miss(releaseDetector.detect(PageSnapshot.ofTexts(PaymentPageRules.WECHAT_PACKAGE, texts))),
        )
    }

    @Test
    fun `聊天页里别人发的红包截图不识别`() {
        val texts = listOf(
            "群友", "微信红包", "¥128.50", "已存入零钱", "看看我的红包",
            "按住 说话", "表情",
        )
        assertEquals(
            MissReason.CHAT_PAGE,
            miss(releaseDetector.detect(PageSnapshot.ofTexts(PaymentPageRules.WECHAT_PACKAGE, texts))),
        )
    }

    // ---- 账单详情页：发布版与 debug 版行为一致 ----

    @Test
    fun `账单详情页发布版可识别`() {
        val snapshot = PageSnapshot.ofTexts(
            PaymentPageRules.ALIPAY_PACKAGE,
            listOf("账单详情", "-25.50", "地铁", "交易单号 2026101822001"),
        )
        val draft = hit(releaseDetector.detect(snapshot))
        assertEquals(Money(2550), draft.amount)
        assertEquals(PaymentPageType.BILL_DETAIL, draft.pageType)
    }

    @Test
    fun `账单详情页 debug 版也可识别`() {
        val debugDetector = PaymentPageDetector(debugPackageName = "com.afu.ledger")
        val snapshot = PageSnapshot.ofTexts(
            PaymentPageRules.ALIPAY_PACKAGE,
            listOf("账单详情", "-25.50", "地铁", "交易单号 2026101822001"),
        )
        val draft = hit(debugDetector.detect(snapshot))
        assertEquals(Money(2550), draft.amount)
        assertEquals(PaymentPageType.BILL_DETAIL, draft.pageType)
    }

    // ---- 真机事故回归：这些页面都必须被排除 ----

    @Test
    fun `支付宝我的页被排除`() {
        // 真机实测：停在这个页面时弹出过 ¥25.00，商户名取到了广告文案「放心消费在身边」
        val texts = listOf(
            "Blessing",
            "卡包",
            "支付宝会员",
            "账单",
            "总资产",
            "余额",
            "银行卡",
            "最近消息",
            "天猫 付款成功 ¥25.00",
            "¥25.00",
        )
        assertEquals(
            MissReason.MAIN_SCREEN,
            miss(releaseDetector.detect(PageSnapshot.ofTexts(PaymentPageRules.ALIPAY_PACKAGE, texts))),
        )
    }

    @Test
    fun `支付宝首页被排除`() {
        val texts = listOf(
            "上海",
            "公积金查询",
            "扫一扫",
            "收付款",
            "出行",
            "卡包",
            "市民中心",
            "余额宝",
            "最近消息 天猫 付款成功 ¥25.00",
            "¥25.00",
        )
        assertEquals(
            MissReason.MAIN_SCREEN,
            miss(releaseDetector.detect(PageSnapshot.ofTexts(PaymentPageRules.ALIPAY_PACKAGE, texts))),
        )
    }

    @Test
    fun `微信账单列表页被排除`() {
        val texts = listOf(
            "账单",
            "全部账单",
            "查找交易",
            "收支统计",
            "2026年10月 支出¥430.60 收入¥400.80",
            "美团 10月18日 20:10 -23.90",
            "Valve 10月17日 23:50 -42.00",
            "阿佑鲜烫牛肉米线 10月16日 12:09 -26.80",
            "微信红包 10月15日 21:34 +128.50",
        )
        assertEquals(
            MissReason.MAIN_SCREEN,
            miss(releaseDetector.detect(PageSnapshot.ofTexts(PaymentPageRules.WECHAT_PACKAGE, texts))),
        )
    }

    @Test
    fun `微信我页被排除`() {
        val texts = listOf(
            "Blessing",
            "服务",
            "收藏",
            "朋友圈",
            "卡包",
            "表情",
            "设置",
            "天猫 付款成功 ¥25.00",
            "¥25.00",
        )
        assertEquals(
            MissReason.MAIN_SCREEN,
            miss(releaseDetector.detect(PageSnapshot.ofTexts(PaymentPageRules.WECHAT_PACKAGE, texts))),
        )
    }

    @Test
    fun `只有通用词汇不算账单页`() {
        val texts = listOf("交易", "订单", "金额", "实付", "合计", "¥25.50")
        assertEquals(
            MissReason.NOT_PAYMENT_PAGE,
            miss(releaseDetector.detect(PageSnapshot.ofTexts(PaymentPageRules.ALIPAY_PACKAGE, texts))),
        )
    }

    @Test
    fun `账单详情页提取交易单号`() {
        val texts = listOf(
            "账单详情",
            "美团",
            "-23.90",
            "当前状态",
            "支付成功",
            "交易单号",
            "4500000576202610184019086744",
            "商户单号",
            "20261018200955U1883159793807781",
            "支付时间",
            "2026年10月18日20:10:02",
        )
        val draft = hit(releaseDetector.detect(PageSnapshot.ofTexts(PaymentPageRules.WECHAT_PACKAGE, texts)))
        assertEquals("4500000576202610184019086744", draft.tradeNo)
        assertEquals("微信", draft.source)
        assertEquals(Money(2390), draft.amount)
    }

    @Test
    fun `指纹优先用交易单号`() {
        val base = PaymentDraft(
            amount = Money(2390),
            direction = TransType.EXPENSE,
            merchant = "美团",
            sourcePackage = PaymentPageRules.WECHAT_PACKAGE,
            pageType = PaymentPageType.BILL_DETAIL,
            occurredAt = 1_700_000_000_000L,
            occurredAtSource = TimeSource.PAGE,
            capturedAt = 1_700_000_000_000L,
            tradeNo = "4500000576202610184019086744",
        )
        val changed = base.copy(merchant = "美团外卖", amount = Money(2500))
        assertEquals(base.fingerprint(), changed.fingerprint())

        val noTradeNo = base.copy(tradeNo = null)
        assertTrue(noTradeNo.fingerprint().contains("2390"))
    }

    @Test
    fun `debug 版放行自家模拟页面`() {
        val debugDetector = PaymentPageDetector(debugPackageName = "com.afu.ledger")
        val draft = hit(
            debugDetector.detect(
                PageSnapshot.ofTexts(
                    "com.afu.ledger",
                    listOf(
                        "账单详情", "支付成功", "¥25.50", "瑞幸咖啡",
                        "交易单号", "4500000576202610184019086744",
                    ),
                ),
            ),
        )
        assertEquals(Money(2550), draft.amount)
    }

    // ---------- 聊天页误弹（真机事故：打开好友对话弹出金额不对的卡片） ----------

    @Test
    fun `聊天页里的转账卡片不识别`() {
        // 真机：与好友的对话里有一条 27 元转账提示，被当成支付结果页弹了出来，
        // 联系人昵称还成了商户名。现在聊天证据是一级排除，命中即放弃。
        val texts = listOf(
            "好友A",
            "[转账] 微信转账",
            "¥27.00",
            "已收款",
            "转账给 好友A",
        )
        assertEquals(
            MissReason.CHAT_PAGE,
            miss(releaseDetector.detect(PageSnapshot.ofTexts(PaymentPageRules.WECHAT_PACKAGE, texts))),
        )
    }

    @Test
    fun `聊天页输入栏被 OCR 读错空格也拦得住`() {
        val variants = listOf(
            "按住 说话",
            "按住说话",
            "按住 说 话",
        )
        variants.forEach { bar ->
            val texts = listOf("某好友-好友B", "支付成功", "¥9.00", bar)
            assertEquals(
                "输入栏文案「$bar」应判定为聊天页",
                MissReason.CHAT_PAGE,
                miss(releaseDetector.detect(PageSnapshot.ofTexts(PaymentPageRules.WECHAT_PACKAGE, texts))),
            )
        }
    }

    @Test
    fun `聊天页里的红包消息不识别`() {
        val texts = listOf("红包", "微信红包", "[动画表情]", "恭喜发财", "¥8.88", "已存入零钱")
        assertEquals(
            MissReason.CHAT_PAGE,
            miss(releaseDetector.detect(PageSnapshot.ofTexts(PaymentPageRules.WECHAT_PACKAGE, texts))),
        )
    }

    @Test
    fun `转账账单详情页不被聊天证据误杀`() {
        // 「转账给」在真正的转账账单详情页里也会出现，但那里有 ≥2 个强字段，应继续识别
        val draft = hit(
            releaseDetector.detect(
                PageSnapshot.ofTexts(
                    PaymentPageRules.WECHAT_PACKAGE,
                    listOf(
                        "转账详情",
                        "转账给 好友A",
                        "¥27.00",
                        "当前状态", "已收款",
                        "转账单号", "10000500012026102501234567890",
                        "转账时间", "2026年10月25日 14:45:02",
                    ),
                ),
            ),
        )
        assertEquals(Money(2700), draft.amount)
        assertEquals(PaymentPageType.BILL_DETAIL, draft.pageType)
    }

    // ---------- OCR 丢小数点（27.00 → 2700） ----------

    @Test
    fun `结果页金额没有小数点时放弃识别`() {
        // OCR 把 ¥27.00 读成 ¥2700：放大 100 倍的经典形态，宁可漏记也不能记错
        val texts = listOf("支付成功", "¥2700", "完成")
        assertEquals(
            MissReason.AMOUNT_LOST_DECIMAL,
            miss(releaseDetector.detect(PageSnapshot.ofTexts(PaymentPageRules.WECHAT_PACKAGE, texts))),
        )
    }

    @Test
    fun `同页有带小数金额时优先它`() {
        val draft = hit(
            releaseDetector.detect(
                PageSnapshot.ofTexts(
                    PaymentPageRules.WECHAT_PACKAGE,
                    listOf("支付成功", "¥2700", "¥27.00", "完成"),
                ),
            ),
        )
        assertEquals(Money(2700), draft.amount)
    }

    @Test
    fun `正常结果页金额带两位小数不受影响`() {
        val draft = hit(
            releaseDetector.detect(
                PageSnapshot.ofTexts(
                    PaymentPageRules.WECHAT_PACKAGE,
                    listOf("支付成功", "¥25.50", "完成"),
                ),
            ),
        )
        assertEquals(Money(2550), draft.amount)
    }

    @Test
    fun `命中时留下页面摘要`() {
        val draft = hit(
            releaseDetector.detect(
                PageSnapshot.ofTexts(
                    PaymentPageRules.WECHAT_PACKAGE,
                    listOf("账单详情", "支付成功", "¥25.50", "瑞幸咖啡"),
                ),
            ),
        )
        assertTrue("页面摘要不应为空", draft.pageHeadline.isNotBlank())
        assertTrue("摘要不能超过 96 字", draft.pageHeadline.length <= 96)
    }

    // ---------- 字号判据（OCR 行高）：金额必须是全页最大的字 ----------

    private fun snapshotWithHeights(vararg rows: Triple<String, Int, Int>): PageSnapshot =
        PageSnapshot(
            packageName = PaymentPageRules.WECHAT_PACKAGE,
            items = rows.mapIndexed { index, (text, height, _) ->
                PageText(text = text, top = 100 + index * 90, left = 80, height = height)
            },
        )

    @Test
    fun `金额与正文同字号时不当作支付结果页`() {
        // 真机事故的形态：页面里出现了成功状态词与一个金额，但金额和正文一样大 ——
        // 这是聊天正文（或截图里的文字），不是支付结果页的「大字金额」。
        val snapshot = snapshotWithHeights(
            Triple("支付成功", 30, 0),
            Triple("刚才那笔 27.00 你帮我垫的", 30, 0),
            Triple("¥27.00", 30, 0),
            Triple("收到", 30, 0),
        )
        assertEquals(
            MissReason.AMOUNT_NOT_PROMINENT,
            miss(releaseDetector.detect(snapshot)),
        )
    }

    @Test
    fun `金额是最大字时正常当作结果页`() {
        val snapshot = snapshotWithHeights(
            Triple("支付成功", 30, 0),
            Triple("¥25.50", 78, 0),
            Triple("瑞幸咖啡", 30, 0),
            Triple("完成", 30, 0),
        )
        val draft = hit(releaseDetector.detect(snapshot))
        assertEquals(Money(2550), draft.amount)
        assertEquals(PaymentPageType.PAYMENT_RESULT, draft.pageType)
    }

    // ---------- 微信支付公众号会话（真机：27.00 被读成 2700 的来源页面） ----------

    @Test
    fun `微信支付公众号会话里的消费记录不识别`() {
        // 真机页面：会话里堆着多条消费记录，每条都是大字金额 +「账单详情」，
        // 底部是公众号菜单「我的账单 / 支付服务 / 优惠」。
        val texts = listOf(
            "微信支付",
            "13:41",
            "某烧鸭面馆",
            "使用零钱支付",
            "¥27.00",
            "账单详情",
            "我的账单", "支付服务", "优惠",
        )
        assertEquals(
            MissReason.NOT_PAYMENT_PAGE,
            miss(releaseDetector.detect(PageSnapshot.ofTexts(PaymentPageRules.WECHAT_PACKAGE, texts))),
        )
    }

    @Test
    fun `微信支付会话里多笔记录不识别`() {
        val texts = listOf(
            "微信支付", "13:41", "某烧鸭面馆", "使用零钱支付", "¥27.00",
            "14:05", "某生活超市", "使用零钱支付", "¥9.00",
        )
        // 两个不同金额 → 歧义放弃；无论如何都不能弹窗
        assertTrue(
            "不应命中",
            releaseDetector.detect(PageSnapshot.ofTexts(PaymentPageRules.WECHAT_PACKAGE, texts))
                is DetectionResult.Missed,
        )
    }

    @Test
    fun `微信会话列表不弹窗`() {
        // 真机：会话列表里「微信支付 · 已支付¥27.00」那一行提供状态词与金额，
        // 整个列表页被当成支付结果页弹了卡片。列表页每行右侧只有「14:58」这种时分。
        val texts = listOf(
            "某好友-好友A", "15.10分我来", "14:58",
            "公众号", "杭州电子科技大学", "14:45",
            "微信支付", "已支付¥27.00", "14:05",
            "某好友-好友B", "后面打得好", "12:19",
        )
        assertEquals(
            MissReason.MAIN_SCREEN,
            miss(releaseDetector.detect(PageSnapshot.ofTexts(PaymentPageRules.WECHAT_PACKAGE, texts))),
        )
    }

    @Test
    fun `账单详情页只有一行时间不受会话规则影响`() {
        val draft = hit(
            releaseDetector.detect(
                PageSnapshot.ofTexts(
                    PaymentPageRules.WECHAT_PACKAGE,
                    listOf(
                        "账单详情", "支付成功", "¥27.00", "某烧鸭面馆",
                        "支付时间", "2026年10月25日 13:41:02",
                        "交易单号", "4200001234202610251234567890",
                    ),
                ),
            ),
        )
        assertEquals(Money(2700), draft.amount)
    }
}
