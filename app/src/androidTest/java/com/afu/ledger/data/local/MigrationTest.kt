package com.afu.ledger.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 数据库迁移测试。
 *
 * 目的是拦住「升级后打不开 / 数据被清空」这类事故：用真实 SQLite 建旧版本库、
 * 写入数据、跑迁移、再校验数据还在。schema JSON 必须随仓库提交，否则本测试读不到。
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate1To2KeepsAccountAndTransaction() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO accounts (id, name, type, initialBalanceCents, colorArgb, iconKey, archived, sortOrder, createdAt)
                VALUES (1, '现金', 'CASH', 50000, 0, 'account_balance_wallet', 0, 0, 1000)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO categories (id, name, type, iconKey, colorArgb, sortOrder, preset)
                VALUES (10, '餐饮', 'EXPENSE', 'restaurant', 0, 0, 1)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO transactions (id, accountId, categoryId, type, amountCents, note, occurredAt, createdAt, updatedAt)
                VALUES (100, 1, 10, 'EXPENSE', 2550, '午饭', 2000, 2000, 2000)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true).use { db ->
            db.query("SELECT name, initialBalanceCents FROM accounts WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("现金", cursor.getString(0))
                assertEquals(50_000L, cursor.getLong(1))
            }
            db.query("SELECT amountCents, note FROM transactions WHERE id = 100").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2_550L, cursor.getLong(0))
                assertEquals("午饭", cursor.getString(1))
            }
            // v2 新增的表必须存在且为空
            db.query("SELECT COUNT(*) FROM schedule_rules").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            // v2 给交易加的两个字段要有默认值，旧数据不能变成 NULL
            db.query("SELECT sourceType, backfilled FROM transactions WHERE id = 100").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("MANUAL", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
            }
        }
    }

    @Test
    fun migrate2To3KeepsDataAndAddsBudgetsTable() {
        helper.createDatabase(TEST_DB_2_TO_3, 2).apply {
            execSQL(
                """
                INSERT INTO accounts (id, name, type, initialBalanceCents, colorArgb, iconKey, archived, sortOrder, createdAt)
                VALUES (1, '现金', 'CASH', 50000, 0, 'account_balance_wallet', 0, 0, 1000)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO categories (id, name, type, iconKey, colorArgb, sortOrder, preset)
                VALUES (10, '餐饮', 'EXPENSE', 'restaurant', 0, 0, 1)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO transactions (id, accountId, categoryId, type, amountCents, note, occurredAt, createdAt, updatedAt, sourceType, backfilled)
                VALUES (100, 1, 10, 'EXPENSE', 2550, '午饭', 2000, 2000, 2000, 'MANUAL', 0)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB_2_TO_3, 3, true).use { db ->
            db.query("SELECT COUNT(*) FROM budgets").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            db.query("SELECT amountCents FROM transactions WHERE id = 100").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2_550L, cursor.getLong(0))
            }
        }
    }

    private companion object {
        const val TEST_DB = "migration-test-v1-to-v2.db"
        const val TEST_DB_2_TO_3 = "migration-test-v2-to-v3.db"
    }
}
