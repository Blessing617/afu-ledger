package com.afu.ledger.schedule

import com.afu.ledger.data.local.dao.AccountBalance
import com.afu.ledger.data.local.dao.AccountDao
import com.afu.ledger.data.local.dao.CategoryDao
import com.afu.ledger.data.local.entity.AccountEntity
import com.afu.ledger.data.local.entity.CategoryEntity
import com.afu.ledger.data.repository.AccountRepository
import com.afu.ledger.data.repository.CategoryRepository
import com.afu.ledger.data.repository.ScheduleRuleRepository
import com.afu.ledger.data.repository.TransactionRepository
import com.afu.ledger.data.local.dao.ScheduleRuleDao
import com.afu.ledger.data.local.dao.TransactionDao
import com.afu.ledger.data.local.entity.ScheduleRuleEntity
import com.afu.ledger.data.local.entity.TransactionEntity
import com.afu.ledger.domain.model.Money
import com.afu.ledger.domain.model.TransType
import com.afu.ledger.domain.schedule.ScheduleBehavior
import com.afu.ledger.domain.schedule.ScheduleFrequency
import com.afu.ledger.domain.schedule.ScheduleRule
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunDueSchedulesUseCaseTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private val account = AccountEntity(
        id = 1L,
        name = "招行卡",
        type = "BANK",
        initialBalanceCents = 1_000_00,
        colorArgb = 0L,
        iconKey = "bank",
        archived = false,
        sortOrder = 0,
        createdAt = 0L,
    )

    private val category = CategoryEntity(
        id = 10L,
        name = "居住",
        type = "EXPENSE",
        iconKey = "home",
        colorArgb = 0L,
        sortOrder = 0,
        preset = true,
    )

    private fun rule(
        id: Long = 0L,
        behavior: ScheduleBehavior = ScheduleBehavior.AUTO_POST,
        max: Int? = null,
        numberOfDays: Int = 0,
    ) = ScheduleRuleEntity(
        id = id,
        accountId = account.id,
        categoryId = category.id,
        type = "EXPENSE",
        amountCents = 300_000,
        note = "房租",
        frequency = ScheduleFrequency.DAILY.name,
        interval = 1,
        dayOfMonth = 1,
        monthOfYear = 1,
        minuteOfDay = 9 * 60,
        startEpochDay = LocalDate.of(2026, 10, 18).toEpochDay(),
        maxOccurrences = max,
        behavior = behavior.name,
        enabled = true,
        lastGeneratedEpochDay = null,
        generatedCount = 0,
    )

    private class Fixture(
        val accountDao: FakeAccountDao,
        val categoryDao: FakeCategoryDao,
        val ruleDao: FakeScheduleRuleDao,
        val transactionDao: FakeTransactionDao,
        val pendingStore: FakePendingStore,
        val useCase: RunDueSchedulesUseCase,
        val transactions: MutableList<TransactionEntity>,
    )

    private fun fixture(
        rule: ScheduleRuleEntity,
        now: LocalDateTime = LocalDateTime.of(2026, 10, 20, 12, 0),
        accounts: List<AccountEntity> = listOf(account),
        categories: List<CategoryEntity> = listOf(category),
    ): Fixture {
        val accountDao = FakeAccountDao(accounts.toMutableList())
        val categoryDao = FakeCategoryDao(categories.toMutableList())
        val ruleDao = FakeScheduleRuleDao(mutableListOf(rule))
        val transactionDao = FakeTransactionDao(mutableListOf())
        val pendingStore = FakePendingStore()

        val useCase = RunDueSchedulesUseCase(
            rules = ScheduleRuleRepository(ruleDao),
            transactions = TransactionRepository(transactionDao),
            accounts = AccountRepository(accountDao),
            categories = CategoryRepository(categoryDao),
            pending = pendingStore,
            now = { now },
            zone = { zone },
        )
        return Fixture(
            accountDao = accountDao,
            categoryDao = categoryDao,
            ruleDao = ruleDao,
            transactionDao = transactionDao,
            pendingStore = pendingStore,
            useCase = useCase,
            transactions = transactionDao.items,
        )
    }

    @Test
    fun `关机三天后自动补三笔且按计划时刻入账`() = runBlocking {
        val f = fixture(rule())

        val report = f.useCase.run()

        assertEquals(3, report.autoPosted)
        assertEquals(3, f.transactions.size)
        assertEquals(
            listOf(
                LocalDate.of(2026, 10, 18),
                LocalDate.of(2026, 10, 19),
                LocalDate.of(2026, 10, 20),
            ),
            f.transactions.sortedBy { it.occurredAt }.map {
                java.time.Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate()
            },
        )
        // 18/19 是补记，20 是当天的正常执行
        assertEquals(listOf(true, true, false), f.transactions.sortedBy { it.occurredAt }.map { it.backfilled })
        assertTrue(f.transactions.all { it.sourceType == "SCHEDULED" && it.scheduleRuleId == 0L })
    }

    @Test
    fun `再次执行不会重复入账`() = runBlocking {
        val f = fixture(rule())

        f.useCase.run()
        val second = f.useCase.run()

        assertEquals(0, second.autoPosted)
        assertEquals(3, f.transactions.size)
    }

    @Test
    fun `提醒型规则只落待确认不写交易`() = runBlocking {
        val f = fixture(rule(behavior = ScheduleBehavior.NEED_CONFIRM))

        val report = f.useCase.run()

        assertEquals(3, report.pendingCreated)
        assertEquals(3, f.pendingStore.items.size)
        assertTrue(f.transactions.isEmpty())
    }

    @Test
    fun `账户归档后规则暂停且不生成`() = runBlocking {
        val f = fixture(rule(), accounts = listOf(account.copy(archived = true)))

        val report = f.useCase.run()

        assertEquals(1, report.pausedRules)
        assertEquals(0, report.autoPosted)
        assertTrue(f.transactions.isEmpty())
        val saved = f.ruleDao.items.first()
        assertFalse(saved.enabled)
        assertTrue(saved.pausedReason?.contains("账户") == true)
    }

    @Test
    fun `分类被删除后规则暂停`() = runBlocking {
        val f = fixture(rule(), categories = emptyList())

        val report = f.useCase.run()

        assertEquals(1, report.pausedRules)
        assertTrue(f.transactions.isEmpty())
    }

    @Test
    fun `执行满期数后规则自动结束`() = runBlocking {
        val f = fixture(rule(max = 2))

        f.useCase.run()

        assertEquals(2, f.transactions.size)
        val saved = f.ruleDao.items.first()
        assertFalse(saved.enabled)
        assertEquals(2, saved.generatedCount)
    }

    @Test
    fun `未到点的当天期次不生成`() = runBlocking {
        val f = fixture(rule(), now = LocalDateTime.of(2026, 10, 20, 8, 0))

        val report = f.useCase.run()

        assertEquals(2, report.autoPosted)
        assertEquals(2, f.transactions.size)
    }
}

