package com.afu.ledger.ui.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afu.ledger.data.repository.CategoryRepository
import com.afu.ledger.domain.model.Category
import com.afu.ledger.domain.model.TransType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoryViewModel(
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CategoryUiState> = combine(
        categoryRepository.observeAll(),
        message,
    ) { categories, msg ->
        CategoryUiState(
            expense = categories.filter { it.type == TransType.EXPENSE },
            income = categories.filter { it.type == TransType.INCOME },
            message = msg,
            loaded = true,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CategoryUiState(),
    )

    fun create(type: TransType, name: String, iconKey: String, colorArgb: Long) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val current = uiState.value
            val sameGroup = if (type == TransType.EXPENSE) current.expense else current.income
            // 同组内不允许重名
            if (sameGroup.any { it.name == name.trim() }) {
                message.value = "已存在同名分类"
                return@launch
            }
            categoryRepository.create(
                Category(
                    name = name.trim(),
                    type = type,
                    iconKey = iconKey,
                    colorArgb = colorArgb,
                    sortOrder = sameGroup.size,
                ),
            )
        }
    }

    fun rename(category: Category, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            categoryRepository.save(category.copy(name = newName.trim()))
        }
    }

    fun delete(category: Category) {
        viewModelScope.launch {
            val removed = categoryRepository.deleteIfUnused(category.id)
            if (!removed) {
                message.value = "「${category.name}」已被交易使用，无法删除"
            }
        }
    }

    fun consumeMessage() {
        message.value = null
    }
}
