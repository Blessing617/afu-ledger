package com.afu.ledger.auto.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer

/**
 * PP-OCRv4 移动版（det + rec）的 ONNX Runtime 实现。
 *
 * 完全离线：模型随 APK 分发，会话只在本地创建，不涉及任何网络路径。
 * 所有推理都在 [Dispatchers.Default] 上串行执行 —— OrtSession 不是线程安全的。
 *
 * ## 性能要点（相对旧实现）
 *
 * 旧实现在 Kotlin 里逐像素双线性缩放到 det 输入尺寸，全屏缩放是整条链路里最大的一块。
 * 现在缩放交给 [BitmapScaler]（Skia native），Kotlin 只做归一化。
 * rec 的裁切块也直接在缩放后的位图上取，不再从原图逐像素复制再插值。
 */
class PpOcrEngine(
    private val files: OcrModelInstaller.OcrModelFiles,
    private val detLimitSide: Int = OcrImageOps.DET_LIMIT_SIDE,
) : OcrEngine {

    private val mutex = Mutex()
    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    @Volatile
    private var detSession: OrtSession? = null

    @Volatile
    private var recSession: OrtSession? = null

    @Volatile
    private var dict: List<String>? = null

    @Volatile
    private var loadFailure: String? = null

    /** 上一次识别的总耗时，供设置页展示。 */
    @Volatile
    var lastElapsedMillis: Long = -1L
        private set

    /** 上一次识别里 det 与 rec 的耗时拆分，供日志定位瓶颈。 */
    @Volatile
    var lastDetMillis: Long = -1L
        private set

    @Volatile
    var lastRecMillis: Long = -1L
        private set

    override suspend fun warmUp(): Boolean = withContext(Dispatchers.Default) {
        mutex.withLock { ensureLoaded() != null }
    }

    override suspend fun recognize(bitmap: Bitmap): List<OcrLine> = withContext(Dispatchers.Default) {
        mutex.withLock {
            val startedAt = System.currentTimeMillis()
            val loaded = ensureLoaded() ?: return@withLock emptyList()
            runCatching { runPipeline(bitmap, loaded) }
                .onFailure { Log.w(TAG, "OCR 失败：${it.message}") }
                .getOrDefault(emptyList())
                .also { lastElapsedMillis = System.currentTimeMillis() - startedAt }
        }
    }

    override fun close() {
        runCatching { detSession?.close() }
        runCatching { recSession?.close() }
        detSession = null
        recSession = null
    }

    /** 初始化失败原因；null 表示尚未失败过。 */
    fun failureReason(): String? = loadFailure

    private fun ensureLoaded(): Loaded? {
        val existingDet = detSession
        val existingRec = recSession
        val existingDict = dict
        if (existingDet != null && existingRec != null && existingDict != null) {
            return Loaded(existingDet, existingRec, existingDict)
        }

        return runCatching {
            val options = OrtSession.SessionOptions()
            options.setIntraOpNumThreads(2)
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            // `.ort` 模型里带着固定优化记录，打开它才能用上，否则每次加载都要重新优化
            runCatching {
                options.addConfigEntry("session.enable_saved_runtime_optimizations", "1")
            }
            val det = env.createSession(files.detPath.absolutePath, options)
            val rec = env.createSession(files.recPath.absolutePath, options)
            val loadedDict = readDict(files.dictPath)
            require(loadedDict.isNotEmpty()) { "字典为空" }
            detSession = det
            recSession = rec
            dict = loadedDict
            loadFailure = null
            Loaded(det, rec, loadedDict)
        }.onFailure {
            loadFailure = it.message ?: it::class.java.simpleName
            Log.w(TAG, "OCR 模型加载失败：$loadFailure")
        }.getOrNull()
    }

    private fun readDict(file: File): List<String> =
        file.readLines(Charsets.UTF_8).toMutableList().apply {
            // 字典文件末尾常有空行，不能把它当成一个字符
            while (isNotEmpty() && last().isEmpty()) removeAt(lastIndex)
        }

    private fun runPipeline(source: Bitmap, loaded: Loaded): List<OcrLine> {
        val sizes = OcrImageOps.detTargetSize(source.width, source.height, detLimitSide)
        val detWidth = sizes[0]
        val detHeight = sizes[1]

        // det 输入：系统缩放 + 直接归一化，省掉一次全尺寸 int 数组的中间对象
        val detInput = BitmapScaler.scaleToDetInput(source, detWidth, detHeight)
            ?: return emptyList()

        val detStarted = System.currentTimeMillis()
        val prob = runDet(detInput, loaded, detWidth, detHeight)
        lastDetMillis = System.currentTimeMillis() - detStarted
        if (prob.isEmpty()) return emptyList()

        val boxes = DbPostProcessor.findBoxes(prob, detWidth, detHeight)
            .filter { it.score >= DbPostProcessor.BOX_SCORE_THRESHOLD }
        if (boxes.isEmpty()) return emptyList()

        // det 坐标系 → 原图坐标系：裁切在**原图**上做，保证 rec 拿到足够细节
        val scaleX = source.width.toFloat() / detWidth
        val scaleY = source.height.toFloat() / detHeight

        val recStarted = System.currentTimeMillis()
        val lines = ArrayList<OcrLine>(boxes.size)
        for (box in boxes) {
            val expanded = DbPostProcessor.unclip(box)
            val left = (expanded.left * scaleX).toInt()
            val top = (expanded.top * scaleY).toInt()
            val right = (expanded.right * scaleX).toInt()
            val bottom = (expanded.bottom * scaleY).toInt()

            val decoded = recognizePatch(source, left, top, right, bottom, loaded) ?: continue
            if (decoded.text.isBlank()) continue

            lines.add(
                OcrLine(
                    text = decoded.text,
                    confidence = decoded.confidence,
                    left = (box.left * scaleX).toInt(),
                    top = (box.top * scaleY).toInt(),
                    right = (box.right * scaleX).toInt(),
                    bottom = (box.bottom * scaleY).toInt(),
                ),
            )
        }
        lastRecMillis = System.currentTimeMillis() - recStarted
        return lines
    }

    /**
     * 从原图裁出一块并识别。
     *
     * 裁切用 `Bitmap.createBitmap`（native），再缩放到 rec 输入尺寸，全程不经过 Kotlin 逐像素插值。
     */
    private fun recognizePatch(
        source: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        loaded: Loaded,
    ): CtcDecoder.Decoded? {
        val l = left.coerceIn(0, source.width - 1)
        val t = top.coerceIn(0, source.height - 1)
        val r = right.coerceIn(l + 1, source.width)
        val b = bottom.coerceIn(t + 1, source.height)

        val patch = runCatching {
            Bitmap.createBitmap(source, l, t, r - l, b - t)
        }.getOrNull() ?: return null

        try {
            // 先算 rec 目标尺寸再缩放；patch 高度为 0 时直接放弃
            if (patch.height < 1) return null
            val targetHeight = OcrImageOps.REC_HEIGHT
            val targetWidth = ((patch.width.toFloat() * targetHeight / patch.height)
                .toInt()).coerceIn(1, OcrImageOps.REC_MAX_WIDTH)

            val recImage = BitmapScaler.scaleToImage(patch, targetWidth, targetHeight) ?: return null
            val input = OcrImageOps.recTensor(recImage, targetWidth, targetHeight) ?: return null
            return runRec(input, targetWidth, targetHeight, loaded)
        } finally {
            patch.recycle()
        }
    }

    private fun runDet(
        input: FloatArray,
        loaded: Loaded,
        targetWidth: Int,
        targetHeight: Int,
    ): FloatArray {
        val shape = longArrayOf(1, 3, targetHeight.toLong(), targetWidth.toLong())

        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape).use { tensor ->
            val inputName = loaded.det.inputNames.first()
            loaded.det.run(mapOf(inputName to tensor)).use { result ->
                // 用 FloatBuffer 读，避免依赖 ORT 返回数组的嵌套层级
                return flatOf(result[0] as OnnxTensor)
            }
        }
    }

    private fun runRec(
        input: FloatArray,
        targetWidth: Int,
        targetHeight: Int,
        loaded: Loaded,
    ): CtcDecoder.Decoded? {
        val shape = longArrayOf(1, 3, targetHeight.toLong(), targetWidth.toLong())

        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape).use { tensor ->
            val inputName = loaded.rec.inputNames.first()
            loaded.rec.run(mapOf(inputName to tensor)).use { result ->
                val output = result[0] as OnnxTensor
                val flat = flatOf(output)
                val outputShape = output.info.shape
                val steps = outputShape[1].toInt()
                val classes = outputShape[2].toInt()
                if (steps <= 0 || classes <= 0) return null
                return CtcDecoder.decode(flat, steps, classes, loaded.dict)
            }
        }
    }

    /** 把一个 float 张量摊平。失败返回空数组。 */
    private fun flatOf(tensor: OnnxTensor): FloatArray {
        val buffer: FloatBuffer = tensor.floatBuffer ?: return FloatArray(0)
        val flat = FloatArray(buffer.remaining())
        buffer.get(flat)
        return flat
    }

    private class Loaded(
        val det: OrtSession,
        val rec: OrtSession,
        val dict: List<String>,
    )

    private companion object {
        const val TAG = "AfuOcr"
    }
}
