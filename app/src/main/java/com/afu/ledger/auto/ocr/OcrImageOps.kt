package com.afu.ledger.auto.ocr

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * 位图预处理与裁切，纯 Kotlin、无 Android 依赖。
 *
 * 采样一律用双线性：手机截图缩到 960 长边时，最近邻会把细笔画打断，中文小字尤其明显。
 */
object OcrImageOps {

    /** det 输入长边上限。越大越准、越慢；960 在小米 14 Pro 上约 200 ms。 */
    const val DET_LIMIT_SIDE = 960

    /** rec 输入固定高度（PP-OCRv4 的约定）。 */
    const val REC_HEIGHT = 48

    /** rec 单行宽度上限，防止超长行把推理时间拖爆。 */
    const val REC_MAX_WIDTH = 640

    private val DET_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val DET_STD = floatArrayOf(0.229f, 0.224f, 0.225f)

    /** 按 32 的倍数取整，这是 det 网络下采样的要求。 */
    fun detTargetSize(width: Int, height: Int, limitSide: Int = DET_LIMIT_SIDE): IntArray {
        val maxSide = maxOf(width, height)
        val scale = if (maxSide > limitSide) limitSide.toFloat() / maxSide else 1f
        val nw = ((width * scale / 32f).roundToInt() * 32).coerceAtLeast(32)
        val nh = ((height * scale / 32f).roundToInt() * 32).coerceAtLeast(32)
        return intArrayOf(nw, nh)
    }

    /** det 输入：CHW + ImageNet 归一化。 */
    fun detInput(image: OcrImage, targetWidth: Int, targetHeight: Int): FloatArray {
        val out = FloatArray(3 * targetHeight * targetWidth)
        val planeSize = targetHeight * targetWidth
        val sx = image.width.toFloat() / targetWidth
        val sy = image.height.toFloat() / targetHeight

        for (y in 0 until targetHeight) {
            val fy = ((y + 0.5f) * sy - 0.5f).coerceIn(0f, (image.height - 1).toFloat())
            val y0 = floor(fy).toInt()
            val y1 = (y0 + 1).coerceAtMost(image.height - 1)
            val wy = fy - y0
            val rowBase = y0 * image.width
            val rowNext = y1 * image.width

            for (x in 0 until targetWidth) {
                val fx = ((x + 0.5f) * sx - 0.5f).coerceIn(0f, (image.width - 1).toFloat())
                val x0 = floor(fx).toInt()
                val x1 = (x0 + 1).coerceAtMost(image.width - 1)
                val wx = fx - x0

                val p00 = image.argb[rowBase + x0]
                val p10 = image.argb[rowBase + x1]
                val p01 = image.argb[rowNext + x0]
                val p11 = image.argb[rowNext + x1]

                val w00 = (1 - wx) * (1 - wy)
                val w10 = wx * (1 - wy)
                val w01 = (1 - wx) * wy
                val w11 = wx * wy

                val index = y * targetWidth + x
                for (c in 0..2) {
                    val shift = 16 - c * 8
                    val value = ((p00 shr shift) and 0xFF) * w00 +
                        ((p10 shr shift) and 0xFF) * w10 +
                        ((p01 shr shift) and 0xFF) * w01 +
                        ((p11 shr shift) and 0xFF) * w11
                    out[c * planeSize + index] = ((value / 255f) - DET_MEAN[c]) / DET_STD[c]
                }
            }
        }
        return out
    }

    /** rec 输入尺寸：高度固定 48，宽度按比例，上限 [maxWidth]。 */
    fun recTargetSize(patch: OcrImage, maxWidth: Int = REC_MAX_WIDTH): IntArray? {
        if (patch.width < 1 || patch.height < 1) return null
        val targetHeight = REC_HEIGHT
        val targetWidth = ((patch.width.toFloat() * targetHeight / patch.height).roundToInt())
            .coerceIn(1, maxWidth)
        return intArrayOf(targetWidth, targetHeight)
    }

    /** rec 输入张量。尺寸由 [recTargetSize] 给出。 */
    fun recTensor(patch: OcrImage, targetWidth: Int, targetHeight: Int): FloatArray {
        val out = FloatArray(3 * targetHeight * targetWidth)
        val planeSize = targetHeight * targetWidth
        val sx = patch.width.toFloat() / targetWidth
        val sy = patch.height.toFloat() / targetHeight

        for (y in 0 until targetHeight) {
            val fy = ((y + 0.5f) * sy - 0.5f).coerceIn(0f, (patch.height - 1).toFloat())
            val y0 = floor(fy).toInt()
            val y1 = (y0 + 1).coerceAtMost(patch.height - 1)
            val wy = fy - y0
            val rowBase = y0 * patch.width
            val rowNext = y1 * patch.width

            for (x in 0 until targetWidth) {
                val fx = ((x + 0.5f) * sx - 0.5f).coerceIn(0f, (patch.width - 1).toFloat())
                val x0 = floor(fx).toInt()
                val x1 = (x0 + 1).coerceAtMost(patch.width - 1)
                val wx = fx - x0

                val p00 = patch.argb[rowBase + x0]
                val p10 = patch.argb[rowBase + x1]
                val p01 = patch.argb[rowNext + x0]
                val p11 = patch.argb[rowNext + x1]

                val w00 = (1 - wx) * (1 - wy)
                val w10 = wx * (1 - wy)
                val w01 = (1 - wx) * wy
                val w11 = wx * wy

                val index = y * targetWidth + x
                for (c in 0..2) {
                    val shift = 16 - c * 8
                    val value = ((p00 shr shift) and 0xFF) * w00 +
                        ((p10 shr shift) and 0xFF) * w10 +
                        ((p01 shr shift) and 0xFF) * w01 +
                        ((p11 shr shift) and 0xFF) * w11
                    out[c * planeSize + index] = (value / 255f - 0.5f) / 0.5f
                }
            }
        }
        return out
    }

    /** 裁切。越界自动收紧，完全无效时返回 null。 */
    fun crop(image: OcrImage, left: Int, top: Int, right: Int, bottom: Int): OcrImage? {
        val l = left.coerceIn(0, image.width - 1)
        val t = top.coerceIn(0, image.height - 1)
        val r = right.coerceIn(l + 1, image.width)
        val b = bottom.coerceIn(t + 1, image.height)
        val width = r - l
        val height = b - t
        if (width < 1 || height < 1) return null

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            System.arraycopy(image.argb, (t + y) * image.width + l, pixels, y * width, width)
        }
        return OcrImage(width, height, pixels)
    }
}
