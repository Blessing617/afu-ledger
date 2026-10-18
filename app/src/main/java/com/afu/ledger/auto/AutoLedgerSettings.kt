package com.afu.ledger.auto

/** 自动记账的持久化配置。默认关闭，必须用户显式开启。 */
data class AutoLedgerSettings(
    val enabled: Boolean = false,
    val defaultAccountId: Long? = null,
    val defaultCategoryId: Long? = null,
    val recognitionLogEnabled: Boolean = false,
    /**
     * 截图 OCR 通道开关。
     *
     * 默认关闭：截屏是敏感能力，要用户自己打开，并在设置页看到它会截取屏幕做本机识别。
     */
    val ocrEnabled: Boolean = false,
)
