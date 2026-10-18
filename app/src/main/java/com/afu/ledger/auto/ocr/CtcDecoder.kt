package com.afu.ledger.auto.ocr

/**
 * PP-OCR rec 的 CTC 贪心解码。
 *
 * 模型输出 `[steps][classes]`，其中：
 * - 索引 0 是 CTC blank
 * - 索引 `1..dict.size` 对应字典里的第 `k-1` 个字符
 * - 最后一位（`dict.size + 1`）是空格（PaddleOCR 的 `use_space_char`）
 */
object CtcDecoder {

    data class Decoded(val text: String, val confidence: Float)

    val EMPTY = Decoded("", 0f)

    fun decode(
        logits: FloatArray,
        steps: Int,
        classes: Int,
        dict: List<String>,
    ): Decoded {
        if (steps <= 0 || classes <= 0) return EMPTY
        if (logits.size < steps * classes) return EMPTY

        val builder = StringBuilder()
        var confidenceSum = 0.0
        var kept = 0
        var previous = -1

        for (step in 0 until steps) {
            val offset = step * classes
            var bestIndex = 0
            var bestScore = logits[offset]
            for (c in 1 until classes) {
                val score = logits[offset + c]
                if (score > bestScore) {
                    bestScore = score
                    bestIndex = c
                }
            }

            if (bestIndex != previous && bestIndex != 0) {
                val char = charAt(bestIndex, dict)
                if (char != null) {
                    builder.append(char)
                    confidenceSum += bestScore
                    kept++
                }
            }
            previous = bestIndex
        }

        if (kept == 0) return EMPTY
        return Decoded(builder.toString(), (confidenceSum / kept).toFloat())
    }

    private fun charAt(index: Int, dict: List<String>): String? = when {
        index in 1..dict.size -> dict[index - 1]
        index == dict.size + 1 -> " "
        else -> null
    }
}
