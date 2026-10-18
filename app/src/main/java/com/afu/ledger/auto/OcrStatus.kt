package com.afu.ledger.auto

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 截图 OCR 的运行状态，只存在内存里。
 *
 * 设置页要能看出「OCR 到底能不能用」，而不是只有一句「已开启」——
 * 系统版本不支持、模型解压失败、截图无权限，这些都必须如实显示。
 */
data class OcrStatus(
    val available: Boolean = false,
    val reason: String? = null,
    val lastElapsedMillis: Long = -1L,
    val lastOutcome: String? = null,
    /** 最近一次识别的分段耗时摘要（节点 / 截图 / OCR / 合计）。 */
    val lastDetail: String? = null,
)

class OcrStatusHolder {

    private val _state = MutableStateFlow(OcrStatus())
    val state: StateFlow<OcrStatus> = _state.asStateFlow()

    fun markUnavailable(reason: String) {
        _state.update { it.copy(available = false, reason = reason) }
    }

    fun markAvailable() {
        _state.update { it.copy(available = true, reason = null) }
    }

    fun recordOutcome(elapsedMillis: Long, outcome: String, detail: String? = null) {
        _state.update {
            it.copy(lastElapsedMillis = elapsedMillis, lastOutcome = outcome, lastDetail = detail)
        }
    }

    fun reset() {
        _state.value = OcrStatus()
    }
}
