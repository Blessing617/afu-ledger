package com.afu.ledger.auto.ocr

import android.graphics.Bitmap

/**
 * 用系统能力（Skia native）缩放位图并取出 ARGB 像素。
 *
 * 这一步替代了原先在 Kotlin 里逐像素做双线性插值的实现。
 * 同样的缩放语义（双线性），但计算在 native 侧完成，耗时通常只有纯 Kotlin 的几分之一。
 *
 * 只在 Android 侧使用；纯逻辑（归一化、裁切、尺寸计算）仍在 [OcrImageOps] 里，可单测。
 */
object BitmapScaler {

    /**
     * 把 [source] 缩放到 `targetWidth × targetHeight` 并**直接归一化成 det 输入张量**。
     *
     * 与「先 [scaleToImage] 得到 IntArray、再 [OcrImageOps.detInput] 归一化」相比，
     * 少一次 `width × height` 的 int 数组分配与拷贝：像素直接从 `getPixels` 的临时缓冲
     * 灌进 float 张量，然后用完即弃。
     *
     * 全屏 1440×3200 缩到 det 尺寸后约 43 万像素，省下的是 ~1.7 MB 的一次性分配。
     *
     * @return CHW 排布的归一化张量；参数非法时返回 null
     */
    fun scaleToDetInput(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
    ): FloatArray? {
        if (targetWidth < 1 || targetHeight < 1) return null
        if (source.width < 1 || source.height < 1) return null

        val scaled = if (source.width == targetWidth && source.height == targetHeight) {
            source
        } else {
            runCatching {
                Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
            }.getOrNull() ?: return null
        }

        try {
            val pixels = IntArray(targetWidth * targetHeight)
            scaled.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
            return OcrImageOps.detInputFromPixels(pixels, targetWidth, targetHeight)
        } finally {
            // createScaledBitmap 在尺寸相同时直接返回源对象，此时不能回收
            if (scaled !== source) scaled.recycle()
        }
    }

    /**
     * 把 [source] 缩放到 `targetWidth × targetHeight` 并取出像素。
     *
     * @return 缩放后的 [OcrImage]；参数非法时返回 null
     */
    fun scaleToImage(source: Bitmap, targetWidth: Int, targetHeight: Int): OcrImage? {
        if (targetWidth < 1 || targetHeight < 1) return null
        if (source.width < 1 || source.height < 1) return null

        val scaled = if (source.width == targetWidth && source.height == targetHeight) {
            source
        } else {
            runCatching {
                Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
            }.getOrNull() ?: return null
        }

        val argb = runCatching { scaled.toArgbPixels() }.getOrNull()

        // createScaledBitmap 在尺寸相同时直接返回源对象，此时不能回收
        if (scaled !== source) scaled.recycle()

        if (argb == null) return null
        return OcrImage(targetWidth, targetHeight, argb)
    }

    /** 直接取一张位图的 ARGB 像素，不缩放。 */
    fun toImage(source: Bitmap): OcrImage? {
        val argb = runCatching { source.toArgbPixels() }.getOrNull() ?: return null
        return OcrImage(source.width, source.height, argb)
    }

    private fun Bitmap.toArgbPixels(): IntArray {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels
    }
}
