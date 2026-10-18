package com.afu.ledger.auto.ocr

/**
 * 待识别的位图像素。
 *
 * 刻意不用 `android.graphics.Bitmap`：OCR 的预处理、后处理、解码都在纯 Kotlin 里完成，
 * 才能在 JVM 上单测，也让引擎实现不必绑定 Android 框架。
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

    /** 识别一张图。失败或不可用时返回空列表，绝不抛出到调用方。 */
    suspend fun recognize(image: OcrImage): List<OcrLine>

    /** 预加载模型，避免首次识别吃冷启动损耗。调用可重复。 */
    suspend fun warmUp(): Boolean

    fun close()
}
