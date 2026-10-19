package com.afu.ledger.auto

/**
 * 从账单页文本里取「交易单号」。
 *
 * 交易单号是**同一张账单最稳定的身份**：金额、商户名都可能被 OCR 读错，
 * 而单号一旦取到，同一张账单无论打开多少次都是同一个值。
 * 去重用它做指纹，才能做到「忽略过就真的不再弹」。
 *
 * 只从标签的值里取，不做全页盲搜：账单页里还有商品编号、商户单号等长数字
 * （实测支付宝「商品说明」里有 `【称重】_大米先生…_124967_011401109`），
 * 盲搜很容易取错，而**取错单号的后果是去重失效**（宁可返回 null）。
 */
object TradeNoExtractor {

    /** 纯数字串，至少 12 位 —— 短于这个长度的数字基本都是日期或序号。 */
    private val digits = Regex("""\d{12,}""")

    /** 兜底接受的最小长度。 */
    private const val FALLBACK_MIN_LENGTH = 16

    fun extract(fields: BillFieldParser): String? {
        for (label in PaymentPageRules.tradeNoLabels) {
            for (value in fields.valueCandidates(label)) {
                val compact = value.replace(" ", "")
                val found = digits.find(compact)?.value
                if (found != null) return found
            }
        }
        // **不做「整页唯一长数字就当单号」的兜底。**
        //
        // 真机踩过：微信账单页的「商品」字段里就带着
        // `美团/大众点评点餐订` + `单-08295008045412020247876`。
        // 上半屏裁剪把「交易单号」标签裁掉时，兜底会把商品编号当交易单号。
        // 而**取错单号比没有单号危险得多**：去重指纹从此认死一个错误编号，
        // 同一张账单每次打开都算新指纹，反复弹窗。
        //
        // 没有单号时指纹退化为「金额 + 方向 + 账单时间」，已经足够区分真实账单。
        return null
    }

    /** 兼容旧调用：直接从文本行列表取。 */
    fun extract(texts: List<String>): String? =
        extract(BillFieldParser.parse(PageSnapshot.ofTexts("", texts)))
}
