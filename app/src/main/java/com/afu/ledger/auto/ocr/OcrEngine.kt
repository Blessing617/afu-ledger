package com.afu.ledger.auto.ocr

import android.graphics.Bitmap

/**
 * 待识别的位图像素。
 *
 * 用于**纯逻辑**阶段（裁切、后处理），刻意不依赖 `android.graphics.Bitmap`，
 * 才能在 JVM 上单测。真正的缩放由 [BitmapScaler] 在 native 侧完成。
 */
class OcrImage(
    val width: Int,
    val height: Int,
    val argb: IntArray,
) {
    init {
        require(argb.size == width * height) {
            "argb 长度 ${argb.size} 与 ${width}x$height 不匹配"
        }
    }
}

/** 一行 OCR 结果。坐标是原图坐标系，供 [OcrLineBuilder] 排序与合并。 */
data class OcrLine(
    val text: String,
    val confidence: Float,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val centerY: Int get() = (top + bottom) / 2

    val height: Int get() = (bottom - top).coerceAtLeast(1)
}

/** 离线 OCR 引擎。实现必须在后台线程执行，不得阻塞调用方的事件回调。 */
interface OcrEngine {

    /**
     * 识别一张图。失败或不可用时返回空列表，绝不抛出到调用方。
     *
     * 直接接收 [Bitmap]：缩放要交给系统能力，先转 IntArray 再转回 Bitmap 是纯浪费。
     * 实现 MUST NOT 回收传入的位图，所有权归调用方。
     */
    suspend fun recognize(bitmap: Bitmap): List<OcrLine>

    /** 预加载模型，避免首次识别吃冷启动损耗。调用可重复。 */
    suspend fun warmUp(): Boolean

    fun close()
}
