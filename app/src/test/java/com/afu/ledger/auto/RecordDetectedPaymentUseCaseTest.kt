package com.afu.ledger.auto

import com.afu.ledger.data.local.dao.AccountBalance
import com.afu.ledger.data.local.dao.AccountDao
import com.afu.ledger.data.local.dao.CategoryDao
import com.afu.ledger.data.local.dao.TransactionDao
import com.afu.ledger.data.local.entity.AccountEntity
import com.afu.ledger.data.local.entity.CategoryEntity
import com.afu.ledger.data.local.entity.TransactionEntity
import com.afu.ledger.data.repository.AccountRepository
import com.afu.ledger.data.repository.CategoryRepository
import com.afu.ledger.data.repository.TransactionRepository
import com.afu.ledger.domain.model.Money
import com.afu.ledger.domain.model.TransType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordDetectedPaymentUseCaseTest {

    private val draft = PaymentDraft(
        amount = Money(2550),
        direction = TransType.EXPENSE,
        merchant = "瑞幸咖啡",
        sourcePackage = PaymentPageRules.WECHAT_PACKAGE,
        pageType = PaymentPageType.BILL_DETAIL,
        occurredAt = 1_700_000_000_000L,
        occurredAtSource = TimeSource.PAGE,
        capturedAt = 1_000L,
    )

    private fun account(id: Long = 1L, archived: Boolean = false) = AccountEntity(
        id = id,
        name = "现金",
        type = "CASH",
        initialBalanceCents = 50_000,
        colorArgb = 0,
        iconKey = "cash",
        archived = archived,
        createdAt = 0,
    )

    private fun category(id: Long, name: String, type: String = "EXPENSE") = CategoryEntity(
        id = id,
        name = name,
        type = type,
        iconKey = "icon",
        colorArgb = 0,
    )

    private fun buildUseCase(
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        transactions: MutableList<TransactionEntity> = mutableListOf(),
    ): RecordDetectedPaymentUseCase = RecordDetectedPaymentUseCase(
        transactionRepository = TransactionRepository(FakeTransactionDao(transactions)),
        accountRepository = AccountRepository(FakeAccountDao(accounts)),
        categoryRepository = CategoryRepository(FakeCategoryDao(categories)),
    )

    @Test
    fun `账户与分类有效时写入支出`() = runBlocking {
        val transactions = mutableListOf<TransactionEntity>()
        val useCase = buildUseCase(
            accounts = listOf(account()),
            categories = listOf(category(10, "餐饮"), category(11, "其他")),
            transactions = transactions,
        )

        val result = useCase.execute(draft, accountId = 1L, categoryId = 10L)

        assertTrue(result is RecordResult.Success)
        val saved = transactions.single()
        assertEquals(2550L, saved.amountCents)
        assertEquals(1L, saved.accountId)
        assertEquals(10L, saved.categoryId)
        assertEquals("EXPENSE", saved.type)
        assertEquals("瑞幸咖啡", saved.note)
        // 用账单时间（draft.occurredAt），不是识别时刻（capturedAt = 1_000L）
        assertEquals(1_700_000_000_000L, saved.occurredAt)
    }

    @Test
    fun `账户不存在时返回 NoAccount`() = runBlocking {
        val useCase = buildUseCase(
            accounts = listOf(account()),
            categories = listOf(category(10, "餐饮")),
        )

        assertEquals(RecordResult.NoAccount, useCase.execute(draft, accountId = 99L, categoryId = 10L))
    }

    @Test
    fun `账户已归档时返回 NoAccount`() = runBlocking {
        val useCase = buildUseCase(
            accounts = listOf(account(archived = true)),
            categories = listOf(category(10, "餐饮")),
        )

        assertEquals(RecordResult.NoAccount, useCase.execute(draft, accountId = 1L, categoryId = 10L))
    }

    @Test
    fun `分类失效时回退到其他`() = runBlocking {
        val transactions = mutableListOf<TransactionEntity>()
        val useCase = buildUseCase(
            accounts = listOf(account()),
            categories = listOf(category(10, "餐饮"), category(11, "其他")),
            transactions = transactions,
        )

        useCase.execute(draft, accountId = 1L, categoryId = 999L)

        assertEquals(11L, transactions.single().categoryId)
    }

    @Test
    fun `分类为空时回退到第一个支出分类`() = runBlocking {
        val transactions = mutableListOf<TransactionEntity>()
        val useCase = buildUseCase(
            accounts = listOf(account()),
            categories = listOf(category(10, "餐饮")),
            transactions = transactions,
        )

        useCase.execute(draft, accountId = 1L, categoryId = null)

        assertEquals(10L, transactions.single().categoryId)
    }

    @Test
    fun `没有支出分类时失败且不写库`() = runBlocking {
        val transactions = mutableListOf<TransactionEntity>()
        val useCase = buildUseCase(
            accounts = listOf(account()),
            categories = listOf(category(20, "工资", type = "INCOME")),
            transactions = transactions,
        )

        val result = useCase.execute(draft, accountId = 1L, categoryId = null)

        assertTrue(result is RecordResult.Failed)
        assertTrue(transactions.isEmpty())
    }
}

