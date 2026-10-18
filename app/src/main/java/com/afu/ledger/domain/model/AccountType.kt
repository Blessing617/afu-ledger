package com.afu.ledger.domain.model

/**
 * 账户类型。
 *
 * 主力三类：微信、支付宝、银行卡。
 * CASH / CREDIT 保留用于兼容历史数据，新建账户时默认选微信。
 */
enum class AccountType {
    /** 微信零钱 / 零钱通。 */
    WECHAT,

    /** 支付宝余额 / 余额宝。 */
    ALIPAY,

    /** 银行卡（借记卡/储蓄卡）。 */
    BANK,

    /** 现金（兼容旧数据）。 */
    CASH,

    /** 信用卡（兼容旧数据，余额通常为负）。 */
    CREDIT,
}
