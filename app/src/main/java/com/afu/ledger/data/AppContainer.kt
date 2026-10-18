package com.afu.ledger.data

import android.content.Context
import com.afu.ledger.auto.AutoLedgerSettingsRepository
import com.afu.ledger.auto.OcrStatusHolder
import com.afu.ledger.auto.RecognitionLog
import com.afu.ledger.auto.RecordDetectedPaymentUseCase
import com.afu.ledger.data.local.AppDatabase
import com.afu.ledger.data.prefs.ThemePreferences
import com.afu.ledger.data.repository.AccountRepository
import com.afu.ledger.data.repository.CategoryRepository
import com.afu.ledger.data.repository.TransactionRepository

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

    val themePreferences: ThemePreferences = ThemePreferences(context.applicationContext)

    val autoLedgerSettings: AutoLedgerSettingsRepository =
        AutoLedgerSettingsRepository(context.applicationContext)

    /** 识别日志只存在内存里，进程结束即清空。 */
    val recognitionLog: RecognitionLog = RecognitionLog()

    /** 截图 OCR 的运行状态，同样只存在内存里。 */
    val ocrStatus: OcrStatusHolder = OcrStatusHolder()

    val recordDetectedPayment: RecordDetectedPaymentUseCase = RecordDetectedPaymentUseCase(
        transactionRepository = transactionRepository,
        accountRepository = accountRepository,
        categoryRepository = categoryRepository,
    )
}
