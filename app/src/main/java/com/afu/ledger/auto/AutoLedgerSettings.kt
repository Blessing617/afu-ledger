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
    /**
     * 隐藏常驻通知。
     *
     * 前台服务的通知是保活手段之一（进程优先级）。用户嫌通知碍事可以关掉，
     * 代价是被系统冻结/强杀后恢复能力变差 —— 这一点要在设置页如实写明。
     */
    val hideKeepAliveNotification: Boolean = false,
)
