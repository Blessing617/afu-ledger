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
    ): EngineResult {
        val nodeSnapshot = collectNodeSnapshot(packageName)

        // 无障碍文本够用时一律走节点：它更便宜、更准，也没有截图耗电问题
        if (hasUsableNodeText(nodeSnapshot)) {
            return toEngineResult(detector.detect(nodeSnapshot), ocrChannelUsed = false)
        }

        val channel = ocrChannel
        if (!ocrAllowed || channel == null || !shouldUseOcr(packageName)) {
            // 读不到节点、又不该走 OCR：保持原来的「静默跳过」行为
            return EngineResult.Missed(MissReason.NOT_PAYMENT_PAGE)
        }

        return when (
            val read = channel.read(
                packageName = packageName,
                kind = kind,
                screenInteractive = screenInteractive,
                overlayShowing = overlayShowing,
            )
        ) {
            is OcrTextChannel.ReadResult.Success -> {
                debugLog("ocr lines=${read.lineCount} in ${read.ocrElapsedMillis}ms")
                toEngineResult(detector.detect(read.snapshot), ocrChannelUsed = true)
            }

            is OcrTextChannel.ReadResult.Failed ->
                EngineResult.Missed(read.reason, read.detail)

            OcrTextChannel.ReadResult.Skipped -> EngineResult.Skipped
        }
    }

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
     * 只有「节点文本几乎为空」时才值得截图。
     *
     * 微信实测是 0 个文本节点；给一点余量是为了兼容偶尔出现的 1–2 个装饰性节点。
     */
    private fun hasUsableNodeText(snapshot: PageSnapshot): Boolean {
        if (snapshot.texts.size >= MIN_USEFUL_NODE_LINES) return true
        return snapshot.texts.sumOf { it.length } >= MIN_USEFUL_NODE_CHARS
    }

    private fun shouldUseOcr(packageName: String): Boolean = when {
        packageName == PaymentPageRules.WECHAT_PACKAGE -> true
        ocrDebugPackageName != null && packageName == ocrDebugPackageName -> true
        // 支付宝节点可读，永不截图（省电，也避免规则分叉）
        else -> false
    }

    private fun collectNodeSnapshot(packageName: String): PageSnapshot {
        val texts = LinkedHashSet<String>()
        service.rootInActiveWindow?.let { texts.addAll(NodeTextCollector.collect(it)) }

        service.windows?.forEach { window ->
            // 别把自己的确认卡片也读进来，否则「自动记账」「账户」「分类」会污染解析
            if (window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) return@forEach
            val windowRoot = runCatching { window.root }.getOrNull() ?: return@forEach
            if (windowRoot.packageName?.toString() != packageName) return@forEach
            texts.addAll(NodeTextCollector.collect(windowRoot))
        }

        return PageSnapshot(packageName, texts.toList())
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    private companion object {
        const val TAG = "AutoLedger"
        const val MIN_USEFUL_NODE_LINES = 3
        const val MIN_USEFUL_NODE_CHARS = 12
    }
}
