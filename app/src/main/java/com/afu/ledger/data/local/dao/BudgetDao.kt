package com.afu.ledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.afu.ledger.data.local.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Insert
    suspend fun insert(budget: BudgetEntity): Long

    @Update
    suspend fun update(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM budgets ORDER BY enabled DESC, sortOrder, id")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE enabled = 1 ORDER BY sortOrder, id")
    fun observeEnabled(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets ORDER BY enabled DESC, sortOrder, id")
    suspend fun findAll(): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun findById(id: Long): BudgetEntity?

    @Query("SELECT COUNT(*) FROM budgets")
    suspend fun count(): Int

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()

    /** 区间内的全部支出合计（总预算用）。 */
    @Query(
        "SELECT IFNULL(SUM(amountCents), 0) FROM transactions " +
            "WHERE type = 'EXPENSE' AND occurredAt >= :fromMillis AND occurredAt < :toMillis"
    )
    suspend fun sumExpense(fromMillis: Long, toMillis: Long): Long

    /** 区间内指定分类的支出合计（分类预算用）。 */
    @Query(
        "SELECT IFNULL(SUM(amountCents), 0) FROM transactions " +
            "WHERE type = 'EXPENSE' AND occurredAt >= :fromMillis AND occurredAt < :toMillis " +
            "AND categoryId IN (:categoryIds)"
    )
    suspend fun sumExpenseInCategories(
        fromMillis: Long,
        toMillis: Long,
        categoryIds: List<Long>,
    ): Long
}
