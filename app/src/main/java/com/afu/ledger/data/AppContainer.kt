package com.afu.ledger.data

import android.content.Context
import com.afu.ledger.auto.AutoLedgerSettingsRepository
import com.afu.ledger.auto.OcrStatusHolder
import com.afu.ledger.auto.PendingBillStore
import com.afu.ledger.auto.ProcessedBillStore
import com.afu.ledger.auto.RecognitionLog
import com.afu.ledger.auto.RecordDetectedPaymentUseCase
import com.afu.ledger.auto.keepalive.KeepAliveStateStore
import com.afu.ledger.data.backup.BackupManager
import com.afu.ledger.data.local.AppDatabase
import com.afu.ledger.data.prefs.ThemePreferences
import com.afu.ledger.data.repository.AccountRepository
import com.afu.ledger.data.repository.BudgetRepository
import com.afu.ledger.data.repository.CategoryRepository
import com.afu.ledger.data.repository.ScheduleRuleRepository
import com.afu.ledger.data.repository.TransactionRepository
import com.afu.ledger.schedule.PendingOccurrenceStore
import com.afu.ledger.schedule.RunDueSchedulesUseCase

/**
 * 手写依赖容器。
 *
 * 第一版类数量少，用显式构造代替 Hilt，依赖关系一眼可见；模块变多后可平滑替换。
 */
class AppContainer(context: Context) {

    private val database: AppDatabase = AppDatabase.get(context.applicationContext)

    val accountRepository: AccountRepository = AccountRepository(database.accountDao())

    val categoryRepository: CategoryRepository = CategoryRepository(database.categoryDao())

    val transactionRepository: TransactionRepository = TransactionRepository(database.transactionDao())

    /** 定时记账规则。 */
    val scheduleRuleRepository: ScheduleRuleRepository = ScheduleRuleRepository(database.scheduleRuleDao())

    /** 预算：额度、周期、分类范围与本期已用。 */
    val budgetRepository: BudgetRepository = BudgetRepository(database.budgetDao())

    /**
     * 账本导出 / 导入 / 清空。
     *
     * 数据库文件路径用来展示「当前占用多少体积」；拿不到就不显示。
     */
    val backupManager: BackupManager = BackupManager(
        database = database,
        categoryRepository = categoryRepository,
        databaseFile = context.applicationContext.getDatabasePath(AppDatabase.DATABASE_NAME),
    )

    /** 「提醒确认」型定时规则的待确认期次。 */
    val pendingOccurrences: PendingOccurrenceStore = PendingOccurrenceStore(context.applicationContext)

    /** 到期检查 + 自动入账 / 落待确认 + 补记。闹钟、兜底任务、打开应用三条路径共用。 */
    val runDueSchedules: RunDueSchedulesUseCase = RunDueSchedulesUseCase(
        rules = scheduleRuleRepository,
        transactions = transactionRepository,
        accounts = accountRepository,
        categories = categoryRepository,
        pending = pendingOccurrences,
    )

    val themePreferences: ThemePreferences = ThemePreferences(context.applicationContext)

    val autoLedgerSettings: AutoLedgerSettingsRepository =
        AutoLedgerSettingsRepository(context.applicationContext)

    /** 识别日志只存在内存里，进程结束即清空。 */
    val recognitionLog: RecognitionLog = RecognitionLog()

    /** 已处理过的账单指纹（忽略 / 已入账 / 刚弹过），持久化以免重启后重复打扰。 */
    val processedBills: ProcessedBillStore = ProcessedBillStore(context.applicationContext)

    /**
     * 待确认账单。
     *
     * 系统对支付类应用可能禁止悬浮窗，识别成功不等于用户看到卡片；
     * 先落这里再弹窗，弹不出来也能从首页入口补确认。
     */
    val pendingBills: PendingBillStore = PendingBillStore(context.applicationContext)

    /** 截图 OCR 的运行状态，同样只存在内存里。 */
    val ocrStatus: OcrStatusHolder = OcrStatusHolder()

    /** 「无障碍曾经开过」的持久化标记，用来区分「从没设置」与「被撤销」。 */
    val keepAliveState: KeepAliveStateStore = KeepAliveStateStore(context.applicationContext)

    val recordDetectedPayment: RecordDetectedPaymentUseCase = RecordDetectedPaymentUseCase(
        transactionRepository = transactionRepository,
        accountRepository = accountRepository,
        categoryRepository = categoryRepository,
    )
}
