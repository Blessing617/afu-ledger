package com.afu.ledger.domain.model

/** 账户类型，决定余额的正负语义与展示方式。 */
enum class AccountType {
    /** 现金：随身现金、零钱。 */
    CASH,

    /** 银行卡 / 电子钱包：有正的可用余额。 */
    BANK,

    /** 信用卡：余额通常为负，表示欠款。 */
    CREDIT,
}
