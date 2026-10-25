package com.afu.ledger.auto

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** 一条识别记录。只保留排查所需的最小信息，不保存整页文本。 */
@Serializable
data class RecognitionEntry(
    val at: Long,
    val packageName: String,
    val outcome: String,
    val amountCents: Long? = null,
    val merchant: String? = null,
    val detail: String? = null,
    /** 识别出的方向（收入 / 支出）。 */
    val direction: String? = null,
    /** 账单时间的来源：PAGE / FALLBACK。回退时说明页面上根本没有时间。 */
    val timeSource: String? = null,
    /** 本次使用的去重指纹。重复入账时，两条记录的指纹是否相同一眼可辨。 */
    val fingerprint: String? = null,
    /** 命中时页面文本的摘要（前 3 行、每行 24 字）。排查误识别全靠它，不含整页内容。 */
    val pageHeadline: String? = null,
)

/**
 * 识别日志：内存环形缓冲 + 私有目录落盘。
 *
 * ## 为什么落盘
 *
 * 原先是纯内存的，进程一结束就没了。真机上出现「同一笔 3.50 被记了好几笔」时，
 * 第二天再想查已经没有任何证据 —— 只能靠猜。日志文件让人能回看**昨天**那几条记录
 * 用的指纹、方向与时间来源，从而判断到底是去重失效还是用户点了两次。
 *
 * ## 边界
 *
 * - 只存本机、只存识别元信息：金额、商户、方向、时间来源、指纹、结果
 * - **不存整页文本、不存截图**，页面原文只在识别那一刻的内存里存在
 * - 环形保留最近 [MAX_ENTRIES] 条与 [MAX_AGE_MILLIS] 天，超出即淘汰
 * - 写盘在 IO 线程上异步进行，不阻塞识别与弹窗
 */
class RecognitionLog(
    context: Context? = null,
    private val maxEntries: Int = MAX_ENTRIES,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val _entries = MutableStateFlow<List<RecognitionEntry>>(emptyList())
    val entries: StateFlow<List<RecognitionEntry>> = _entries.asStateFlow()

    private val file: File? = context?.let { File(it.filesDir, FILE_NAME) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    init {
        // 启动时把历史记录读回内存：用户重启应用后仍然看得到昨天那几条
        file?.takeIf { it.exists() }?.let { logFile ->
            scope.launch {
                val restored = runCatching { readFrom(logFile) }.getOrDefault(emptyList())
                if (restored.isNotEmpty()) {
                    _entries.update { current -> (current + restored).take(maxEntries) }
                }
            }
        }
    }

    fun record(entry: RecognitionEntry) {
        _entries.update { current -> (listOf(entry) + current).take(maxEntries) }
        val logFile = file ?: return
        scope.launch {
            runCatching { append(logFile, entry) }
        }
    }

    fun clear() {
        _entries.value = emptyList()
        val logFile = file ?: return
        scope.launch { runCatching { logFile.delete() } }
    }

    private suspend fun append(logFile: File, entry: RecognitionEntry) {
        withContext(Dispatchers.IO) {
            logFile.appendText(json.encodeToString(RecognitionEntry.serializer(), entry) + "\n")
            prune(logFile)
        }
    }

    /** 超龄的行先删掉；文件行数超过上限时整份重写成最近 [maxEntries] 行。 */
    private fun prune(logFile: File) {
        val lines = logFile.readLines().filter { it.isNotBlank() }
        if (lines.size <= maxEntries) return
        val kept = lines.takeLast(maxEntries)
        logFile.writeText(kept.joinToString("\n", postfix = "\n"))
    }

    private fun readFrom(logFile: File): List<RecognitionEntry> {
        val now = clock()
        return logFile.readLines()
            .asSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line -> runCatching { json.decodeFromString(RecognitionEntry.serializer(), line) }.getOrNull() }
            .filter { now - it.at <= MAX_AGE_MILLIS }
            .toList()
            .takeLast(maxEntries)
            .reversed()
    }

    companion object {
        const val MAX_ENTRIES: Int = 200
        const val MAX_AGE_MILLIS: Long = 7L * 24 * 60 * 60 * 1000
        const val FILE_NAME: String = "recognition_log.jsonl"

        /** 旧名字保留，避免调用点到处改。 */
        const val DEFAULT_MAX_ENTRIES: Int = MAX_ENTRIES
    }
}
