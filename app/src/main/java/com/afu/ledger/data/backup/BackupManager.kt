package com.afu.ledger.data.backup

import androidx.room.withTransaction
import com.afu.ledger.data.local.AFU_DB_SCHEMA_VERSION
import com.afu.ledger.data.local.AppDatabase
import com.afu.ledger.data.repository.CategoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedWriter
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.time.OffsetDateTime

/** 备份相关的用户可见错误。message 直接展示给用户，不要塞堆栈。 */
class BackupException(message: String) : Exception(message)

/** 数据管理区块展示的统计。 */
data class DataStats(
    val transactions: Int,
    val accounts: Int,
    val categories: Int,
    val scheduleRules: Int,
    val databaseBytes: Long,
)

/**
 * 账本数据的导出 / 导入 / 清空。
 *
 * 为什么要有它：数据库放在应用私有目录，「卸载即丢」；自动云备份又依赖 ROM 与账号。
 * 给用户一个能自己拿在手里的文件，才是真正意义上的「记住」。
 */
class BackupManager(
    private val database: AppDatabase,
    private val categoryRepository: CategoryRepository,
    /** 用来展示「数据库大小」，可为 null（取不到就不显示体积）。 */
    private val databaseFile: File?,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun stats(): DataStats = withContext(Dispatchers.IO) {
        DataStats(
            transactions = database.transactionDao().count(),
            accounts = database.accountDao().count(),
            categories = database.categoryDao().count(),
            scheduleRules = database.scheduleRuleDao().count(),
            databaseBytes = databaseFile?.takeIf { it.exists() }?.length() ?: 0L,
        )
    }

    /**
     * 导出到 [out]。
     *
     * 交易按 500 行分页读出、逐条序列化写入，写完立即丢弃这批 ——
     * 堆内存不随账单数量增长，5000 笔和 5 笔占用一样。
     */
    suspend fun exportTo(
        out: OutputStream,
        appVersionName: String,
        appVersionCode: Int,
    ): BackupCounts = withContext(Dispatchers.IO) {
        val counts = readCounts()
        val meta = BackupMeta(
            format = BackupFormat.FORMAT,
            formatVersion = BackupFormat.VERSION,
            exportedAt = OffsetDateTime.now().toString(),
            appVersionName = appVersionName,
            appVersionCode = appVersionCode,
            schemaVersion = AFU_DB_SCHEMA_VERSION,
            counts = counts,
        )

        val writer = BufferedWriter(OutputStreamWriter(out, Charsets.UTF_8), WRITE_BUFFER)
        writer.use { w ->
            // 头信息整体序列化后去掉收尾的 "}"，方便直接续写实体数组
            w.write(json.encodeToString(BackupMeta.serializer(), meta).removeSuffix("}"))

            w.write(",\"accounts\":[")
            writeAccounts(w)
            w.write("],\"categories\":[")
            writeCategories(w)
            w.write("],\"transactions\":[")
            writeTransactions(w)
            w.write("],\"scheduleRules\":[")
            writeScheduleRules(w)
            w.write("],\"budgets\":[")
            writeBudgets(w)
            w.write("]}")
        }
        counts
    }

    private suspend fun writeAccounts(writer: Writer) {
        var first = true
        for (row in database.accountDao().findAll()) {
            if (!first) writer.write(",")
            writer.write(json.encodeToString(BackupAccount.serializer(), BackupAccount.from(row)))
            first = false
        }
    }

    private suspend fun writeCategories(writer: Writer) {
        var first = true
        for (row in database.categoryDao().findAll()) {
            if (!first) writer.write(",")
            writer.write(json.encodeToString(BackupCategory.serializer(), BackupCategory.from(row)))
            first = false
        }
    }

    private suspend fun writeScheduleRules(writer: Writer) {
        var first = true
        for (row in database.scheduleRuleDao().findAll()) {
            if (!first) writer.write(",")
            writer.write(
                json.encodeToString(BackupScheduleRule.serializer(), BackupScheduleRule.from(row))
            )
            first = false
        }
    }

    private suspend fun writeBudgets(writer: Writer) {
        var first = true
        for (row in database.budgetDao().findAll()) {
            if (!first) writer.write(",")
            writer.write(json.encodeToString(BackupBudget.serializer(), BackupBudget.from(row)))
            first = false
        }
    }

    private suspend fun writeTransactions(writer: Writer) {
        val dao = database.transactionDao()
        var offset = 0
        var first = true
        while (true) {
            val rows = dao.page(limit = PAGE_SIZE, offset = offset)
            if (rows.isEmpty()) break
            for (row in rows) {
                if (!first) writer.write(",")
                writer.write(json.encodeToString(BackupTransaction.serializer(), BackupTransaction.from(row)))
                first = false
            }
            offset += rows.size
        }
    }

    /**
     * 从 [input] 还原。
     *
     * 校验全部通过后才动数据库；「清空 + 写入」在同一个事务里，失败整体回滚，
     * 不会出现「清了一半、导了一半」的中间态。
     */
    suspend fun importFrom(input: InputStream): BackupCounts = withContext(Dispatchers.IO) {
        val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val document = try {
            json.decodeFromString(BackupDocument.serializer(), text)
        } catch (e: Exception) {
            throw BackupException("备份文件损坏，或不是阿福记账的备份文件")
        }
        validate(document)

        val counts = BackupCounts(
            accounts = document.accounts.size,
            categories = document.categories.size,
            transactions = document.transactions.size,
            scheduleRules = document.scheduleRules.size,
            budgets = document.budgets.size,
        )

        database.withTransaction {
            deleteEverything()
            insertEverything(document)
        }
        counts
    }

    /** 清空全部账本数据，并写回预置分类（否则记账面板没有可选项）。 */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        database.withTransaction { deleteEverything() }
        categoryRepository.ensurePresetCategories()
        Unit
    }

    private fun validate(document: BackupDocument) {
        if (document.format != BackupFormat.FORMAT) {
            throw BackupException("这不是阿福记账的备份文件")
        }
        if (document.formatVersion <= 0) {
            throw BackupException("备份文件缺少格式版本，无法识别")
        }
        if (document.formatVersion > BackupFormat.VERSION) {
            throw BackupException(
                "备份文件来自更新版本的应用（格式 v${document.formatVersion}），请先升级阿福记账再导入"
            )
        }

        val accountIds = document.accounts.mapTo(HashSet()) { it.id }
        val categoryIds = document.categories.mapTo(HashSet()) { it.id }

        for (transaction in document.transactions) {
            if (transaction.accountId !in accountIds) {
                throw BackupException("备份内容不一致：有交易引用了不存在的账户")
            }
            if (transaction.categoryId !in categoryIds) {
                throw BackupException("备份内容不一致：有交易引用了不存在的分类")
            }
            if (transaction.amountCents < 0) {
                throw BackupException("备份内容不一致：存在负数金额的交易")
            }
            if (transaction.type != "EXPENSE" && transaction.type != "INCOME") {
                throw BackupException("备份内容不一致：交易收支类型异常（${transaction.type}）")
            }
        }
        for (rule in document.scheduleRules) {
            if (rule.accountId !in accountIds || rule.categoryId !in categoryIds) {
                throw BackupException("备份内容不一致：有定时规则引用了不存在的账户或分类")
            }
        }
        for (budget in document.budgets) {
            val bound = budget.categoryIds.split(',').mapNotNull { it.trim().toLongOrNull() }
            if (bound.any { it !in categoryIds }) {
                throw BackupException("备份内容不一致：有预算绑定了不存在的分类")
            }
            if (budget.amountCents <= 0) {
                throw BackupException("备份内容不一致：存在额度不大于 0 的预算")
            }
        }
    }

    private suspend fun deleteEverything() {
        // 顺序受外键约束：先删引用方，再删被引用方
        database.transactionDao().deleteAll()
        database.scheduleRuleDao().deleteAll()
        database.budgetDao().deleteAll()
        database.accountDao().deleteAll()
        database.categoryDao().deleteAll()
    }

    private suspend fun insertEverything(document: BackupDocument) {
        val accountDao = database.accountDao()
        val categoryDao = database.categoryDao()
        val transactionDao = database.transactionDao()
        val ruleDao = database.scheduleRuleDao()

        // 保留原始 id：交易、规则的引用关系靠它维持
        document.accounts.forEach { accountDao.insert(it.toEntity()) }
        document.categories.forEach { categoryDao.insert(it.toEntity()) }
        document.transactions.forEach { transactionDao.insert(it.toEntity()) }
        document.scheduleRules.forEach { ruleDao.insert(it.toEntity()) }
        document.budgets.forEach { database.budgetDao().insert(it.toEntity()) }
    }

    private suspend fun readCounts() = BackupCounts(
        accounts = database.accountDao().count(),
        categories = database.categoryDao().count(),
        transactions = database.transactionDao().count(),
        scheduleRules = database.scheduleRuleDao().count(),
        budgets = database.budgetDao().count(),
    )

    private companion object {
        const val PAGE_SIZE = 500
        const val WRITE_BUFFER = 64 * 1024
    }
}
