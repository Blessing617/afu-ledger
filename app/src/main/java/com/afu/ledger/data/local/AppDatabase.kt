package com.afu.ledger.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.afu.ledger.data.local.dao.AccountDao
import com.afu.ledger.data.local.dao.BudgetDao
import com.afu.ledger.data.local.dao.CategoryDao
import com.afu.ledger.data.local.dao.ScheduleRuleDao
import com.afu.ledger.data.local.dao.TransactionDao
import com.afu.ledger.data.local.entity.AccountEntity
import com.afu.ledger.data.local.entity.BudgetEntity
import com.afu.ledger.data.local.entity.CategoryEntity
import com.afu.ledger.data.local.entity.ScheduleRuleEntity
import com.afu.ledger.data.local.entity.TransactionEntity

/**
 * 当前数据库 schema 版本。
 *
 * 必须与 [AppDatabase] 的 `version` 一致；备份文件写入它，导入时据此判断兼容性。
 * 每次加版本都要：改这里 → 加 AutoMigration/Migration → 提交 `app/schemas/` 下新导出的 JSON
 * → 在 MigrationTest 里补一条「上一版本 → 新版本」的用例。
 */
const val AFU_DB_SCHEMA_VERSION = 4

/**
 * v3 → v4：归档概念从界面移除。
 *
 * 只做数据修正：历史上被归档的账户统一恢复成普通账户（`archived = 0`）。
 * **保留 `archived` 列**，不删列 —— 删列要重建表，风险和收益不成比例；
 * 界面与仓库层不再读写它，保留的列只是让旧库与新库的 schema 差得最少。
 *
 * 不用 AutoMigration：它只能处理结构差异，做不了这种值修正。
 */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE accounts SET archived = 0 WHERE archived = 1")
    }
}

/**
 * 应用数据库。
 *
 * **绝对不要加 `fallbackToDestructiveMigration`。** 那会让「升级后打不开数据库」的
 * 唯一后果变成「用户账本被静默清空」——记账 App 最不能接受的失败方式。
 * 迁移写错就让它崩，崩了还能修；数据被删了就真没了。
 */
@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        ScheduleRuleEntity::class,
        BudgetEntity::class,
    ],
    version = AFU_DB_SCHEMA_VERSION,
    exportSchema = true,
    // v2：定时记账（新增 schedule_rules 表 + 交易来源字段 + 规则期次唯一索引）。
    // v3：预算管理（新增 budgets 表）。
    // v4：归档概念移除（值修正，见 [MIGRATION_3_4]）。
    // 用 Room 的自动迁移而不是手写 DDL：手写 CREATE TABLE 与实体定义稍有出入，
    // Room 打开数据库时的 schema 校验就会崩，普通用户遇到就是「升级后打不开」。
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
    ],
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao

    abstract fun categoryDao(): CategoryDao

    abstract fun transactionDao(): TransactionDao

    abstract fun scheduleRuleDao(): ScheduleRuleDao

    abstract fun budgetDao(): BudgetDao

    companion object {
        const val DATABASE_NAME = "afu-ledger.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME,
                )
                    .addMigrations(MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
    }
}
