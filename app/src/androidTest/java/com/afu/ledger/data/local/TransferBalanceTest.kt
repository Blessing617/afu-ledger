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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 资产转移的余额口径。
 *
 * 一笔转移对两个账户各生效一次：转出方减、转入方加，净资产不变。
 * 这里守着的是整个功能的核心不变量 —— 一旦算错，用户的「总资产」会凭空涨落。
 */
@RunWith(AndroidJUnit4::class)
class TransferBalanceTest {

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

    private suspend fun addAccount(name: String, initialCents: Long): Long =
        db.accountDao().insert(
            AccountEntity(
                name = name,
                type = "BANK",
                initialBalanceCents = initialCents,
                colorArgb = 0,
                iconKey = "credit_card",
                createdAt = 0,
            ),
        )

    private suspend fun transferCategoryId(): Long =
        db.categoryDao().insert(
            CategoryEntity(
                name = "转账",
                type = "TRANSFER",
                iconKey = "swap_horiz",
                colorArgb = 0,
                sortOrder = 999,
                preset = true,
            ),
        )

    @Test
    fun transferMovesMoneyBetweenAccounts() = runBlocking {
        val salary = addAccount("工资卡", 4_100_000)
        val spending = addAccount("消费卡", 9_522)
        val categoryId = transferCategoryId()
        val now = System.currentTimeMillis()

        db.transactionDao().insert(
            TransactionEntity(
                accountId = salary,
                categoryId = categoryId,
                type = "TRANSFER",
                amountCents = 556_521,
                occurredAt = now,
                createdAt = now,
                updatedAt = now,
                transferAccountId = spending,
            ),
        )

        val balances = db.accountDao().observeBalances().first().associate { it.id to it.balanceCents }

        // 工资卡 41,000.00 − 5,565.21 = 35,434.79
        assertEquals(3_543_479L, balances[salary])
        // 消费卡 95.22 + 5,565.21 = 5,660.43
        assertEquals(566_043L, balances[spending])
        // 钱只是换了容器：总资产不变
        assertEquals(4_109_522L, balances.values.sum())
    }

    @Test
    fun deletingAccountRemovesIncomingTransfer() = runBlocking {
        val salary = addAccount("工资卡", 100_000)
        val spending = addAccount("消费卡", 0)
        val categoryId = transferCategoryId()
        val now = System.currentTimeMillis()

        db.transactionDao().insert(
            TransactionEntity(
                accountId = salary,
                categoryId = categoryId,
                type = "TRANSFER",
                amountCents = 20_000,
                occurredAt = now,
                createdAt = now,
                updatedAt = now,
                transferAccountId = spending,
            ),
        )

        // 删掉转入方：那笔转移必须一起消失，不能留下「转入账户已不存在」的半截记录
        db.accountDao().deleteWithTransfers(spending)

        assertEquals(1, db.accountDao().count())
        assertEquals(0, db.transactionDao().count())
        // 工资卡回到转出之前的余额
        val remaining = db.accountDao().observeBalances().first().single()
        assertEquals(100_000L, remaining.balanceCents)
        assertNull(db.accountDao().findById(spending))
    }
}
