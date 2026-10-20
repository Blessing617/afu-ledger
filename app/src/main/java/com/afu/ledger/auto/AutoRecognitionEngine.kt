package com.afu.ledger.auto

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityWindowInfo
import com.afu.ledger.BuildConfig
import com.afu.ledger.auto.ocr.OcrTextChannel
import com.afu.ledger.auto.ocr.ScreenshotThrottle

/**
 * 识别编排：决定这次用哪条文本来源，并把结果交给 [PaymentPageDetector]。
 *
 * 从 `AutoLedgerAccessibilityService` 里抽出来，原因有二：
 * 1. 服务文件已经顶到 300 行红线，再塞 OCR 编排必然失控；
 * 2. 采集与判定是可测逻辑，不该和生命周期、悬浮窗纠缠在一起。
 *
 * 两条来源对后续链路完全等价，产出的都是 [PageSnapshot]。
 */
class AutoRecognitionEngine(
    private val service: AccessibilityService,
    private val detector: PaymentPageDetector,
    private val ocrChannel: OcrTextChannel?,
    /** debug 构建传自己的包名，让内置模拟页也能走 OCR 通道；发布版传 null。 */
    private val ocrDebugPackageName: String? = null,
) {

    sealed interface EngineResult {

        data class Hit(val draft: PaymentDraft) : EngineResult

        data class Missed(val reason: MissReason, val detail: String? = null) : EngineResult

        /** 被节流跳过。高频且无信息量，不写日志。 */
        data object Skipped : EngineResult
    }

    /** OCR 是否真的可用（系统版本、模型、初始化都通过）。 */
    val ocrAvailable: Boolean
        get() = ocrChannel?.isSupported == true

    /**
     * 识别当前页面。
     *
     * @param kind 本次事件类型，决定截图节流的间隔档位
     */
    suspend fun detect(
        packageName: String,
        kind: ScreenshotThrottle.EventKind,
        screenInteractive: Boolean,
        overlayShowing: Boolean,
        ocrAllowed: Boolean,
        pageChanged: Boolean = false,
    ): EngineResult {
        val startedAt = System.currentTimeMillis()
        val nodeSnapshot = collectNodeSnapshot(packageName)
        val nodeMillis = System.currentTimeMillis() - startedAt

        val channel = ocrChannel
        val canUseOcr = ocrAllowed && channel != null && shouldUseOcr(packageName)

        // 节点文本**足够丰富**时先采信节点：它更便宜、更准，也没有截图耗电问题。
        //
        // 这里踩过一个真实的坑：原先只要「节点文本有 >=3 行」就直接用节点、不再看 OCR。
        // 而支付宝的账单 / 余额明细类页面是 H5，**内容区根本不暴露节点**，
        // 无障碍树里只有「返回」「余额明细详情」这几个导航文字 —— 正好凑够 3 行，
        // 于是被判成「节点够用」，OCR 被短路，金额永远读不到（`miss NO_AMOUNT`）。
        // 所以除了提高「够用」的门槛，还要在节点路径失败时给 OCR 一次兜底机会。
        if (isNodeTextRich(nodeSnapshot)) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "node texts (${nodeSnapshot.texts.size}):")
                nodeSnapshot.texts.forEach { t -> Log.d(TAG, "  node: ${t.take(45)}") }
            }
            val result = toEngineResult(detector.detect(nodeSnapshot), ocrChannelUsed = false)
            if (result is EngineResult.Hit || !canUseOcr) {
                lastTiming = Timing(nodeMillis = nodeMillis, totalMillis = System.currentTimeMillis() - startedAt)
                return result
            }
            debugLog("node path missed (${(result as EngineResult.Missed).reason.name}), fallback to ocr")
        }

        if (!canUseOcr) {
            // 读不到节点、又不该走 OCR：保持「静默跳过」行为
            lastTiming = Timing(nodeMillis = nodeMillis, totalMillis = System.currentTimeMillis() - startedAt)
            return EngineResult.Missed(MissReason.NOT_PAYMENT_PAGE)
        }

        return when (
            val read = channel!!.read(
                packageName = packageName,
                kind = kind,
                screenInteractive = screenInteractive,
                overlayShowing = overlayShowing,
                pageChanged = pageChanged,
            )
        ) {
            is OcrTextChannel.ReadResult.Success -> {
                debugLog(
                    "ocr lines=${read.lineCount} in ${read.ocrElapsedMillis}ms " +
                        "(capture=${read.captureMillis} det=${read.detMillis} rec=${read.recMillis} " +
                        "skipped=${read.skippedBoxes})",
                )
                if (BuildConfig.DEBUG) {
                    read.snapshot.texts.forEachIndexed { i, t ->
                        Log.d("AutoLedger", "  line[$i] ${t.take(40)}")
                    }
                }
                val result = toEngineResult(detector.detect(read.snapshot), ocrChannelUsed = true)
                lastTiming = Timing(
                    nodeMillis = nodeMillis,
                    captureMillis = read.captureMillis,
                    ocrMillis = read.ocrMillis,
                    detMillis = read.detMillis,
                    recMillis = read.recMillis,
                    lineCount = read.lineCount,
                    totalMillis = System.currentTimeMillis() - startedAt,
                )
                result
            }

            is OcrTextChannel.ReadResult.Failed -> {
                lastTiming = Timing(nodeMillis = nodeMillis, totalMillis = System.currentTimeMillis() - startedAt)
                EngineResult.Missed(read.reason, read.detail)
            }

            OcrTextChannel.ReadResult.Skipped -> {
                lastTiming = null
                EngineResult.Skipped
            }
        }
    }

    /** 最近一次识别的分段耗时。仅用于排查与日志，不在关闭日志时持久化。 */
    data class Timing(
        /** 无障碍节点采集。 */
        val nodeMillis: Long = 0L,
        /** 截图（含 HW buffer → ARGB 拷贝）。 */
        val captureMillis: Long = 0L,
        /** OCR 推理（det + rec）。 */
        val ocrMillis: Long = 0L,
        /** 检测阶段。 */
        val detMillis: Long = 0L,
        /** 识别阶段。 */
        val recMillis: Long = 0L,
        /** 识别出的文本行数 —— 行数直接决定 rec 的开销。 */
        val lineCount: Int = 0,
        /** 本次 detect 的总耗时。 */
        val totalMillis: Long = 0L,
    ) {
        /** 一行可读的分段摘要，写进识别日志的 detail。 */
        fun summary(): String = buildString {
            append("节点 ${nodeMillis}ms")
            if (captureMillis > 0) append(" · 截图 ${captureMillis}ms")
            if (ocrMillis > 0) {
                append(" · OCR ${ocrMillis}ms")
                if (detMillis >= 0) append("(检测 ${detMillis} / 识别 ${recMillis}")
                if (lineCount > 0) append(" · ${lineCount}行")
                if (detMillis >= 0) append(")")
            }
            append(" · 合计 ${totalMillis}ms")
        }
    }

    @Volatile
    var lastTiming: Timing? = null
        private set

    /** 上报一次最终结果，驱动截图退避。 */
    fun reportOutcome(hit: Boolean) {
        ocrChannel?.recordOutcome(hit)
    }

    fun resetThrottle() {
        ocrChannel?.resetThrottle()
    }

    fun throttleDecision(): ScreenshotThrottle.Decision? = ocrChannel?.currentDecision()

    private fun toEngineResult(result: DetectionResult, ocrChannelUsed: Boolean): EngineResult =
        when (result) {
            is DetectionResult.Hit -> {
                ocrChannel?.recordOutcome(true)
                EngineResult.Hit(result.draft)
            }
            is DetectionResult.Missed -> {
                if (ocrChannelUsed) {
                    // 页面文本来自 OCR 却没命中，说明这次截图没价值，计入退避
                    ocrChannel?.recordOutcome(false)
                }
                EngineResult.Missed(result.reason)
            }
        }

    /**
     * 节点文本是否**足够丰富**，可以直接采信。
     *
     * 判据从「行数够」改为「行数够 **且** 内容够」。
     *
     * 只数行数会误判：支付宝账单类 H5 页面只暴露「返回」「余额明细详情」这类导航文字，
     * 正好 3 行、却没有任何金额与交易信息，会被当成「节点够用」而短路掉 OCR。
     * 要求同时满足字符数下限，才能把这种「有 3 行但没内容」的情况识别出来。
     */
    private fun isNodeTextRich(snapshot: PageSnapshot): Boolean {
        if (snapshot.texts.size < MIN_USEFUL_NODE_LINES) return false
        return snapshot.texts.sumOf { it.length } >= MIN_USEFUL_NODE_CHARS
    }

    /**
     * 这个包在**节点不可靠时**是否允许走截图 OCR。
     *
     * 曾经这里写着「支付宝永不截图」，理由是「支付宝节点可读，省电」。
     * 真实页面实测证明这个假设是错的：支付宝的账单详情、余额明细详情这类页面
     * 是 H5，**内容区不暴露无障碍节点**，整个树里只有「返回」「余额明细详情」。
     * 于是这些页面既读不到节点、又不许截图，等于永远识别不了。
     *
     * 现在改成按「页面是否有可靠节点」来判断，而不是按包名：
     * 调用方只会在 [isNodeTextRich] 为 false 时才走到这里，
     * 所以支付宝的支付成功页（节点丰富）仍然不截图，只在 H5 详情页才截图。
     */
    private fun shouldUseOcr(packageName: String): Boolean = when {
        packageName in PaymentPageRules.supportedPackages -> true
        ocrDebugPackageName != null && packageName == ocrDebugPackageName -> true
        else -> false
    }

    private fun collectNodeSnapshot(packageName: String): PageSnapshot {
        val seen = LinkedHashSet<String>()
        val items = ArrayList<PageText>(128)

        fun addAll(collected: List<PageText>) {
            collected.forEach { item ->
                // 同一段文本在多个窗口里重复出现时只留第一份，避免「支付时间」出现两次
                if (seen.add("${item.top}|${item.left}|${item.text}")) items.add(item)
            }
        }

        service.rootInActiveWindow?.let { addAll(NodeTextCollector.collectWithBounds(it)) }

        service.windows?.forEach { window ->
            // 别把自己的确认卡片也读进来，否则「自动记账」「账户」「分类」会污染解析
            if (window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) return@forEach
            val windowRoot = runCatching { window.root }.getOrNull() ?: return@forEach
            if (windowRoot.packageName?.toString() != packageName) return@forEach
            addAll(NodeTextCollector.collectWithBounds(windowRoot))
        }

        return PageSnapshot(
            packageName = packageName,
            items = items.sortedWith(compareBy({ it.top }, { it.left })),
        )
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    private companion object {
        const val TAG = "AutoLedger"
    }
}

/**
 * 「节点文本够不够用」的阈值。
 *
 * 提到文件级并标 internal，是为了让单测引用同一份常量：
 * 如果这些数字被改动（例如放宽到「有 1 行就算够用」），
 * 支付宝这类页面就会从节点通道退化成整页 OCR，而测试能立刻失败。
 */
internal const val MIN_USEFUL_NODE_LINES = 3
internal const val MIN_USEFUL_NODE_CHARS = 20
