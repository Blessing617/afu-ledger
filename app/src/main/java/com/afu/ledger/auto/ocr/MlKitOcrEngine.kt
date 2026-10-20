package com.afu.ledger.auto.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * 基于 Google ML Kit 中文文字识别的 OCR 引擎。
 *
 * ## 为什么换掉 PP-OCRv4
 *
 * 原先用 PP-OCRv4（det + rec 两阶段）：先检测所有文本框，再**逐行**跑识别网络。
 * 真实微信账单详情页有 30 行文本，于是要做 1 次 det + 30 次 rec，
 * 实测 rec 阶段就要 1040–1290ms，占整个识别的 69%。
 *
 * 同赛道的 Tally（`cypressincloud/Tally`）用的是 ML Kit 的
 * `TextRecognition.getClient(ChineseTextRecognizerOptions())` —— **整页一次推理**，
 * 没有「逐行」这个阶段。这是两者最本质的差别，也是我们慢 4 倍的直接原因。
 *
 * 除此之外还有两个好处：
 * - 模型随 APK 分发，完全离线，不申请 `INTERNET`
 * - 返回的结构（Text → textBlocks → lines → boundingBox）天然就是我们要的行结构，
 *   不需要自己拼框、排序、做 NMS
 *
 * ## 保留的约束
 *
 * - 不落盘：截图位图用完即弃，识别结果只取文本与坐标
 * - 不联网：ML Kit 的 bundled 版本不触网；这是本项目「无 INTERNET 权限」的前提
 */
class MlKitOcrEngine : OcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    @Volatile
    override var lastDetMillis: Long = -1L
        private set

    @Volatile
    override var lastRecMillis: Long = -1L
        private set

    @Volatile
    override var lastSkippedBoxes: Int = 0
        private set

    @Volatile
    var lastElapsedMillis: Long = -1L
        private set

    @Volatile
    private var failure: String? = null

    override suspend fun recognize(bitmap: Bitmap): List<OcrLine> = withContext(Dispatchers.Default) {
        val startedAt = System.currentTimeMillis()
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val text = recognizer.process(image).await()
            val lines = mutableListOf<OcrLine>()

            for (block in text.textBlocks) {
                for (line in block.lines) {
                    val content = line.text
                    if (content.isBlank()) continue
                    val box = line.boundingBox ?: continue
                    lines.add(
                        OcrLine(
                            text = content,
                            // ML Kit 不提供逐行置信度；它内部已经过滤过低置信结果，
                            // 这里给一个中性值，让下游的「置信度过低丢弃」不会误杀。
                            confidence = 1f,
                            left = box.left,
                            top = box.top,
                            right = box.right,
                            bottom = box.bottom,
                        ),
                    )
                }
            }
            lines
        } catch (error: Throwable) {
            failure = error.message ?: error::class.java.simpleName
            emptyList()
        } finally {
            lastElapsedMillis = System.currentTimeMillis() - startedAt
        }
    }

    /**
     * 预热。
     *
     * ML Kit 的识别器是懒初始化的，第一次 `process` 会付出模型加载开销。
     * 这里用一张最小合法尺寸的图跑一次，把加载开销挪到服务连接时。
     *
     * 注意：ML Kit 要求输入**至少 32×32**，小于这个会抛
     * `InputImage width and height should be at least 32!`（踩过）。
     */
    override suspend fun warmUp(): Boolean = withContext(Dispatchers.Default) {
        runCatching {
            val tiny = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            try {
                recognizer.process(InputImage.fromBitmap(tiny, 0)).await()
            } finally {
                tiny.recycle()
            }
            true
        }.getOrElse { error ->
            failure = error.message ?: error::class.java.simpleName
            android.util.Log.e("AfuOcr", "ML Kit warmUp 失败", error)
            false
        }
    }

    override fun close() {
        runCatching { recognizer.close() }
    }

    /** 初始化/识别失败原因；null 表示还没失败过。 */
    fun failureReason(): String? = failure
}
