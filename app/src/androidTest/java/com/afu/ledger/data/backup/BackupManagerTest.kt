package com.afu.ledger.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afu.ledger.data.local.AppDatabase
import com.afu.ledger.data.local.entity.AccountEntity
import com.afu.ledger.data.local.entity.CategoryEntity
import com.afu.ledger.data.local.entity.ScheduleRuleEntity
import com.afu.ledger.data.local.entity.TransactionEntity
import com.afu.ledger.data.repository.CategoryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/** 备份导出 / 导入 / 清空的端到端校验（真实 SQLite，内存库）。 */
@RunWith(AndroidJUnit4::class)
class BackupManagerTest {

    private lateinit var db: AppDatabase
    private lateinit var manager: BackupManager

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        manager = BackupManager(
            database = db,
            categoryRepository = CategoryRepository(db.categoryDao()),
            databaseFile = null,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun exportThenImportRestoresEverything() = runBlocking {
        seedSampleData(transactionCount = 3)

        val exported = ByteArrayOutputStream()
        val exportedCounts = manager.exportTo(exported, appVersionName = "1.0.0", appVersionCode = 8)
        assertEquals(1, exportedCounts.accounts)
        assertEquals(1, exportedCounts.categories)
        assertEquals(3, exportedCounts.transactions)
        assertEquals(1, exportedCounts.scheduleRules)
        assertEquals(1, exportedCounts.budgets)

        val json = exported.toString(Charsets.UTF_8.name())
        assertTrue(json.startsWith("{"))
        assertTrue(json.contains("\"format\":\"afu-ledger-backup\""))
        assertTrue(json.endsWith("]}"))

        // 清空（会重建预置分类），再导回
        manager.clearAll()
        assertEquals(0, db.transactionDao().count())
        assertEquals(0, db.accountDao().count())

        val importedCounts = manager.importFrom(json.byteInputStream(Charsets.UTF_8))
        assertEquals(3, importedCounts.transactions)
        assertEquals(1, importedCounts.accounts)
        assertEquals(1, importedCounts.categories)
        assertEquals(1, importedCounts.scheduleRules)
        assertEquals(1, importedCounts.budgets)

        // id 与引用关系必须原样恢复
        assertEquals(1, db.accountDao().count())
        val account = db.accountDao().findById(1)
        assertNotNull(account)
        assertEquals(50_000L, account!!.initialBalanceCents)
        val transaction = db.transactionDao().findById(100)
        assertNotNull(transaction)
        assertEquals(2_550L, transaction!!.amountCents)

        // 余额 SQL 仍然正确：50000 - 2550 - 2550 - 2550 = 42350
        val balances = db.accountDao().observeBalances().first()
        assertEquals(42_350L, balances.first().balanceCents)
    }

    @Test
    fun exportSpansMultiplePages() = runBlocking {
        seedSampleData(transactionCount = 1_201)

        val exported = ByteArrayOutputStream()
        manager.exportTo(exported, appVersionName = "1.0.0", appVersionCode = 8)
        manager.clearAll()

        val imported = manager.importFrom(exported.toByteArray().inputStream())
        assertEquals(1_201, imported.transactions)
        assertEquals(1_201, db.transactionDao().count())
    }

    @Test
    fun importRejectsCorruptFile() = runBlocking {
        seedSampleData(transactionCount = 1)
        val failure = runCatching {
            manager.importFrom("{ this is not json".byteInputStream(Charsets.UTF_8))
        }.exceptionOrNull()
        assertTrue(failure is BackupException)
        // 现有数据必须原封不动
        assertEquals(1, db.transactionDao().count())
    }

    @Test
    fun importRejectsNewerFormatVersion() = runBlocking {
        seedSampleData(transactionCount = 1)
        val newer = """
            {"format":"afu-ledger-backup","formatVersion":99,
             "exportedAt":"2026-01-01T00:00:00+08:00","appVersionName":"9.9.9",
             "appVersionCode":999,"schemaVersion":99,"counts":{},
             "accounts":[],"categories":[],"transactions":[],"scheduleRules":[]}
        """.trimIndent()

        val failure = runCatching {
            manager.importFrom(newer.byteInputStream(Charsets.UTF_8))
        }.exceptionOrNull()
        assertTrue(failure is BackupException)
        assertTrue(failure?.message.orEmpty().contains("更新版本"))
        assertEquals(1, db.transactionDao().count())
    }

    @Test
    fun importRejectsDanglingReference() = runBlocking {
        seedSampleData(transactionCount = 1)
        val dangling = """
            {"format":"afu-ledger-backup","formatVersion":1,
             "exportedAt":"2026-01-01T00:00:00+08:00","appVersionName":"1.0.0",
             "appVersionCode":8,"schemaVersion":2,"counts":{},
             "accounts":[],
             "categories":[{"id":10,"name":"餐饮","type":"EXPENSE","iconKey":"restaurant","colorArgb":0}],
             "transactions":[{"id":1,"accountId":999,"categoryId":10,"type":"EXPENSE",
                              "amountCents":100,"occurredAt":0,"createdAt":0,"updatedAt":0}],
             "scheduleRules":[]}
        """.trimIndent()

        val failure = runCatching {
            manager.importFrom(dangling.byteInputStream(Charsets.UTF_8))
        }.exceptionOrNull()
        assertTrue(failure is BackupException)
        assertEquals(1, db.transactionDao().count())
    }

    @Test
    fun transferSurvivesExportImport() = runBlocking {
        // 两个账户 + 一笔转移：导入后必须仍是一笔转移（不是被拆成两笔收支），两端余额一致
        db.accountDao().insert(
            AccountEntity(
                id = 1,
                name = "工资卡",
                type = "BANK",
                initialBalanceCents = 4_100_000,
                colorArgb = 0,
                iconKey = "credit_card",
                createdAt = 1_000,
            ),
        )
        db.accountDao().insert(
            AccountEntity(
                id = 2,
                name = "消费卡",
                type = "BANK",
                initialBalanceCents = 9_522,
                colorArgb = 0,
                iconKey = "credit_card",
                sortOrder = 1,
                createdAt = 1_000,
            ),
        )
        db.categoryDao().insert(
            CategoryEntity(
                id = 9,
                name = "转账",
                type = "TRANSFER",
                iconKey = "swap_horiz",
                colorArgb = 0,
                sortOrder = 999,
                preset = true,
            ),
        )
        db.transactionDao().insert(
            TransactionEntity(
                id = 100,
                accountId = 1,
                categoryId = 9,
                type = "TRANSFER",
                amountCents = 556_521,
                occurredAt = 2_000,
                createdAt = 2_000,
                updatedAt = 2_000,
                transferAccountId = 2,
            ),
        )

        val exported = ByteArrayOutputStream()
        manager.exportTo(exported, appVersionName = "1.4.0", appVersionCode = 20)

        val imported = manager.importFrom(
            exported.toString(Charsets.UTF_8.name()).byteInputStream(Charsets.UTF_8),
        )
        assertEquals(1, imported.transactions)

        val restored = db.transactionDao().findById(100)
        assertNotNull(restored)
        assertEquals("TRANSFER", restored!!.type)
        assertEquals(2L, restored.transferAccountId)

        val balances = db.accountDao().observeBalances().first().associate { it.id to it.balanceCents }
        assertEquals(3_543_479L, balances[1])
        assertEquals(566_043L, balances[2])
    }

    @Test
    fun importAcceptsV1BackupWithoutBudgets() = runBlocking {
        seedSampleData(transactionCount = 1)
        val v1 = """
            {"format":"afu-ledger-backup","formatVersion":1,
             "exportedAt":"2026-01-01T00:00:00+08:00","appVersionName":"0.9.0",
             "appVersionCode":7,"schemaVersion":2,"counts":{},
             "accounts":[{"id":1,"name":"现金","type":"CASH","initialBalanceCents":50000,
                          "colorArgb":0,"iconKey":"account_balance_wallet","createdAt":0}],
             "categories":[{"id":10,"name":"餐饮","type":"EXPENSE","iconKey":"restaurant","colorArgb":0}],
             "transactions":[{"id":100,"accountId":1,"categoryId":10,"type":"EXPENSE",
                              "amountCents":2550,"occurredAt":1000,"createdAt":1000,"updatedAt":1000}],
             "scheduleRules":[]}
        """.trimIndent()

        val counts = manager.importFrom(v1.byteInputStream(Charsets.UTF_8))
        assertEquals(1, counts.transactions)
        assertEquals(0, counts.budgets)
        assertEquals(0, db.budgetDao().count())
    }

    @Test
    fun clearAllReseedsPresetCategories() = runBlocking {
        seedSampleData(transactionCount = 2)
        manager.clearAll()

        assertEquals(0, db.transactionDao().count())
        assertEquals(0, db.accountDao().count())
        assertEquals(0, db.scheduleRuleDao().count())
        // 预置分类要回来，否则记账面板没有可选项
        assertTrue(db.categoryDao().count() > 0)
    }

    private suspend fun seedSampleData(transactionCount: Int) {
        db.accountDao().insert(
            AccountEntity(
                id = 1,
                name = "现金",
                type = "CASH",
                initialBalanceCents = 50_000,
                colorArgb = 0,
                iconKey = "account_balance_wallet",
                createdAt = 1_000,
            ),
        )
        db.categoryDao().insert(
            CategoryEntity(
                id = 10,
                name = "餐饮",
                type = "EXPENSE",
                iconKey = "restaurant",
                colorArgb = 0,
                preset = true,
            ),
        )
        repeat(transactionCount) { index ->
            db.transactionDao().insert(
                TransactionEntity(
                    id = (100 + index).toLong(),
                    accountId = 1,
                    categoryId = 10,
                    type = "EXPENSE",
                    amountCents = 2_550,
                    note = "午饭",
                    occurredAt = 2_000L + index,
                    createdAt = 2_000L + index,
                    updatedAt = 2_000L + index,
                ),
            )
        }
        db.budgetDao().insert(
            com.afu.ledger.data.local.entity.BudgetEntity(
                id = 1,
                name = "本月",
                amountCents = 300_000,
                periodType = "MONTHLY",
                categoryIds = "10",
                alertThresholdPercent = 80,
                createdAt = 1_000,
                updatedAt = 1_000,
            ),
        )
        db.scheduleRuleDao().insert(
            ScheduleRuleEntity(
                id = 1,
                accountId = 1,
                categoryId = 10,
                type = "EXPENSE",
                amountCents = 1_000,
                note = "房租",
                frequency = "MONTHLY",
                dayOfMonth = 1,
                startEpochDay = 20_000,
                createdAt = 1_000,
                updatedAt = 1_000,
            ),
        )
    }
}
