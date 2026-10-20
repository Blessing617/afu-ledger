package com.afu.ledger.auto.ocr

import com.afu.ledger.auto.PageText

/**
 * 把 OCR 的框结果整理成「自上而下的文本行」。
 *
 * det 通常一行给一个框，但也会把同一行的内容切成几块（例如「支付方式」与「零钱」）。
 * 这里按垂直重叠把它们合回一行，顺序是自上而下、自左而右 —— 与无障碍节点的文本顺序一致，
 * 这样 [com.afu.ledger.auto.PaymentPageDetector] 拿到的快照形状与读屏通道完全相同。
 */
object OcrLineBuilder {

    /** 低于该置信度的行直接丢弃：宁可漏行，也不要错行。 */
    const val MIN_CONFIDENCE: Float = 0.6f

    /** 两块的垂直重叠超过较矮者高度的这个比例，就认为在同一行。 */
    private const val ROW_OVERLAP_RATIO = 0.5f

    /** 同一行内允许的最大水平间隙（按行高倍数计）。超过就当成两块内容，不硬拼。 */
    private const val MAX_GAP_RATIO = 2.0f

    fun build(lines: List<OcrLine>, minConfidence: Float = MIN_CONFIDENCE): List<String> =
        buildWithBounds(lines, minConfidence).map { it.text }

    /**
     * 与 [build] 相同，但保留每行的位置（取该行最上面那块的上沿）。
     *
     * 位置用于与节点通道共用同一套「按视觉行分组」逻辑：OCR 的行本身已经是
     * 「标签 值」拼好的一段文本，所以把它们当成「单元素的视觉行」处理即可。
     */
    fun buildWithBounds(
        lines: List<OcrLine>,
        minConfidence: Float = MIN_CONFIDENCE,
    ): List<PageText> {
        val usable = lines
            .filter { it.text.isNotBlank() && it.confidence >= minConfidence }
            .sortedWith(compareBy({ it.top }, { it.left }))
        if (usable.isEmpty()) return emptyList()

        val rows = ArrayList<MutableList<OcrLine>>()
        for (line in usable) {
            val current = rows.lastOrNull()
            val sameRow = current != null && belongsToRow(current.first(), line)
            if (sameRow) current.add(line) else rows.add(mutableListOf(line))
        }

        return rows.mapNotNull { row ->
            val sorted = row.sortedBy { it.left }
            val text = sorted.joinToString(separator = " ") { it.text.trim() }.trim()
            if (text.isEmpty()) {
                null
            } else {
                PageText(text = text, top = sorted.first().top, left = sorted.first().left)
            }
        }
    }

    private fun belongsToRow(a: OcrLine, b: OcrLine): Boolean {
        val overlap = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        if (overlap <= 0) return false
        val shorter = minOf(a.height, b.height)
        if (overlap.toFloat() / shorter < ROW_OVERLAP_RATIO) return false

        val gap = if (a.right < b.left) b.left - a.right else if (b.right < a.left) a.left - b.right else 0
        val avgHeight = (a.height + b.height) / 2f
        return gap <= avgHeight * MAX_GAP_RATIO
    }
}
