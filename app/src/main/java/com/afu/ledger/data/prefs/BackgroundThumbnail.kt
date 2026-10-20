package com.afu.ledger.data.prefs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import kotlin.math.max

/**
 * 背景图的极小缩略图，用作**首帧占位**。
 *
 * 为什么需要它：整屏背景图从磁盘解码到 GPU 上屏需要一次异步解码（即使按屏幕尺寸，
 * 也要几十毫秒），冷启动时用户看到的是纯色底，主观上就是「打开先空一下」。
 * 一张 96px 宽的缩略图解码只要几毫秒，所以可以在第一帧就铺上去，
 * 原图到达后再淡入覆盖 —— 观感从「先白一下」变成「图一直在，只是从糊到清」。
 *
 * 体积代价：文件约 5–15 KB，解码后常驻内存约 96×213×4 ≈ 80 KB，可忽略。
 */
object BackgroundThumbnail {

    private const val FILE_NAME = "background_thumb.jpg"
    // 240px 宽：铺满 1440px 屏仍有明显模糊，但已经能看出构图与色调；
    // 文件约 20–40 KB，解码后内存约 240×533×4 ≈ 500 KB，仍然可忽略。
    private const val TARGET_WIDTH = 240
    private const val TARGET_HEIGHT = 533
    private const val JPEG_QUALITY = 82

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    /** 读取占位缩略图；没有或损坏时返回 null（调用方直接不铺占位即可）。 */
    fun load(context: Context): Bitmap? {
        val file = file(context)
        if (!file.exists() || file.length() == 0L) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    /**
     * 在后台线程重新生成缩略图。
     *
     * 只做 bounds 解码 + 按需 inSampleSize，避免把整张原图读进内存。
     */
    fun regenerate(context: Context, uri: String) {
        val file = file(context)
        val out = runCatching {
            val resolver = context.contentResolver
            val parsed = Uri.parse(uri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching false

            var sample = 1
            while (bounds.outWidth / (sample * 2) >= TARGET_WIDTH &&
                bounds.outHeight / (sample * 2) >= TARGET_HEIGHT
            ) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(parsed)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return@runCatching false

            // 采样后再精确缩到目标尺寸，保持原图比例
            val scale = TARGET_WIDTH.toFloat() / decoded.width
            val width = max(1, TARGET_WIDTH)
            val height = max(1, (decoded.height * scale).toInt())
            val scaled = Bitmap.createScaledBitmap(decoded, width, height, true)
            if (scaled !== decoded) decoded.recycle()

            file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            scaled.recycle()
            true
        }.getOrDefault(false)

        if (!out) file.delete()
    }

    fun clear(context: Context) {
        file(context).delete()
    }
}