// ---- 测试替身 ----

private class FakeAccountDao(private val items: MutableList<AccountEntity>) : AccountDao {
    override suspend fun insert(account: AccountEntity): Long {
        items.add(account); return account.id
    }

    override suspend fun update(account: AccountEntity) {
        val index = items.indexOfFirst { it.id == account.id }
        if (index >= 0) items[index] = account
    }

    override suspend fun deleteById(id: Long) {
        items.removeAll { it.id == id }
    }

    override suspend fun countTransactions(id: Long): Int = 0

    override suspend fun nextSortOrder(): Int = items.size

    override suspend fun setSortOrder(id: Long, sortOrder: Int) {
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) items[index] = items[index].copy(sortOrder = sortOrder)
    }

    override fun observeAll(): Flow<List<AccountEntity>> = flowOf(items.toList())

    override fun observeActive(): Flow<List<AccountEntity>> = flowOf(items.filter { !it.archived })

    override suspend fun findById(id: Long): AccountEntity? = items.firstOrNull { it.id == id }

    override fun observeBalances(): Flow<List<AccountBalance>> = flowOf(emptyList())

    override suspend fun findAll(): List<AccountEntity> = items.toList()

    override suspend fun count(): Int = items.size

    override suspend fun deleteAll() {
        items.clear()
    }
}

private class FakeCategoryDao(private val items: MutableList<CategoryEntity>) : CategoryDao {
    override suspend fun insert(category: CategoryEntity): Long {
        items.add(category); return category.id
    }

    override suspend fun insertAll(categories: List<CategoryEntity>) {
        items.addAll(categories)
    }

