package com.afu.ledger.auto.ocr

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.Display
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/**
 * 用无障碍服务的截图能力抓一张当前屏幕。
 *
 * 需要 `android:canTakeScreenshot="true"`（已声明）与 API 30+。
 *
 * 截图结果直接以 **ARGB_8888 的 [Bitmap]** 交出，不再先转成 IntArray 再由引擎转回 Bitmap：
 * 缩放要交给系统（Skia native），中间多一次全屏像素拷贝纯属浪费。
 * 调用方用完必须 [Bitmap.recycle]。
 */
class ScreenshotCapturer(
    private val service: AccessibilityService,
    private val executor: Executor,
) {

    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    sealed interface CaptureResult {

        data class Success(val image: Bitmap, val elapsedMillis: Long) : CaptureResult

        /** 截图失败。[reason] 直接进识别日志。 */
        data class Failure(val reason: String) : CaptureResult

        /** 系统版本不支持截图。 */
        data object Unsupported : CaptureResult
    }

    suspend fun capture(): CaptureResult {
        if (!isSupported) return CaptureResult.Unsupported
        val startedAt = System.currentTimeMillis()

        return suspendCancellableCoroutine { continuation ->
            val callback = object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    val image = runCatching { toSoftwareBitmap(screenshot) }
                        .onFailure { Log.w(TAG, "截图转换失败：${it.message}") }
                        .getOrNull()
                    if (!continuation.isActive) {
                        image?.recycle()
                        return
                    }
                    continuation.resume(
                        if (image == null) {
                            CaptureResult.Failure("截图转换失败")
                        } else {
                            CaptureResult.Success(image, System.currentTimeMillis() - startedAt)
                        },
                    )
                }

                override fun onFailure(errorCode: Int) {
                    if (continuation.isActive) {
                        continuation.resume(CaptureResult.Failure(describeError(errorCode)))
                    }
                }
            }

            runCatching {
                service.takeScreenshot(Display.DEFAULT_DISPLAY, executor, callback)
            }.onFailure { error ->
                if (continuation.isActive) {
                    continuation.resume(CaptureResult.Failure("截图调用异常：${error.message}"))
                }
            }
        }
    }

    /**
     * HardwareBuffer → 软件位图。
     *
     * `wrapHardwareBuffer` 给的是 HARDWARE 配置，读不了像素也不能缩放，
     * 必须拷成 ARGB_8888。这一步是全屏拷贝，无法避免，但比「再逐像素缩放到 det 尺寸」便宜得多。
     */
    private fun toSoftwareBitmap(result: AccessibilityService.ScreenshotResult): Bitmap? {
        val buffer = result.hardwareBuffer
        try {
            val hardware = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace) ?: return null
            val soft = runCatching {
                hardware.copy(Bitmap.Config.ARGB_8888, false)
            }.getOrNull()
            hardware.recycle()
            if (soft == null) return null
            if (soft.width <= 0 || soft.height <= 0) {
                soft.recycle()
                return null
            }
            return soft
        } finally {
            runCatching { buffer.close() }
        }
    }

    private fun describeError(errorCode: Int): String = when (errorCode) {
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "距上次截图太近"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> "无截图权限"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> "无效显示器"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR -> "系统截图内部错误"
        else -> "截图失败（code=$errorCode）"
    }

    private companion object {
        const val TAG = "AfuOcr"
    }
}
