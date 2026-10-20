package com.afu.ledger.data.repository

import com.afu.ledger.data.local.dao.CategoryDao
import com.afu.ledger.data.local.entity.CategoryEntity
import com.afu.ledger.data.repository.mapper.toDomain
import com.afu.ledger.data.repository.mapper.toEntity
import com.afu.ledger.domain.model.Category
import com.afu.ledger.domain.model.TransType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CategoryRepository(
    private val categoryDao: CategoryDao,
) {

    fun observeAll(): Flow<List<Category>> =
        categoryDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeByType(type: TransType): Flow<List<Category>> =
        categoryDao.observeByType(type.name).map { list -> list.map { it.toDomain() } }

    suspend fun create(category: Category): Long = categoryDao.insert(category.toEntity())

    suspend fun save(category: Category) = categoryDao.update(category.toEntity())

    /**
     * 删除分类。
     *
     * 被交易引用的分类不允许删除，返回 false，调用方负责提示用户。
     */
    suspend fun deleteIfUnused(categoryId: Long): Boolean {
        if (categoryDao.countUsage(categoryId) > 0) return false
        categoryDao.deleteById(categoryId)
        return true
    }

    /** 首次启动时写入预置分类；已有数据时直接跳过。 */
    suspend fun ensurePresetCategories() {
        if (categoryDao.count() > 0) return
        categoryDao.insertAll(presetCategories())
    }

    private fun presetCategories(): List<CategoryEntity> {
        val expense = listOf(
            Triple("餐饮", "restaurant", 0xFFFF8A65),
            Triple("交通", "directions_bus", 0xFF4FC3F7),
            Triple("购物", "shopping_bag", 0xFFF06292),
            Triple("居住", "home", 0xFF9575CD),
            Triple("娱乐", "sports_esports", 0xFF4DB6AC),
            Triple("医疗", "medical_services", 0xFFE57373),
            Triple("教育", "school", 0xFF7986CB),
            Triple("人情", "redeem", 0xFFFFB74D),
            Triple("其他", "more_horiz", 0xFF90A4AE),
        )
        val income = listOf(
            Triple("工资", "payments", 0xFF66BB6A),
            Triple("奖金", "emoji_events", 0xFFFFD54F),
            Triple("理财", "trending_up", 0xFF26A69A),
            Triple("红包", "redeem", 0xFFFF8A65),
            Triple("其他", "more_horiz", 0xFF90A4AE),
        )

        fun build(items: List<Triple<String, String, Long>>, type: TransType) =
            items.mapIndexed { index, (name, icon, color) ->
                CategoryEntity(
                    name = name,
                    type = type.name,
                    iconKey = icon,
                    colorArgb = color,
                    sortOrder = index,
                    preset = true,
                )
            }

        return build(expense, TransType.EXPENSE) + build(income, TransType.INCOME)
    }
}
