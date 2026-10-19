package com.afu.ledger.auto

import com.afu.ledger.domain.model.TransType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import kotlin.math.abs

/**
 * 真实账单样本的回归测试。
 *
 * 数据来自 `test/bill-samples/`（真机采集），由 `tools/export_fixtures.py` 导出成
 * `app/src/test/resources/bills/` 下的纯文本 fixture。每条样本都是**真的账单页**，
 * 不是构造数据 —— 改识别规则后跑这个测试，就知道规则还能不能认出真账单。
 *
 * fixture 格式：
 * ```
 * # key=value      ← 元信息与期望值
 * # ---
 * top|left|text    ← 一行文本及其屏幕坐标
 * ```
 */
class BillRecognitionRegressionTest {

    /** 固定时区，避免跑测试的机器时区不同导致时间断言飘。 */
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 采集设备（小米 14 Pro）的屏幕高度，与 fixture 里的坐标同一坐标系。 */
    private val SCREEN_HEIGHT = 3200

    private val detector = PaymentPageDetector(debugPackageName = null, zone = zone)

    private data class Fixture(
        val meta: Map<String, String>,
        val items: List<PageText>,
    ) {
        fun meta(key: String): String = this.meta[key].orEmpty()
    }

    @Test
    fun `支付宝账单样本全部识别正确`() {
        var checked = 0
        for (index in 0..24) {
            val path = "bills/alipay/${"%02d".format(index)}.txt"
            val fixture = loadOrNull(path) ?: continue
            checked++

            val snapshot = PageSnapshot("com.eg.android.AlipayGphone", fixture.items)
            val result = detector.detect(snapshot)

            assertTrue(
                "${fixture.meta("id")} 应命中，实际 ${(result as? DetectionResult.Missed)?.reason}",
                result is DetectionResult.Hit,
            )
            val draft = (result as DetectionResult.Hit).draft

            assertEquals(
                "${fixture.meta("id")} 金额",
                fixture.meta("amountCents").toLong(),
                draft.amount.cents,
            )
            assertEquals(
                "${fixture.meta("id")} 方向",
                fixture.meta("direction"),
                draft.direction.name,
            )
            assertEquals(
                "${fixture.meta("id")} 账单时间",
                fixture.meta("occurredAt"),
                BillTimeExtractor.format(draft.occurredAt, zone),
            )
            if (fixture.meta("merchant").isNotEmpty()) {
                assertNotNull("${fixture.meta("id")} 商户不应为空", draft.merchant)
            }
        }
        assertTrue("至少要有 5 条支付宝样本参与回归，实际 $checked", checked >= 5)
    }

    @Test
    fun `非账单页一律不命中`() {
        val names = listOf("alipay_home", "alipay_mine")
        var checked = 0
        for (name in names) {
            val fixture = loadOrNull("bills/negative/$name.txt") ?: continue
            checked++
            val packageName = when {
                name.startsWith("alipay") -> "com.eg.android.AlipayGphone"
                else -> "com.tencent.mm"
            }
            val result = detector.detect(PageSnapshot(packageName, fixture.items))
            assertTrue(
                "$name 不应命中，实际 ${(result as? DetectionResult.Hit)?.draft}",
                result is DetectionResult.Missed,
            )
        }
        assertTrue("至少要有 1 条负样本", checked >= 1)
    }

    /**
     * 账单时间必须来自页面，不能等于运行时刻（也就是「昨天账单记在今天」的回归防线）。
     */
    @Test
    fun `账单时间不等于运行时刻`() {
        val path = "bills/alipay/00.txt"
        val fixture = loadOrNull(path) ?: return

        val result = detector.detect(
            PageSnapshot("com.eg.android.AlipayGphone", fixture.items),
        )
        val draft = (result as DetectionResult.Hit).draft
        val now = System.currentTimeMillis()

        assertTrue(
            "账单时间应远早于现在，实际相差 ${(now - draft.occurredAt) / 1000}s",
            abs(now - draft.occurredAt) > 60_000L,
        )
        assertEquals(TimeSource.PAGE, draft.occurredAtSource)
    }

    /** 商户名要从页面取到具体的那一个，而不是随便抓一段相邻文本。 */
    @Test
    fun `支付宝旧版页取到正确商户`() {
        val fixture = loadOrNull("bills/alipay/00.txt") ?: return

        val result = detector.detect(
            PageSnapshot("com.eg.android.AlipayGphone", fixture.items),
        )
        val draft = (result as DetectionResult.Hit).draft
        assertEquals("森众餐饮", draft.merchant)
    }

    /** 「支出25元」这种口语化金额必须解析成 2500 分且方向为支出。 */
    @Test
    fun `中文方向前缀金额能解析`() {
        val fixture = loadOrNull("bills/alipay/02.txt") ?: return

        val result = detector.detect(
            PageSnapshot("com.eg.android.AlipayGphone", fixture.items),
        )
        val draft = (result as DetectionResult.Hit).draft
        assertEquals(2974L, draft.amount.cents)
        assertEquals(TransType.EXPENSE, draft.direction)
    }

