package com.afu.ledger.auto

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 一条识别记录。只保留排查所需的最小信息，不保存整页文本。 */
data class RecognitionEntry(
    val at: Long,
    val packageName: String,
    val outcome: String,
    val amountCents: Long? = null,
    val merchant: String? = null,
    val detail: String? = null,
)

/**
 * 内存环形识别日志。
 *
 * 只在用户开启日志开关时记录；进程重启即清空——为了不留持久化的页面文本，
 * 这点可观测性让位给隐私。最多保留 [maxEntries] 条。
 */
class RecognitionLog(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {

    private val _entries = MutableStateFlow<List<RecognitionEntry>>(emptyList())
    val entries: StateFlow<List<RecognitionEntry>> = _entries.asStateFlow()

    fun record(entry: RecognitionEntry) {
        _entries.update { current -> (listOf(entry) + current).take(maxEntries) }
    }

    fun clear() {
        _entries.value = emptyList()
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES: Int = 50
    }
}
