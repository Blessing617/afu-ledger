package com.afu.ledger.auto

import com.afu.ledger.domain.model.TransType

/**
 * 页面自带的分类文案 → 账本分类名。
 *
 * 支付宝的账单详情页有「账单分类」字段（`餐饮美食`、`数码电器`…），
 * 微信的账单页没有分类字段，所以这张表主要服务支付宝。
 *
 * 映射**只按方向分开**：同一个「转账红包」在支出侧该归「人情」（我发出去的），
 * 在收入侧该归「红包」。
 *
 * 映射不到就返回 null，由调用方回退到默认分类 —— 宁可归「其他」，
 * 也不要把「network购物」这种模糊词硬塞进「购物」里制造错账。
 */
object BillCategoryMapper {

    /** 支付宝「账单分类」常见值 → 账本支出分类。 */
    private val expense: Map<String, String> = mapOf(
        "餐饮美食" to "餐饮",
        "交通出行" to "交通",
        "数码电器" to "购物",
        "日用百货" to "购物",
        "服饰装扮" to "购物",
        "美容美发" to "购物",
        "母婴亲子" to "购物",
        "家居家装" to "居住",
        "住房物业" to "居住",
        "充值缴费" to "居住",
        "医疗健康" to "医疗",
        "文化休闲" to "娱乐",
        "酒店旅游" to "娱乐",
        "运动户外" to "娱乐",
        "教育培训" to "教育",
        "转账红包" to "人情",
        "亲友代付" to "人情",
        "投资理财" to "其他",
        "保险" to "其他",
        "商业服务" to "其他",
        "生活服务" to "其他",
        "公共事业" to "其他",
        "退款" to "其他",
    )

    /** 支付宝「账单分类」常见值 → 账本收入分类。 */
    private val income: Map<String, String> = mapOf(
        "转账红包" to "红包",
        "退款" to "其他",
        "投资收益" to "理财",
        "投资理财" to "理财",
        "工资" to "工资",
        "奖金" to "奖金",
        "其他" to "其他",
    )

    /**
     * 映射到账本分类名；映射不到返回 null。
     *
     * 先精确匹配，再退一步做「包含」匹配（支付宝会改文案，比如
     * 「餐饮美食」变成「餐饮美食酒水」时仍能落到餐饮）。
     * 包含匹配要求键长度 ≥ 3，避免「保险」这种短词误伤。
     */
    fun map(pageCategory: String?, direction: TransType): String? {
        val raw = pageCategory?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val table = if (direction == TransType.INCOME) income else expense

        table[raw]?.let { return it }
        return table.entries
            .filter { it.key.length >= 3 && raw.contains(it.key) }
            .maxByOrNull { it.key.length }
            ?.value
    }
}
