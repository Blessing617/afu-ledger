package com.afu.ledger.auto.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
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

    /** 上一次识别的耗时，供设置页展示。 */
    @Volatile
    var lastElapsedMillis: Long = -1L
        private set

    override suspend fun warmUp(): Boolean = withContext(Dispatchers.Default) {
        mutex.withLock { ensureLoaded() != null }
    }

    override suspend fun recognize(image: OcrImage): List<OcrLine> = withContext(Dispatchers.Default) {
        mutex.withLock {
            val startedAt = System.currentTimeMillis()
            val loaded = ensureLoaded() ?: return@withLock emptyList()
            runCatching { runPipeline(image, loaded) }
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

    private fun runPipeline(image: OcrImage, loaded: Loaded): List<OcrLine> {
        val sizes = OcrImageOps.detTargetSize(image.width, image.height, detLimitSide)
        val detWidth = sizes[0]
        val detHeight = sizes[1]

        val prob = runDet(image, loaded, detWidth, detHeight)
        if (prob.isEmpty()) return emptyList()

        val boxes = DbPostProcessor.findBoxes(prob, detWidth, detHeight)
            .filter { it.score >= DbPostProcessor.BOX_SCORE_THRESHOLD }
        if (boxes.isEmpty()) return emptyList()

        val scaleX = image.width.toFloat() / detWidth
        val scaleY = image.height.toFloat() / detHeight

        val lines = ArrayList<OcrLine>(boxes.size)
        for (box in boxes) {
            val expanded = DbPostProcessor.unclip(box)
            val patch = OcrImageOps.crop(
                image = image,
                left = (expanded.left * scaleX).toInt(),
                top = (expanded.top * scaleY).toInt(),
                right = (expanded.right * scaleX).toInt(),
                bottom = (expanded.bottom * scaleY).toInt(),
            ) ?: continue

            val decoded = runRec(patch, loaded) ?: continue
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
        return lines
    }

    private fun runDet(image: OcrImage, loaded: Loaded, targetWidth: Int, targetHeight: Int): FloatArray {
        val input = OcrImageOps.detInput(image, targetWidth, targetHeight)
        val shape = longArrayOf(1, 3, targetHeight.toLong(), targetWidth.toLong())

        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape).use { tensor ->
            val inputName = loaded.det.inputNames.first()
            loaded.det.run(mapOf(inputName to tensor)).use { result ->
                // 用 FloatBuffer 读，避免依赖 ORT 返回数组的嵌套层级
                return flatOf(result[0] as OnnxTensor)
            }
        }
    }

    private fun runRec(patch: OcrImage, loaded: Loaded): CtcDecoder.Decoded? {
        val size = OcrImageOps.recTargetSize(patch) ?: return null
        val targetWidth = size[0]
        val targetHeight = size[1]
        val input = OcrImageOps.recTensor(patch, targetWidth, targetHeight)
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
