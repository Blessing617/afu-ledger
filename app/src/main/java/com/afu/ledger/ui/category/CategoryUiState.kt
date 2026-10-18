package com.afu.ledger.ui.category

import com.afu.ledger.domain.model.Category

data class CategoryUiState(
    val expense: List<Category> = emptyList(),
    val income: List<Category> = emptyList(),
    /** 一次性提示（如删除被拒），消费后置空。 */
    val message: String? = null,
    val loaded: Boolean = false,
)
