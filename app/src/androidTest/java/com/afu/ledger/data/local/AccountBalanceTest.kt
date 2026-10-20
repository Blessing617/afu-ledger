package com.afu.ledger.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afu.ledger.data.local.entity.AccountEntity
import com.afu.ledger.data.local.entity.CategoryEntity
import com.afu.ledger.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 验证账户余额聚合 SQL：余额 = 初始余额 + Σ收入 − Σ支出。 */
@RunWith(AndroidJUnit4::class)
class AccountBalanceTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun balanceIncludesInitialAndTransactions() = runBlocking {
        val accountDao = db.accountDao()
        val categoryDao = db.categoryDao()
        val transactionDao = db.transactionDao()

        val accountId = accountDao.insert(
            AccountEntity(
                name = "现金",
                type = "CASH",
                initialBalanceCents = 50_000, // 500.00
                colorArgb = 0,
                iconKey = "account_balance_wallet",
                createdAt = 0,
            ),
        )
        val categoryId = categoryDao.insert(
            CategoryEntity(name = "测试", type = "EXPENSE", iconKey = "more_horiz", colorArgb = 0),
        )
        val incomeCategoryId = categoryDao.insert(
            CategoryEntity(name = "工资", type = "INCOME", iconKey = "payments", colorArgb = 0),
        )

        val now = System.currentTimeMillis()
        transactionDao.insert(
            TransactionEntity(
                accountId = accountId,
                categoryId = categoryId,
                type = "EXPENSE",
                amountCents = 2_550, // -25.50
                occurredAt = now,
                createdAt = now,
                updatedAt = now,
            ),
        )
        transactionDao.insert(
            TransactionEntity(
                accountId = accountId,
                categoryId = incomeCategoryId,
                type = "INCOME",
                amountCents = 1_000, // +10.00
                occurredAt = now,
                createdAt = now,
                updatedAt = now,
            ),
        )

        val balances = accountDao.observeBalances().first()
        assertEquals(1, balances.size)
        // 50000 - 2550 + 1000 = 48450
        assertEquals(48_450L, balances.first().balanceCents)
    }

    @Test
    fun balanceWithoutTransactionsEqualsInitial() = runBlocking {
        val accountDao = db.accountDao()
        accountDao.insert(
            AccountEntity(
                name = "空账户",
                type = "BANK",
                initialBalanceCents = 123_456,
                colorArgb = 0,
                iconKey = "credit_card",
                createdAt = 0,
            ),
        )

        val balances = accountDao.observeBalances().first()
        assertEquals(123_456L, balances.first().balanceCents)
    }

    @Test
    fun categoryUsageCountedForDeletionGuard() = runBlocking {
        val accountDao = db.accountDao()
        val categoryDao = db.categoryDao()
        val transactionDao = db.transactionDao()

        val accountId = accountDao.insert(
            AccountEntity(
                name = "现金",
                type = "CASH",
                initialBalanceCents = 0,
                colorArgb = 0,
                iconKey = "account_balance_wallet",
                createdAt = 0,
            ),
        )
        val categoryId = categoryDao.insert(
            CategoryEntity(name = "餐饮", type = "EXPENSE", iconKey = "restaurant", colorArgb = 0),
        )
        val now = System.currentTimeMillis()
        transactionDao.insert(
            TransactionEntity(
                accountId = accountId,
                categoryId = categoryId,
                type = "EXPENSE",
                amountCents = 100,
                occurredAt = now,
                createdAt = now,
                updatedAt = now,
            ),
        )

        assertEquals(1, categoryDao.countUsage(categoryId))
    }
}
