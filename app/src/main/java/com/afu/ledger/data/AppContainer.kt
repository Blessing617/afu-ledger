package com.afu.ledger.data

import android.content.Context
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
}
