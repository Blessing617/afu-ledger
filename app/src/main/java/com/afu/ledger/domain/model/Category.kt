package com.afu.ledger.domain.model

/** 交易分类，区分为收入分类与支出分类两套。 */
data class Category(
    val id: Long = 0L,
    val name: String,
    val type: TransType,
    val iconKey: String,
    val colorArgb: Long,
    val sortOrder: Int = 0,
    /** 是否为内置预置分类。预置分类允许重命名，但不建议删除。 */
    val preset: Boolean = false,
)
