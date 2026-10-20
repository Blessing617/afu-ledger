package com.afu.ledger.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.afu.ledger.data.local.dao.AccountDao
import com.afu.ledger.data.local.dao.CategoryDao
import com.afu.ledger.data.local.dao.ScheduleRuleDao
import com.afu.ledger.data.local.dao.TransactionDao
import com.afu.ledger.data.local.entity.AccountEntity
import com.afu.ledger.data.local.entity.CategoryEntity
import com.afu.ledger.data.local.entity.ScheduleRuleEntity
import com.afu.ledger.data.local.entity.TransactionEntity

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        ScheduleRuleEntity::class,
    ],
    version = 2,
    exportSchema = true,
    // v2：定时记账（新增 schedule_rules 表 + 交易来源字段 + 规则期次唯一索引）。
    // 用 Room 的自动迁移而不是手写 DDL：手写 CREATE TABLE 与实体定义稍有出入，
    // Room 打开数据库时的 schema 校验就会崩，普通用户遇到就是「升级后打不开」。
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
    ],
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao

    abstract fun categoryDao(): CategoryDao

    abstract fun transactionDao(): TransactionDao

    abstract fun scheduleRuleDao(): ScheduleRuleDao

    companion object {
        private const val DATABASE_NAME = "afu-ledger.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME,
                ).build().also { instance = it }
            }
    }
}
