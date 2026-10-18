package com.afu.ledger.auto.ocr

/**
 * DBNet（PP-OCR det）概率图后处理：二值化 → 连通域 → 轴对齐外扩。
 *
 * 没有实现旋转框：手机页面上的文字基本水平，轴对齐矩形够用，
 * 而且少一个最小外接矩形算法就少一处可能算错的地方。
 */
object DbPostProcessor {

    /** 概率图二值化阈值。 */
    const val BINARIZE_THRESHOLD: Float = 0.3f

    /** 连通域平均得分下限，低于此认为不是文字。 */
    const val BOX_SCORE_THRESHOLD: Float = 0.5f

    /** 外扩比例，对应 PaddleOCR 的 unclip_ratio。 */
    const val UNCLIP_RATIO: Float = 1.6f

    /** 过滤碎块的最小边长（det 尺度下）。 */
    private const val MIN_SIDE = 3

    data class DetBox(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val score: Float,
    ) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
        val area: Int get() = width * height
    }

    /**
     * 从概率图里抠出文字块。
     *
     * @param prob 长度为 `width * height` 的概率图
     */
    fun findBoxes(
        prob: FloatArray,
        width: Int,
        height: Int,
        threshold: Float = BINARIZE_THRESHOLD,
    ): List<DetBox> {
        if (prob.size < width * height) return emptyList()

        val visited = BooleanArray(width * height)
        val stack = IntArray(width * height)
        val boxes = ArrayList<DetBox>(32)

        for (start in 0 until width * height) {
            if (visited[start] || prob[start] <= threshold) continue

            var top = 0
            stack[top++] = start
            visited[start] = true

            var minX = width
            var minY = height
            var maxX = -1
            var maxY = -1
            var sum = 0.0
            var count = 0

            while (top > 0) {
                val index = stack[--top]
                val x = index % width
                val y = index / width

                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                sum += prob[index]
                count++

                for (dy in -1..1) {
                    val ny = y + dy
                    if (ny < 0 || ny >= height) continue
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        if (nx < 0 || nx >= width) continue
                        val nIndex = ny * width + nx
                        if (visited[nIndex] || prob[nIndex] <= threshold) continue
                        visited[nIndex] = true
                        stack[top++] = nIndex
                    }
                }
            }

            val boxW = maxX - minX + 1
            val boxH = maxY - minY + 1
            if (boxW < MIN_SIDE || boxH < MIN_SIDE) continue
            boxes.add(
                DetBox(
                    left = minX,
                    top = minY,
                    right = maxX + 1,
                    bottom = maxY + 1,
                    score = (sum / count).toFloat(),
                ),
            )
        }
        return boxes
    }

    /** 按 unclip 规则向外扩，让框把中文字符的边缘包住。 */
    fun unclip(box: DetBox, ratio: Float = UNCLIP_RATIO): DetBox {
        val perimeter = 2 * (box.width + box.height)
        if (perimeter <= 0) return box
        val distance = box.area * ratio / perimeter
        val d = distance.toInt()
        return DetBox(
            left = box.left - d,
            top = box.top - d,
            right = box.right + d,
            bottom = box.bottom + d,
            score = box.score,
        )
    }
}
