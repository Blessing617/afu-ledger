package com.afu.ledger.domain.model

/** 交易方向。 */
enum class TransType {
    /** 收入：账户余额增加。 */
    INCOME,

    /** 支出：账户余额减少。 */
    EXPENSE,
}
