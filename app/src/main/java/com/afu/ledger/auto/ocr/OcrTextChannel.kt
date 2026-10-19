package com.afu.ledger.auto.ocr

import android.graphics.Bitmap
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
            /** 截图（含 HW buffer → ARGB 拷贝）耗时。 */
            val captureMillis: Long = 0L,
            /** 纯 OCR 推理耗时（det + rec）。 */
            val ocrMillis: Long = 0L,
            /** 检测阶段耗时。 */
            val detMillis: Long = 0L,
            /** 识别阶段耗时（逐行 / 分档批量）。 */
            val recMillis: Long = 0L,
            /** 预过滤掉的框数量（状态栏 / 长单号）。 */
            val skippedBoxes: Int = 0,
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
        val captureMillis = System.currentTimeMillis() - startedAt

        val ocrStartedAt = System.currentTimeMillis()
        // 只认屏幕上半部分（快筛）。
        //
        // 账单详情页的关键字段全在上半屏：商户名 18–22%、金额 25–30%、状态 37–39%、
        // 支付/收款时间 42–45%、交易单号 48–55%（真机四类样本实测）。
        // 整页识别里有一半时间花在读根本用不到的下半屏，裁掉直接省掉这部分。
        //
        // 裁剪不会裁坏识别：`BillRecognitionRegressionTest` 里有专门一条测试，
        // 把样本的行按同样的高度截断后断言仍然全部识别成功。
        val input = runCatching {
            val height = (image.height * QUICK_SCAN_HEIGHT_RATIO).toInt().coerceAtLeast(1)
            if (height >= image.height) image else Bitmap.createBitmap(image, 0, 0, image.width, height)
        }.getOrDefault(image)

        val lines = try {
            withTimeoutOrNull(timeoutMillis) { engine.recognize(input) }
                ?: return ReadResult.Failed(MissReason.OCR_TIMEOUT, "超过 ${timeoutMillis}ms")
        } finally {
            // 位图所有权在调用方：识别结束立即释放，不落盘、不缓存
            if (input !== image) runCatching { input.recycle() }
            image.recycle()
        }
        val ocrMillis = System.currentTimeMillis() - ocrStartedAt

        if (lines.isEmpty()) return ReadResult.Failed(MissReason.OCR_NO_TEXT)

        val rows = OcrLineBuilder.buildWithBounds(lines)
        if (rows.isEmpty()) return ReadResult.Failed(MissReason.OCR_NO_TEXT, "全部行置信度过低")

        val sanitized = OcrTextSanitizer.sanitizeItems(rows)
        if (sanitized.isEmpty()) return ReadResult.Failed(MissReason.OCR_NO_TEXT)

        when (val verdict = OcrAmountGuard.check(sanitized.map { it.text })) {
            is OcrAmountGuard.Verdict.Rejected ->
                return ReadResult.Failed(MissReason.OCR_SUSPICIOUS_AMOUNT, verdict.reason)
            OcrAmountGuard.Verdict.Ok -> Unit
        }

        return ReadResult.Success(
            snapshot = PageSnapshot(packageName = packageName, items = sanitized),
            ocrElapsedMillis = System.currentTimeMillis() - startedAt,
            lineCount = sanitized.size,
            captureMillis = captureMillis,
            ocrMillis = ocrMillis,
            detMillis = engine.lastDetMillis,
            recMillis = engine.lastRecMillis,
            skippedBoxes = engine.lastSkippedBoxes,
        )
    }

    companion object {
        const val OCR_TIMEOUT_MILLIS: Long = 2_500L

        /**
         * 快筛使用的屏幕高度比例。
         *
         * 0.6 的依据是四类真机样本的字段分布，且 `BillRecognitionRegressionTest`
         * 会按同一比例截断样本验证识别不退化。调小（更快）前先跑那条测试。
         */
        const val QUICK_SCAN_HEIGHT_RATIO: Float = 0.6f
    }
}
