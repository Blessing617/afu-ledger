package com.afu.ledger.auto.ocr

import android.graphics.Bitmap

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

    /**
     * 上一次识别的分段耗时，供日志定位瓶颈。
     *
     * 默认给 -1（表示实现不提供），这样纯逻辑的测试替身不必实现它。
     * 真实引擎会覆盖这两个值：只看总耗时没法判断该优化检测还是识别。
     */
    val lastDetMillis: Long get() = -1L

    val lastRecMillis: Long get() = -1L

    /** 上一次识别里被预过滤掉的框数量。 */
    val lastSkippedBoxes: Int get() = 0
}
