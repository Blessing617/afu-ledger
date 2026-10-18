package com.afu.ledger.auto.ocr

import com.afu.ledger.auto.MissReason
import com.afu.ledger.auto.PageSnapshot
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 截图 OCR 通道：节流 → 截图 → 识别 → 行整理 → 纠错 → 金额闸门 → [PageSnapshot]。
 *
 * 产出的 [PageSnapshot] 与无障碍采集出的完全同形态，所以后面的页面判定、金额解析、
 * 商户提取、去重、悬浮窗、入账全都能原样复用。
 */
class OcrTextChannel(
    private val capturer: ScreenshotCapturer,
    private val engine: OcrEngine,
    private val throttle: ScreenshotThrottle,
    private val timeoutMillis: Long = OCR_TIMEOUT_MILLIS,
) {

    sealed interface ReadResult {

        data class Success(
            val snapshot: PageSnapshot,
            val ocrElapsedMillis: Long,
            val lineCount: Int,
        ) : ReadResult

        data class Failed(val reason: MissReason, val detail: String? = null) : ReadResult

        /** 被节流拦下，不属于失败，不该写日志。 */
        data object Skipped : ReadResult
    }

    val isSupported: Boolean get() = capturer.isSupported

    /** 记录判定结果，供退避使用。 */
    fun recordOutcome(hit: Boolean) = throttle.recordResult(hit)

    fun resetThrottle() = throttle.reset()

    fun currentDecision(): ScreenshotThrottle.Decision = throttle.lastDecision

    suspend fun read(
        packageName: String,
        kind: ScreenshotThrottle.EventKind,
        screenInteractive: Boolean,
        overlayShowing: Boolean,
        pageChanged: Boolean = false,
    ): ReadResult {
        if (!capturer.isSupported) {
            return ReadResult.Failed(MissReason.OCR_UNAVAILABLE, "系统版本不支持截图")
        }
        if (!throttle.shouldCapture(kind, screenInteractive, overlayShowing, pageChanged)) {
            return ReadResult.Skipped
        }

        val startedAt = System.currentTimeMillis()
        val captured = capturer.capture()
        val image = when (captured) {
            is ScreenshotCapturer.CaptureResult.Success -> captured.image
            is ScreenshotCapturer.CaptureResult.Failure ->
                return ReadResult.Failed(MissReason.SCREENSHOT_FAILED, captured.reason)
            ScreenshotCapturer.CaptureResult.Unsupported ->
                return ReadResult.Failed(MissReason.OCR_UNAVAILABLE, "系统版本不支持截图")
        }

        val lines = withTimeoutOrNull(timeoutMillis) { engine.recognize(image) }
            ?: return ReadResult.Failed(MissReason.OCR_TIMEOUT, "超过 ${timeoutMillis}ms")

        if (lines.isEmpty()) return ReadResult.Failed(MissReason.OCR_NO_TEXT)

        val texts = OcrLineBuilder.build(lines)
        if (texts.isEmpty()) return ReadResult.Failed(MissReason.OCR_NO_TEXT, "全部行置信度过低")

        val sanitized = OcrTextSanitizer.sanitizeAll(texts)

        when (val verdict = OcrAmountGuard.check(sanitized)) {
            is OcrAmountGuard.Verdict.Rejected ->
                return ReadResult.Failed(MissReason.OCR_SUSPICIOUS_AMOUNT, verdict.reason)
            OcrAmountGuard.Verdict.Ok -> Unit
        }

        return ReadResult.Success(
            snapshot = PageSnapshot(packageName = packageName, texts = sanitized),
            ocrElapsedMillis = System.currentTimeMillis() - startedAt,
            lineCount = sanitized.size,
        )
    }

    companion object {
        const val OCR_TIMEOUT_MILLIS: Long = 2_500L
    }
}
