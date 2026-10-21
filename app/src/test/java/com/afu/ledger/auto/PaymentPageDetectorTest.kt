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
            MissReason.NOT_PAYMENT_PAGE,
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
    fun `只有成功字样和金额的页面仍不识别`() {
        // 结果页参与识别，但**正向准入没有放宽**：页面必须自己证明它是账单页/结果页
        // （有账单标题，或凑够强字段）。只有一句「付款成功 + 金额」的页面依然不弹窗。
        assertEquals(
            MissReason.NOT_PAYMENT_PAGE,
            miss(
                releaseDetector.detect(
                    PageSnapshot.ofTexts(
                        PaymentPageRules.ALIPAY_PACKAGE,
                        listOf("付款成功", "¥25.50", "瑞幸咖啡", "完成"),
                    ),
                ),
            ),
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
}