private class FakeAccountDao(private val accounts: List<AccountEntity>) : AccountDao {
    override suspend fun insert(account: AccountEntity): Long = account.id

    override suspend fun update(account: AccountEntity) = Unit

    override suspend fun deleteById(id: Long) = Unit

    override suspend fun deleteIncomingTransfers(id: Long) = Unit

    override suspend fun deleteWithTransfers(id: Long) = deleteById(id)

    override suspend fun countTransactions(id: Long): Int = 0

    override suspend fun nextSortOrder(): Int = accounts.size

    override suspend fun setSortOrder(id: Long, sortOrder: Int) = Unit

    override fun observeAll(): Flow<List<AccountEntity>> = flowOf(accounts)

    override fun observeActive(): Flow<List<AccountEntity>> =
        flowOf(accounts.filter { !it.archived })

    override suspend fun findById(id: Long): AccountEntity? = accounts.firstOrNull { it.id == id }

    override fun observeBalances(): Flow<List<AccountBalance>> = flowOf(emptyList())

    override suspend fun findAll(): List<AccountEntity> = accounts

    override suspend fun count(): Int = accounts.size

    override suspend fun deleteAll() = Unit
}

private class FakeCategoryDao(private val categories: List<CategoryEntity>) : CategoryDao {
    override suspend fun insert(category: CategoryEntity): Long = category.id

    override suspend fun insertAll(categories: List<CategoryEntity>) = Unit

    override suspend fun update(category: CategoryEntity) = Unit

    override suspend fun deleteById(id: Long) = Unit

    override fun observeAll(): Flow<List<CategoryEntity>> = flowOf(categories)

    override fun observeByType(type: String): Flow<List<CategoryEntity>> =
        flowOf(categories.filter { it.type == type })

    override suspend fun findById(id: Long): CategoryEntity? =
        categories.firstOrNull { it.id == id }

    override suspend fun findTransferCategory(): CategoryEntity? =
        categories.firstOrNull { it.type == "TRANSFER" }

    override suspend fun count(): Int = categories.size

    override suspend fun countUsage(categoryId: Long): Int = 0

    override suspend fun findAll(): List<CategoryEntity> = categories

    override suspend fun deleteAll() = Unit
}

private class FakeTransactionDao(
    private val transactions: MutableList<TransactionEntity>,
) : TransactionDao {
    override suspend fun insert(transaction: TransactionEntity): Long {
        transactions.add(transaction)
        return transactions.size.toLong()
    }

    override suspend fun insertIgnoringDuplicate(transaction: TransactionEntity): Long {
        val duplicate = transaction.scheduleRuleId != null && transactions.any {
            it.scheduleRuleId == transaction.scheduleRuleId && it.occurredAt == transaction.occurredAt
        }
        if (duplicate) return -1L
        transactions.add(transaction)
        return transactions.size.toLong()
    }

    override suspend fun findByRule(ruleId: Long): List<TransactionEntity> =
        transactions.filter { it.scheduleRuleId == ruleId }

    override suspend fun update(transaction: TransactionEntity) = Unit

    override suspend fun deleteById(id: Long) = Unit

    override fun observeAll(): Flow<List<TransactionEntity>> = flowOf(transactions.toList())

    override fun observeBetween(fromInclusive: Long, toExclusive: Long): Flow<List<TransactionEntity>> =
        flowOf(transactions.toList())

    override suspend fun findById(id: Long): TransactionEntity? =
        transactions.firstOrNull { it.id == id }

    override suspend fun sumByType(type: String, fromInclusive: Long, toExclusive: Long): Long = 0L

    override suspend fun count(): Int = transactions.size

    override suspend fun page(limit: Int, offset: Int): List<TransactionEntity> =
        transactions.sortedBy { it.id }.drop(offset).take(limit)

    override suspend fun deleteAll() = Unit
}
