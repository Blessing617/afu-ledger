package com.afu.ledger.data.repository

import com.afu.ledger.data.local.dao.BudgetDao
import com.afu.ledger.data.repository.mapper.toDomain
import com.afu.ledger.data.repository.mapper.toEntity
import com.afu.ledger.domain.budget.BudgetPeriod
import com.afu.ledger.domain.budget.BudgetProgress
import com.afu.ledger.domain.budget.BudgetWindow
import com.afu.ledger.domain.model.Budget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

/** 预算的读写与「本期已用」聚合。 */
class BudgetRepository(
    private val budgetDao: BudgetDao,
) {

    fun observeAll(): Flow<List<Budget>> =
        budgetDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeEnabled(): Flow<List<Budget>> =
        budgetDao.observeEnabled().map { list -> list.map { it.toDomain() } }

    suspend fun find(id: Long): Budget? = budgetDao.findById(id)?.toDomain()

    suspend fun create(budget: Budget): Long = budgetDao.insert(budget.copy(id = 0).toEntity())

    suspend fun save(budget: Budget) {
        val existing = budgetDao.findById(budget.id) ?: return
        budgetDao.update(budget.toEntity(now = System.currentTimeMillis()).copy(
            createdAt = existing.createdAt,
            updatedAt = System.currentTimeMillis(),
        ))
    }

    suspend fun delete(id: Long) = budgetDao.deleteById(id)

    suspend fun usedCents(budget: Budget, window: BudgetWindow): Long =
        if (budget.isTotalBudget) {
            budgetDao.sumExpense(window.startMillis, window.endMillisExclusive)
        } else {
            budgetDao.sumExpenseInCategories(
                fromMillis = window.startMillis,
                toMillis = window.endMillisExclusive,
                categoryIds = budget.categoryIds,
            )
        }

    suspend fun progress(
        budget: Budget,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): BudgetProgress {
        val window = BudgetPeriod.windowOf(budget, today, zone)
        return BudgetProgress(budget = budget, window = window, usedCents = usedCents(budget, window))
    }

    suspend fun progressOfAll(
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<BudgetProgress> =
        budgetDao.findAll()
            .map { it.toDomain() }
            .filter { it.enabled }
            .map { progress(it, today, zone) }
}
