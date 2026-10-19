package com.afu.ledger.auto

/**
 * 把页面文本整理成「视觉行 + 标签→值」的结构，供金额、时间、商户、分类共用。
 *
 * ## 为什么不能直接用扁平文本
 *
 * 账单详情页本质是「标签 | 值」的两列布局，但无障碍节点树给出的顺序是错的：
 * 同一视觉行里，右侧**值**节点的 `top` 比左侧**标签**小 7px，按 top 排序就变成
 *
 * ```
 * 2026-10-19 18:01:22      ← 值排到了前面
 * 支付时间
 * ```
 *
 * 于是「支付时间」后面跟的是下一行的内容。旧实现用「金额前后两行」猜商户，
 * 在这种顺序下必然取错。
 *
 * 这里先按 `top` 容差把文本分组还原成视觉行，行内再按 `left` 排序，
 * 于是「标签 → 值」可以按真实位置取，而不是按遍历顺序猜。
 *
 * OCR 通道的行本身已经是「标签 值」拼好的一段文本（见 `OcrLineBuilder`），
 * 它们会各自成为「只有一个 cell 的行」，走同一条取值路径。
 */
class BillFieldParser private constructor(
    val rows: List<Row>,
    private val items: List<PageText>,
) {

    /** 一个视觉行：同一高度上从左到右的若干段文本。 */
    data class Row(val top: Int, val cells: List<String>) {
        val text: String get() = cells.joinToString(separator = " ")
    }

    /** 供「不需要结构」的旧逻辑使用的扁平行文本。 */
    val lineTexts: List<String> get() = rows.map { it.text }

    /** 原始文本项（含坐标），用于上下文排除等需要看邻近行的地方。 */
    val allItems: List<PageText> get() = items

    /**
     * 取某个标签后面的值。
     *
     * 支持四种排布，都是真机见过的：
     * 1. 标签是独立 cell，值在同行的右侧 cell（节点通道的常态）
     * 2. 标签和值被拼进同一个 cell（OCR 通道的常态，如 `支付时间 2026-10-19 18:01:22`）
     * 3. 标签在一行、值在紧邻的下一行
     * 4. 标签在一行、值在**上一行**（OCR 两列切上下时顺序可能是反的，
     *    实测微信红包页就是 `2026年8月30日19:33:55` 在 `收款时间` 上面）
     *
     * @param labels 依次尝试的标签，命中第一个就返回
     */
    fun valueOf(vararg labels: String): String? {
        for (label in labels) {
            valueCandidates(label).firstOrNull()?.let { return it }
        }
        return null
    }

    /**
     * 某个标签的所有**候选值**，按可信度排序：同行右侧 → 下一行 → 上一行。
     *
     * 需要自带校验的调用方（例如时间解析要看值是否符合日期格式）应该用这个，
     * 逐个试到能校验通过为止 —— 直接取 [valueOf] 的第一项可能拿到的不是值
     * （标签下面的下一行往往是另一个标签）。
     */
    fun valueCandidates(label: String): List<String> {
        val out = LinkedHashSet<String>()
        rows.forEachIndexed { rowIndex, row ->
            row.cells.forEachIndexed { cellIndex, cell ->
                val inline: String? = when {
                    cell == label -> null
                    cell.startsWith(label) -> cell.removePrefix(label).trim()
                    else -> return@forEachIndexed
                }

                // 情形 2：同一 cell 里就带着值
                if (!inline.isNullOrEmpty()) out.add(inline)

                // 情形 1：同行右侧还有 cell
                val rest = row.cells.drop(cellIndex + 1)
                    .filter { it.isNotBlank() && it != label }
                    .joinToString(separator = " ")
                    .trim()
                if (rest.isNotEmpty()) out.add(rest)

                // 情形 3：值在下一行
                rows.getOrNull(rowIndex + 1)?.text?.trim()
                    ?.takeIf { it.isNotEmpty() }?.let { out.add(it) }

                // 情形 4：值在上一行
                rows.getOrNull(rowIndex - 1)?.text?.trim()
                    ?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
            }
        }
        return out.toList()
    }

    /** 这一行里是否含有某个标签。 */
    fun hasLabel(label: String): Boolean =
        rows.any { row -> row.cells.any { it == label || it.startsWith(label) } }

    companion object {
        /**
         * 同一视觉行允许的 `top` 偏差。
         *
         * 实测：政策行内标签与值的 top 差 7px；不同行之间至少差 100px 以上。
         * 取 24px 既能把「值比标签矮半行」的错位合回一行，又不会把相邻两行粘在一起。
         */
        const val ROW_TOP_TOLERANCE: Int = 24

        fun parse(snapshot: PageSnapshot): BillFieldParser {
            val withPosition = snapshot.items.filter { it.hasPosition }
            val withoutPosition = snapshot.items.filterNot { it.hasPosition }

            val rows = ArrayList<Row>()
            // 有坐标的按 top 分组
            withPosition.sortedWith(compareBy({ it.top }, { it.left }))
                .forEach { item ->
                    val current = rows.lastOrNull()
                    if (current != null && item.top - current.top <= ROW_TOP_TOLERANCE) {
                        rows[rows.lastIndex] = current.copy(cells = current.cells + item.text)
                    } else {
                        rows.add(Row(top = item.top, cells = listOf(item.text)))
                    }
                }

            // 没有坐标的（个别 H5 节点）追加在末尾，各自成行，不参与位置推断
            withoutPosition.forEach { rows.add(Row(top = PageText.NO_POSITION, cells = listOf(it.text))) }

            return BillFieldParser(rows = rows, items = snapshot.items)
        }
    }
}
