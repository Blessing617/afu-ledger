package com.afu.ledger.auto.ocr

import com.afu.ledger.auto.PageText

/**
 * OCR 文本的字符级纠错，纯 Kotlin。
 *
 * 只改「数字上下文」里的形近字符：OCR 常把小数点的 `0` 读成 `O`、把 `1` 读成 `l`。
 * 无脑全文替换会把「COCO都可」改成「C0C0都可」，所以每个替换都要先看左右有没有数字。
 *
 * 这里刻意**不做**激进的猜测：不做 `Z→2` 这类弱形近替换，因为那不是纠错而是编造。
 * 纠错后仍然过不了严格校验的文本会在后面被直接放弃。
 */
object OcrTextSanitizer {

    /** 判定「数字上下文」时左右各看几个字符。 */
    private const val CONTEXT_RADIUS = 3

    private const val ZERO_LIKE = "OoD"
    private const val ONE_LIKE = "lI|"
    private const val FIVE_LIKE = "S"
    private const val EIGHT_LIKE = "B"
    private const val YUAN_LIKE = "Yy"

    /**
     * 清洗一行 OCR 文本。
     *
     * 顺序很重要：先转半角，再做形近字符替换，最后去掉首尾空白。
     */
    fun sanitize(raw: String): String {
        if (raw.isEmpty()) return raw
        val chars = toHalfWidth(raw).toCharArray()

        for (index in chars.indices) {
            val char = chars[index]
            if (char.isDigit()) continue

            // 负号被 OCR 读成横线类字符：真机实测微信账单金额「-23.90」被读成「一23.90」。
            // 只有当它**紧跟数字且前面不是数字**时才当作负号 ——
            // 「一元」「一起」「一共」里的「一」后面是汉字，不会被误替换。
            if (char == '一' || char == '—' || char == '–' || char == '－') {
                val nextDigit = chars.getOrNull(index + 1)?.isDigit() == true
                val prevDigit = chars.getOrNull(index - 1)?.isDigit() == true
                if (nextDigit && !prevDigit) {
                    chars[index] = '-'
                    continue
                }
            }

            val digitNearby = hasDigitNearby(chars, index)
            when {
                // 紧邻数字的 Y/y 是货币符号的误识别
                char in YUAN_LIKE && adjacentIsDigit(chars, index) -> chars[index] = '¥'
                !digitNearby -> Unit
                char in ZERO_LIKE -> chars[index] = '0'
                char in ONE_LIKE -> chars[index] = '1'
                char in FIVE_LIKE -> chars[index] = '5'
                char in EIGHT_LIKE -> chars[index] = '8'
            }
        }
        return String(chars).trim()
    }

    /** 整段文本逐行清洗。 */
    fun sanitizeAll(lines: List<String>): List<String> = lines.map { sanitize(it) }

    /**
     * 逐行清洗并**保留坐标**。
     *
     * OCR 行已经带位置，丢了就只能靠行号分组；保留它才能和节点通道共用
     * 「按视觉行还原标签→值」的那套逻辑。
     */
    fun sanitizeItems(items: List<PageText>): List<PageText> =
        items.map { it.copy(text = sanitize(it.text)) }.filter { it.text.isNotEmpty() }

    private fun adjacentIsDigit(chars: CharArray, index: Int): Boolean {
        val next = chars.getOrNull(index + 1)
        if (next != null && next.isDigit()) return true
        val previous = chars.getOrNull(index - 1)
        return previous != null && previous.isDigit()
    }

    private fun hasDigitNearby(chars: CharArray, index: Int): Boolean {
        val from = (index - CONTEXT_RADIUS).coerceAtLeast(0)
        val to = (index + CONTEXT_RADIUS).coerceAtMost(chars.lastIndex)
        for (i in from..to) {
            if (i != index && chars[i].isDigit()) return true
        }
        return false
    }

    /** 全角数字与常见符号转半角。 */
    private fun toHalfWidth(text: String): String {
        val builder = StringBuilder(text.length)
        for (char in text) {
            val replaced = when (char) {
                in '０'..'９' -> '0' + (char - '０')
                '．' -> '.'
                '－' -> '-'
                '＋' -> '+'
                '，' -> ','
                '：' -> ':'
                '％' -> '%'
                '　' -> ' '
                else -> char
            }
            builder.append(replaced)
        }
        return builder.toString()
    }
}
