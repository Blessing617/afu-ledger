package com.afu.ledger.auto.ocr

/**
 * OCR 文本的「金额可信度」闸门。
 *
 * 存在的理由：`AmountParser` 的正则会在 `¥4Z.50` 里匹配到 `¥4`，把一个明显有问题的
 * 文本读成 4 元。无障碍通道不会遇到这种输入（文本本来就是干净的），但 OCR 会。
 * 所以 OCR 侧先在这里挡一道，挡不住就让整次识别放弃 —— 宁可漏记，不可记错。
 */
object OcrAmountGuard {

    /** 含货币符号的片段里混进了字母：几乎必然是被认错的数字。 */
    private val symbolWithLetter = Regex("""[¥￥]\s*-?[\d.,]*[A-Za-z]""")

    /** 货币符号后面的金额片段本身（用于位数检查）。 */
    private val symbolAmount = Regex("""[¥￥]\s*(-?)(\d[\d,]*(?:\.(\d+))?)""")

    /** 整数位上限：超过 6 位（即 1 百万元）不可能是日常记账。 */
    private const val MAX_INTEGER_DIGITS = 6

    /** 小数位上限。 */
    private const val MAX_FRACTION_DIGITS = 2

    sealed interface Verdict {
        data object Ok : Verdict

        /** 文本里有看起来像金额、但又不可信的东西。 */
        data class Rejected(val reason: String) : Verdict
    }

    fun check(lines: List<String>): Verdict {
        for (line in lines) {
            if (symbolWithLetter.containsMatchIn(line)) {
                return Verdict.Rejected("金额里混入字母：$line")
            }
        }

        for (line in lines) {
            val match = symbolAmount.find(line) ?: continue
            val integerPart = match.groupValues[2].replace(",", "")
            val fractionPart = match.groupValues[3]

            val dotIndex = integerPart.indexOf('.')
            val integerDigits = if (dotIndex >= 0) dotIndex else integerPart.length
            if (integerDigits > MAX_INTEGER_DIGITS) {
                return Verdict.Rejected("金额位数过多：$line")
            }
            if (fractionPart.length > MAX_FRACTION_DIGITS) {
                return Verdict.Rejected("小数位过多：$line")
            }
        }
        return Verdict.Ok
    }
}
