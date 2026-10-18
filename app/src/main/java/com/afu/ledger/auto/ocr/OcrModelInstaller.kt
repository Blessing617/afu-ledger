package com.afu.ledger.auto.ocr

import java.io.File
import java.io.InputStream

/**
 * 把 APK 内的 OCR 模型释放到私有目录。
 *
 * ONNX Runtime 只能从文件系统加载模型，不能直接读 assets，所以首次使用前要解压一次。
 * 用版本目录 + 完成标记避免重复解压；模型缺失（被清理、升级不完整）时自愈重建。
 */
object OcrModelInstaller {

    /** 模型版本。更换模型或字典时必须递增，否则旧目录不会被替换。 */
    const val MODEL_VERSION: String = "ppocrv4-mobile-ort-3"

    /**
     * 模型用 ORT 格式（`.ort`）而不是 `.onnx`：
     * `onnxruntime-mobile` 不支持读取 `.onnx`，而它的 so 只有 3.7MB（full 包是 34MB）。
     */
    const val DET_FILE: String = "det.ort"
    const val REC_FILE: String = "rec.ort"
    const val DICT_FILE: String = "ppocr_keys_v1.txt"

    /** 各文件的最小可信字节数，用来判断解压是否完整（不追求精确，够挡掉 0 字节与半截文件）。 */
    private const val MIN_DET_BYTES = 3_000_000L
    private const val MIN_REC_BYTES = 6_000_000L
    private const val MIN_DICT_BYTES = 20_000L

    /**
     * 解压完成标记。
     *
     * 只比大小不够：换了模型但字节数相近时会被误判成「已就绪」，于是继续用旧模型。
     * 标记文件里写版本号，版本一变就必然重新解压。
     */
    private const val MARKER_FILE = ".ready"

    /**
     * 确保模型就绪。
     *
     * @param targetDir 目标根目录（通常是 `context.filesDir/ocr`）
     * @return 模型文件路径；解压失败返回 null，调用方据此把 OCR 标记为不可用
     */
    fun ensureInstalled(
        targetDir: File,
        assetOpener: (String) -> InputStream,
    ): OcrModelFiles? {
        val versionDir = File(targetDir, MODEL_VERSION)
        val files = OcrModelFiles(
            detPath = File(versionDir, DET_FILE),
            recPath = File(versionDir, REC_FILE),
            dictPath = File(versionDir, DICT_FILE),
            markerPath = File(versionDir, MARKER_FILE),
        )
        if (files.isReady()) return files

        versionDir.deleteRecursively()
        if (!versionDir.mkdirs() && !versionDir.isDirectory) return null

        val assets = listOf(
            DET_FILE to files.detPath,
            REC_FILE to files.recPath,
            DICT_FILE to files.dictPath,
        )
        for ((assetName, target) in assets) {
            val copied = runCatching {
                assetOpener("ocr/$assetName").use { input ->
                    target.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
                }
            }.isSuccess
            if (!copied) {
                versionDir.deleteRecursively()
                return null
            }
        }

        val marked = runCatching { files.markerPath.writeText(MODEL_VERSION) }.isSuccess
        if (!marked) {
            versionDir.deleteRecursively()
            return null
        }
        return files.takeIf { it.isReady() }
    }

    /** 已释放的模型文件路径。 */
    data class OcrModelFiles(
        val detPath: File,
        val recPath: File,
        val dictPath: File,
        val markerPath: File,
    ) {
        fun isReady(): Boolean =
            markerPath.isFile &&
                markerPath.readText().trim() == MODEL_VERSION &&
                detPath.isFile && detPath.length() >= MIN_DET_BYTES &&
                recPath.isFile && recPath.length() >= MIN_REC_BYTES &&
                dictPath.isFile && dictPath.length() >= MIN_DICT_BYTES
    }
}
