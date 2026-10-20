package com.afu.ledger.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afu.ledger.BuildConfig
import com.afu.ledger.auto.AutoLedgerSettings
import com.afu.ledger.auto.AutoLedgerSettingsRepository
import com.afu.ledger.auto.OcrStatus
import com.afu.ledger.auto.OcrStatusHolder
import com.afu.ledger.auto.RecognitionEntry
import com.afu.ledger.auto.RecognitionLog
import com.afu.ledger.data.backup.BackupException
import com.afu.ledger.data.backup.BackupManager
import com.afu.ledger.data.backup.DataStats
import com.afu.ledger.data.prefs.DarkModeSetting
import com.afu.ledger.data.prefs.ThemePreferences
import com.afu.ledger.data.prefs.ThemeSettings
import com.afu.ledger.data.repository.AccountRepository
import com.afu.ledger.data.repository.CategoryRepository
import com.afu.ledger.domain.model.Account
import com.afu.ledger.domain.model.Category
import com.afu.ledger.domain.model.TransType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 备份操作的结果提示，展示成 Toast 后调用 [SettingsViewModel.consumeDataMessage] 清掉。 */
data class DataActionMessage(val text: String, val isError: Boolean = false)

class SettingsViewModel(
    private val themePreferences: ThemePreferences,
    private val autoLedgerSettings: AutoLedgerSettingsRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    private val recognitionLog: RecognitionLog,
    ocrStatusHolder: OcrStatusHolder,
    private val backupManager: BackupManager,
    private val contentResolver: ContentResolver,
    /** 导入或清空后要让定时规则重新排闹钟、补算期次。 */
    private val afterLedgerRestored: () -> Unit = {},
) : ViewModel() {

    val settings: StateFlow<ThemeSettings> = themePreferences.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ThemeSettings(),
    )

    val autoSettings: StateFlow<AutoLedgerSettings> = autoLedgerSettings.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AutoLedgerSettings(),
    )

    val accounts: StateFlow<List<Account>> = accountRepository.observeActive().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val expenseCategories: StateFlow<List<Category>> =
        categoryRepository.observeByType(TransType.EXPENSE).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val logEntries: StateFlow<List<RecognitionEntry>> = recognitionLog.entries

    val ocrStatus: StateFlow<OcrStatus> = ocrStatusHolder.state

    private val _dataStats = MutableStateFlow(DataStats(0, 0, 0, 0, 0))
    val dataStats: StateFlow<DataStats> = _dataStats.asStateFlow()

    private val _dataBusy = MutableStateFlow(false)
    val dataBusy: StateFlow<Boolean> = _dataBusy.asStateFlow()

    private val _dataMessage = MutableStateFlow<DataActionMessage?>(null)
    val dataMessage: StateFlow<DataActionMessage?> = _dataMessage.asStateFlow()

    init {
        refreshDataStats()
    }

    fun refreshDataStats() {
        viewModelScope.launch {
            runCatching { _dataStats.value = backupManager.stats() }
        }
    }

    /** 导出到用户在系统文件选择器里指定的位置。 */
    fun exportBackup(uri: Uri) {
        if (_dataBusy.value) return
        _dataBusy.value = true
        viewModelScope.launch {
            try {
                val stream = contentResolver.openOutputStream(uri)
                    ?: throw BackupException("无法写入所选位置")
                val counts = stream.use { out ->
                    backupManager.exportTo(
                        out = out,
                        appVersionName = BuildConfig.VERSION_NAME,
                        appVersionCode = BuildConfig.VERSION_CODE,
                    )
                }
                _dataMessage.value = DataActionMessage(
                    "已导出 ${counts.transactions} 笔交易、${counts.accounts} 个账户"
                )
            } catch (e: Exception) {
                _dataMessage.value = DataActionMessage(
                    text = e.message ?: "导出失败",
                    isError = true,
                )
            } finally {
                _dataBusy.value = false
                refreshDataStats()
            }
        }
    }

    /** 从备份文件还原：先校验，再在一个事务里替换。 */
    fun importBackup(uri: Uri) {
        if (_dataBusy.value) return
        _dataBusy.value = true
        viewModelScope.launch {
            try {
                val stream = contentResolver.openInputStream(uri)
                    ?: throw BackupException("无法读取所选文件")
                val counts = stream.use { backupManager.importFrom(it) }
                _dataMessage.value = DataActionMessage(
                    "已还原 ${counts.transactions} 笔交易、${counts.accounts} 个账户"
                )
                afterLedgerRestored()
            } catch (e: Exception) {
                _dataMessage.value = DataActionMessage(
                    text = e.message ?: "导入失败",
                    isError = true,
                )
            } finally {
                _dataBusy.value = false
                refreshDataStats()
            }
        }
    }

    fun clearAllData() {
        if (_dataBusy.value) return
        _dataBusy.value = true
        viewModelScope.launch {
            try {
                backupManager.clearAll()
                _dataMessage.value = DataActionMessage("已清空全部账本数据")
                afterLedgerRestored()
            } catch (e: Exception) {
                _dataMessage.value = DataActionMessage(
                    text = e.message ?: "清空失败",
                    isError = true,
                )
            } finally {
                _dataBusy.value = false
                refreshDataStats()
            }
        }
    }

    fun consumeDataMessage() {
        _dataMessage.value = null
    }

    fun setThemeStyle(styleId: String) {
        viewModelScope.launch { themePreferences.setThemeStyle(styleId) }
    }

    fun setThemeColor(colorId: String) {
        viewModelScope.launch { themePreferences.setThemeColor(colorId) }
    }

    fun setDarkMode(mode: DarkModeSetting) {
        viewModelScope.launch { themePreferences.setDarkMode(mode) }
    }

    fun setBackgroundUri(uri: String?) {
        viewModelScope.launch { themePreferences.setBackgroundUri(uri) }
    }

    fun setBackgroundVisibility(value: Float) {
        viewModelScope.launch { themePreferences.setBackgroundVisibility(value) }
    }

    fun setBackgroundBlur(enabled: Boolean) {
        viewModelScope.launch { themePreferences.setBackgroundBlur(enabled) }
    }

    fun setBackgroundScrim(enabled: Boolean) {
        viewModelScope.launch { themePreferences.setBackgroundScrim(enabled) }
    }

    fun setCardOpacity(value: Float) {
        viewModelScope.launch { themePreferences.setCardOpacity(value) }
    }

    /** 恢复默认背景：连显示参数一起重置。 */
    fun resetBackground() {
        viewModelScope.launch { themePreferences.resetBackground() }
    }

    fun setAutoEnabled(enabled: Boolean) {
        viewModelScope.launch { autoLedgerSettings.setEnabled(enabled) }
    }

    fun setDefaultAccount(accountId: Long?) {
        viewModelScope.launch { autoLedgerSettings.setDefaultAccount(accountId) }
    }

    fun setDefaultCategory(categoryId: Long?) {
        viewModelScope.launch { autoLedgerSettings.setDefaultCategory(categoryId) }
    }

    fun setRecognitionLogEnabled(enabled: Boolean) {
        viewModelScope.launch {
            autoLedgerSettings.setRecognitionLogEnabled(enabled)
            if (!enabled) recognitionLog.clear()
        }
    }

    fun setOcrEnabled(enabled: Boolean) {
        viewModelScope.launch { autoLedgerSettings.setOcrEnabled(enabled) }
    }

    fun setHideKeepAliveNotification(hide: Boolean) {
        viewModelScope.launch { autoLedgerSettings.setHideKeepAliveNotification(hide) }
    }

    fun clearLog() = recognitionLog.clear()
}