    /** 订单金额与立减金不能把真实金额拖成「金额歧义」。 */
    @Test
    fun `多金额页面仍能选中真实交易金额`() {
        val fixture = loadOrNull("bills/alipay/04.txt") ?: return

        val result = detector.detect(
            PageSnapshot("com.eg.android.AlipayGphone", fixture.items),
        )
        assertTrue(
            "多金额页应命中，实际 ${(result as? DetectionResult.Missed)?.reason}",
            result is DetectionResult.Hit,
        )
        assertEquals(1749978L, (result as DetectionResult.Hit).draft.amount.cents)
    }

    /** 退款页没有方向前缀，靠「退款成功」判成收入。 */
    @Test
    fun `退款页判为收入`() {
        val fixture = loadOrNull("bills/alipay/06.txt") ?: return

        val result = detector.detect(
            PageSnapshot("com.eg.android.AlipayGphone", fixture.items),
        )
        val draft = (result as DetectionResult.Hit).draft
        assertEquals(10000L, draft.amount.cents)
        assertEquals(TransType.INCOME, draft.direction)
    }

    /**
     * 快筛覆盖率：只保留屏幕上 60% 的文本行，账单样本是否仍然全部识别成功。
     *
     * 微信通道要把「整页 OCR」改成「先认上半屏」，因为关键字段（商户、金额、状态、时间）
     * 全在上半屏。这条测试保证裁剪不会把识别裁坏 —— 如果哪天有样本只有下半屏才有证据，
     * 它会立刻失败，提醒调整裁剪比例而不是硬推。
     */
    @Test
    fun `上六成屏幕足以识别所有账单`() {
        val cut = (SCREEN_HEIGHT * 0.6).toInt()
        var checked = 0
        for (index in 0..24) {
            val path = "bills/alipay/${"%02d".format(index)}.txt"
            val fixture = loadOrNull(path) ?: continue
            val top = fixture.items.filter { it.top < cut }
            if (top.size < 3) continue
            checked++

            val result = detector.detect(
                PageSnapshot("com.eg.android.AlipayGphone", top),
            )
            assertTrue(
                "${fixture.meta("id")} 只用上半屏也应命中，" +
                    "实际 ${(result as? DetectionResult.Missed)?.reason}",
                result is DetectionResult.Hit,
            )
            val draft = (result as DetectionResult.Hit).draft
            assertEquals(
                "${fixture.meta("id")} 上半屏金额",
                fixture.meta("amountCents").toLong(),
                draft.amount.cents,
            )
            assertEquals(
                "${fixture.meta("id")} 上半屏账单时间",
                fixture.meta("occurredAt"),
                BillTimeExtractor.format(draft.occurredAt, zone),
            )
        }
        assertTrue("至少要有 5 条样本参与快筛验证，实际 $checked", checked >= 5)
    }

    /**
     * 微信外币支付页：真实金额 `-42.00` 下面紧贴「6.24美元 / 标价 / 汇率」。
     *
     * 真机踩过：按「邻行含价格词就排除」的规则，真实金额被自己下面的「标价」排掉，
     * 整页 `NO_AMOUNT`，外币账单永远记不上。
     */
    @Test
    fun `外币页不能把真实金额排除掉`() {
        val lines = listOf(
            "X",
            "Valve",
            "-42.00",
            "6.24美元",
            "标价",
            "汇率",
            "1美元=6.726131人民币",
            "当前状态",
            "支付成功",
            "支付时间",
            "2026年10月17日23:50:26",
            "商品",
            "Steam Purchase 1710000222169061",
        )
        val result = detector.detect(PageSnapshot.ofTexts("com.tencent.mm", lines))
        assertTrue(
            "外币页应命中，实际 ${(result as? DetectionResult.Missed)?.reason}",
            result is DetectionResult.Hit,
        )
        val draft = (result as DetectionResult.Hit).draft
        assertEquals(4200L, draft.amount.cents)
        assertEquals(TransType.EXPENSE, draft.direction)
    }

    /** 负号被 OCR 读成汉字「一」时仍要认出金额（真机实测微信账单页）。 */
    @Test
    fun `负号读成汉字一仍能识别`() {
        val snapshot = PageSnapshot.ofTexts(
            "com.tencent.mm",
            listOf(
                "X",
                "美团",
                "一23.90",
                "当前状态",
                "支付成功",
                "支付时间",
                "2026年10月18日20:10:02",
                "交易单号",
                "4500000576202610184019086744",
            ),
        )
        // OCR 通道会先过 sanitizer；这里模拟同样的清洗
        val sanitized = PageSnapshot(
            packageName = snapshot.packageName,
            items = com.afu.ledger.auto.ocr.OcrTextSanitizer.sanitizeItems(snapshot.items),
        )
        val result = detector.detect(sanitized)
        assertTrue(
            "应命中，实际 ${(result as? DetectionResult.Missed)?.reason}",
            result is DetectionResult.Hit,
        )
        assertEquals(2390L, (result as DetectionResult.Hit).draft.amount.cents)
    }