    override suspend fun update(category: CategoryEntity) {
        val index = items.indexOfFirst { it.id == category.id }
        if (index >= 0) items[index] = category
    }

    override suspend fun deleteById(id: Long) {
        items.removeAll { it.id == id }
    }

    override fun observeAll(): Flow<List<CategoryEntity>> = flowOf(items.toList())

    override fun observeByType(type: String): Flow<List<CategoryEntity>> =
        flowOf(items.filter { it.type == type })

    override suspend fun findById(id: Long): CategoryEntity? = items.firstOrNull { it.id == id }

    override suspend fun count(): Int = items.size

    override suspend fun countUsage(categoryId: Long): Int = 0

    override suspend fun findAll(): List<CategoryEntity> = items.toList()

    override suspend fun deleteAll() {
        items.clear()
    }
}

private class FakeScheduleRuleDao(val items: MutableList<ScheduleRuleEntity>) : ScheduleRuleDao {
    override suspend fun insert(rule: ScheduleRuleEntity): Long {
        items.add(rule); return rule.id
    }

    override suspend fun update(rule: ScheduleRuleEntity) {
        val index = items.indexOfFirst { it.id == rule.id }
        if (index >= 0) items[index] = rule
    }

    override fun observeAll(): Flow<List<ScheduleRuleEntity>> = flowOf(items.toList())

    override suspend fun findAll(): List<ScheduleRuleEntity> = items.toList()

    override suspend fun findEnabled(): List<ScheduleRuleEntity> = items.filter { it.enabled }

    override suspend fun findById(id: Long): ScheduleRuleEntity? = items.firstOrNull { it.id == id }

    override suspend fun deleteById(id: Long) {
        items.removeAll { it.id == id }
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean, reason: String?, now: Long) {
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) items[index] = items[index].copy(enabled = enabled, pausedReason = reason)
    }

    override suspend fun advance(id: Long, epochDay: Long, count: Int, enabled: Boolean, now: Long) {
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) {
            items[index] = items[index].copy(
                lastGeneratedEpochDay = epochDay,
                generatedCount = count,
                enabled = enabled,
            )
        }
    }

    override suspend fun count(): Int = items.size

    override suspend fun deleteAll() {
        items.clear()
    }
}

private class FakeTransactionDao(val items: MutableList<TransactionEntity>) : TransactionDao {
    override suspend fun insert(transaction: TransactionEntity): Long {
        items.add(transaction); return items.size.toLong()
    }

    override suspend fun insertIgnoringDuplicate(transaction: TransactionEntity): Long {
        val duplicate = transaction.scheduleRuleId != null && items.any {
            it.scheduleRuleId == transaction.scheduleRuleId && it.occurredAt == transaction.occurredAt
        }
        if (duplicate) return -1L
        items.add(transaction)
        return items.size.toLong()
    }

    override suspend fun findByRule(ruleId: Long): List<TransactionEntity> =
        items.filter { it.scheduleRuleId == ruleId }

    override suspend fun update(transaction: TransactionEntity) = Unit

    override suspend fun deleteById(id: Long) {
        items.removeAll { it.id == id }
    }

    override fun observeAll(): Flow<List<TransactionEntity>> = flowOf(items.toList())

    override fun observeBetween(fromInclusive: Long, toExclusive: Long): Flow<List<TransactionEntity>> =
        flowOf(items.toList())

    override suspend fun findById(id: Long): TransactionEntity? = items.firstOrNull { it.id == id }

    override suspend fun sumByType(type: String, fromInclusive: Long, toExclusive: Long): Long = 0L

    override suspend fun count(): Int = items.size

    override suspend fun page(limit: Int, offset: Int): List<TransactionEntity> =
        items.sortedBy { it.id }.drop(offset).take(limit)

    override suspend fun deleteAll() {
        items.clear()
    }
}

private class FakePendingStore : PendingOccurrenceSink {
    val items = mutableListOf<PendingOccurrenceStore.Occurrence>()

    override suspend fun add(occurrence: PendingOccurrenceStore.Occurrence) {
        items.removeAll { it.key == occurrence.key }
        items.add(occurrence)
    }

    override suspend fun list(): List<PendingOccurrenceStore.Occurrence> = items.toList()

    override suspend fun remove(key: String) {
        items.removeAll { it.key == key }
    }
}
