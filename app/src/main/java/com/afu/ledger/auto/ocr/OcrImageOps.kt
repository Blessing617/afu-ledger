package com.afu.ledger.auto.ocr

import kotlin.math.roundToInt

/**
 * 位图预处理：尺寸计算、裁切，以及「已缩放到目标尺寸」的归一化。
 *
 * ## 为什么不再在这里做缩放
 *
 * 旧实现用 Kotlin 双层循环做双线性插值，每输出一个像素要读 4 个源像素、按 3 通道各算 4 次加权。
 * 全屏 1440×3200 缩到 960 长边时约 41 万个输出像素 × 12 次采样，单线程纯 Kotlin 跑，
 * 在真机上就是几百毫秒 —— 实测整条识别链路 1894ms，这是最大的一块。
 *
 * 现在缩放交给系统（`Bitmap.createScaledBitmap`，最终走 Skia 的 native 实现），
 * 这里只负责「把已经缩好的 ARGB 像素转成 CHW + 归一化」的逐像素循环。
 * 逐像素循环的规模仍有 3 × H × W，但不再有插值计算，且不必读 4 个源像素。
 *
 * 归一化循环本身也做了两点提速：
 * 1. 预先把每个通道的偏移与缩放算好，内层只做一次乘加；
 * 2. 用位运算直接取 R/G/B，避免 `shr` + `and` 的重复计算。
 */
object OcrImageOps {

    /** det 输入长边上限。越大越准、越慢。 */
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
        val nw = ((width * scale / 32f).roundToIntSafe() * 32).coerceAtLeast(32)
        val nh = ((height * scale / 32f).roundToIntSafe() * 32).coerceAtLeast(32)
        return intArrayOf(nw, nh)
    }

    /**
     * det 输入：CHW + ImageNet 归一化。
     *
     * [image] 必须**已经是** [targetWidth] × [targetHeight] 的像素，缩放由调用方用系统能力完成。
     * 尺寸不符时返回 null，让调用方走降级路径，而不是在错误的尺寸上瞎算。
     */
    fun detInput(image: OcrImage, targetWidth: Int, targetHeight: Int): FloatArray? {
        if (image.width != targetWidth || image.height != targetHeight) return null

        val planeSize = targetHeight * targetWidth
        val out = FloatArray(3 * planeSize)

        // 预计算每通道的 (1/std, -mean/std)，内层只做 mul + add
        val scaleR = 1f / DET_STD[0]
        val scaleG = 1f / DET_STD[1]
        val scaleB = 1f / DET_STD[2]
        val biasR = -DET_MEAN[0] / DET_STD[0]
        val biasG = -DET_MEAN[1] / DET_STD[1]
        val biasB = -DET_MEAN[2] / DET_STD[2]

        val pixels = image.argb
        val inv255 = 1f / 255f
        for (i in 0 until planeSize) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) * inv255
            val g = ((p shr 8) and 0xFF) * inv255
            val b = (p and 0xFF) * inv255
            out[i] = r * scaleR + biasR
            out[planeSize + i] = g * scaleG + biasG
            out[2 * planeSize + i] = b * scaleB + biasB
        }
        return out
    }

    /** rec 输入尺寸：高度固定 48，宽度按比例，上限 [maxWidth]。 */
    fun recTargetSize(patch: OcrImage, maxWidth: Int = REC_MAX_WIDTH): IntArray? {
        if (patch.width < 1 || patch.height < 1) return null
        val targetHeight = REC_HEIGHT
        val targetWidth = ((patch.width.toFloat() * targetHeight / patch.height).roundToIntSafe())
            .coerceIn(1, maxWidth)
        return intArrayOf(targetWidth, targetHeight)
    }

    /** rec 输入张量。像素需已缩放到 [targetWidth] × [targetHeight]。 */
    fun recTensor(patch: OcrImage, targetWidth: Int, targetHeight: Int): FloatArray? {
        if (patch.width != targetWidth || patch.height != targetHeight) return null

        val planeSize = targetHeight * targetWidth
        val out = FloatArray(3 * planeSize)
        val pixels = patch.argb
        val inv255 = 1f / 255f
        for (i in 0 until planeSize) {
            val p = pixels[i]
            // rec 的归一化是 (x/255 - 0.5) / 0.5 = x/127.5 - 1
            out[i] = ((p shr 16) and 0xFF) * inv255 * 2f - 1f
            out[planeSize + i] = ((p shr 8) and 0xFF) * inv255 * 2f - 1f
            out[2 * planeSize + i] = (p and 0xFF) * inv255 * 2f - 1f
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

    /** `roundToInt` 在非有限值上会抛异常，这里做一次兜底，避免脏数据把识别打崩。 */
    private fun Float.roundToIntSafe(): Int =
        if (isNaN() || isInfinite()) 0 else this.roundToInt()
}