    /**
     * 微信红包收款页的真实 OCR 输出（真机日志原样）。
     *
     * 两个坑都在这里：
     * 1. OCR 把「微信红包-来自群友-江浩(DJ)」和「+50.00」合进同一个视觉行；
     * 2. 「收款时间」的**值在上面一行**、「交易单号」在它下面。
     */
    @Test
    fun `微信红包收款页能识别为收入`() {
        val lines = listOf(
            "X",
            "微信红包-来自群友-江浩(DJ) +50.00",
            "当前状态",
            "己存入零钱",
            "红包详情",
            "查看",
            "2026年8月30日19:33:55",
            "收款时间",
            "交易单号",
            "1000039901000608306305517330057",
            "商戶单号",
            "1000039901202608306305517330057",
        )
        val result = detector.detect(PageSnapshot.ofTexts("com.tencent.mm", lines))
        assertTrue(
            "红包页应命中，实际 ${(result as? DetectionResult.Missed)?.reason}",
            result is DetectionResult.Hit,
        )
        val draft = (result as DetectionResult.Hit).draft
        assertEquals(5000L, draft.amount.cents)
        assertEquals(TransType.INCOME, draft.direction)
        assertEquals(
            "2026-08-30T19:33:55",
            BillTimeExtractor.format(draft.occurredAt, zone),
        )
        assertEquals("1000039901000608306305517330057", draft.tradeNo)
    }

    /**
     * 微信账单样本（文本行来自真机上 App 自己的 OCR 输出）。
     *
     * 微信账单页对无障碍完全不可读，只能靠截图 OCR，所以这批 fixture 的
     * `sourceKind` 是 `OCR`，文本是 logcat 里 `line[N]` 的原样搬运。
     */
    @Test
    fun `微信账单样本识别结果与期望一致`() {
        var checked = 0
        for (index in 1..7) {
            val path = "bills/wechat/${"%02d".format(index)}.txt"
            val fixture = loadOrNull(path) ?: continue
            checked++

            val result = detector.detect(PageSnapshot("com.tencent.mm", fixture.items))
            val expectMatched = fixture.meta("matched").equals("True", ignoreCase = true)

            if (!expectMatched) {
                assertTrue(
                    "${fixture.meta("id")} 应放弃识别，" +
                        "实际 ${(result as? DetectionResult.Hit)?.draft}",
                    result is DetectionResult.Missed,
                )
                continue
            }

            assertTrue(
                "${fixture.meta("id")} 应命中，实际 ${(result as? DetectionResult.Missed)?.reason}",
                result is DetectionResult.Hit,
            )
            val draft = (result as DetectionResult.Hit).draft
            assertEquals(
                "${fixture.meta("id")} 金额",
                fixture.meta("amountCents").toLong(),
                draft.amount.cents,
            )
            assertEquals(
                "${fixture.meta("id")} 方向",
                fixture.meta("direction"),
                draft.direction.name,
            )
            assertEquals(
                "${fixture.meta("id")} 账单时间",
                fixture.meta("occurredAt"),
                BillTimeExtractor.format(draft.occurredAt, zone),
            )
            if (fixture.meta("tradeNo").isNotEmpty()) {
                assertEquals(
                    "${fixture.meta("id")} 单号",
                    fixture.meta("tradeNo"),
                    draft.tradeNo,
                )
            }
        }
        assertTrue("至少要有 6 条微信样本参与回归，实际 $checked", checked >= 6)
    }

    private fun loadOrNull(path: String): Fixture? {
        val stream = javaClass.classLoader?.getResourceAsStream(path) ?: return null
        return stream.bufferedReader().use { reader ->
            val meta = LinkedHashMap<String, String>()
            val items = ArrayList<PageText>()
            reader.forEachLine { raw ->
                val line = raw.trimEnd()
                when {
                    line.isBlank() -> Unit
                    line.startsWith("# ---") -> Unit
                    line.startsWith("#") -> {
                        val body = line.removePrefix("#").trim()
                        val index = body.indexOf('=')
                        if (index > 0) {
                            meta[body.substring(0, index)] = body.substring(index + 1).trim()
                        }
                    }
                    else -> {
                        val parts = line.split('|', limit = 3)
                        if (parts.size == 3) {
                            items.add(
                                PageText(
                                    text = parts[2],
                                    top = parts[0].toIntOrNull() ?: PageText.NO_POSITION,
                                    left = parts[1].toIntOrNull() ?: PageText.NO_POSITION,
                                ),
                            )
                        }
                    }
                }
            }
            Fixture(meta, items)
        }
    }
}
