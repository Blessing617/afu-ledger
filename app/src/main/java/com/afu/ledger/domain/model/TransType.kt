package com.afu.ledger.domain.model

/** 交易方向。 */
enum class TransType {
    /** 收入：账户余额增加。 */
    INCOME,

    /** 支出：账户余额减少。 */
    EXPENSE,

    /**
     * 资产转移：从一个账户转到另一个账户。
     *
     * 余额上「转出减、转入加」，**总资产与净资产不变**；收支统计（首页、明细结余、
     * 报表、预算）一律不计入——钱没花掉，只是换了个容器。
     */
    TRANSFER,
}
