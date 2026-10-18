package com.afu.ledger.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afu.ledger.auto.AutoLedgerSettings
import com.afu.ledger.auto.AutoLedgerSettingsRepository
import com.afu.ledger.auto.RecognitionEntry
import com.afu.ledger.auto.RecognitionLog
import com.afu.ledger.data.prefs.DarkModeSetting
import com.afu.ledger.data.prefs.ThemePreferences
import com.afu.ledger.data.prefs.ThemeSettings
import com.afu.ledger.data.repository.AccountRepository
import com.afu.ledger.data.repository.CategoryRepository
import com.afu.ledger.domain.model.Account
import com.afu.ledger.domain.model.Category
import com.afu.ledger.domain.model.TransType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val themePreferences: ThemePreferences,
    private val autoLedgerSettings: AutoLedgerSettingsRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    private val recognitionLog: RecognitionLog,
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

    fun setThemeColor(colorId: String) {
        viewModelScope.launch { themePreferences.setThemeColor(colorId) }
    }

    fun setDarkMode(mode: DarkModeSetting) {
        viewModelScope.launch { themePreferences.setDarkMode(mode) }
    }

    fun setBackgroundUri(uri: String?) {
        viewModelScope.launch { themePreferences.setBackgroundUri(uri) }
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

    fun clearLog() = recognitionLog.clear()
}
