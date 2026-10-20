package com.afu.ledger.auto

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.autoLedgerDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "auto_ledger_settings")

/** 自动记账配置的读写，与主题设置分开存，互不影响。 */
class AutoLedgerSettingsRepository(
    private val context: Context,
) {

    val settings: Flow<AutoLedgerSettings> = context.autoLedgerDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            AutoLedgerSettings(
                enabled = prefs[KEY_ENABLED] ?: false,
                defaultAccountId = prefs[KEY_DEFAULT_ACCOUNT],
                defaultCategoryId = prefs[KEY_DEFAULT_CATEGORY],
                recognitionLogEnabled = prefs[KEY_RECOGNITION_LOG] ?: false,
                ocrEnabled = prefs[KEY_OCR_ENABLED] ?: false,
                hideKeepAliveNotification = prefs[KEY_HIDE_KEEPALIVE_NOTIFICATION] ?: false,
            )
        }

    suspend fun setEnabled(enabled: Boolean) {
        context.autoLedgerDataStore.edit { it[KEY_ENABLED] = enabled }
    }

    suspend fun setDefaultAccount(accountId: Long?) {
        context.autoLedgerDataStore.edit { prefs ->
            if (accountId == null) prefs.remove(KEY_DEFAULT_ACCOUNT) else prefs[KEY_DEFAULT_ACCOUNT] = accountId
        }
    }

    suspend fun setDefaultCategory(categoryId: Long?) {
        context.autoLedgerDataStore.edit { prefs ->
            if (categoryId == null) prefs.remove(KEY_DEFAULT_CATEGORY)
            else prefs[KEY_DEFAULT_CATEGORY] = categoryId
        }
    }

    suspend fun setRecognitionLogEnabled(enabled: Boolean) {
        context.autoLedgerDataStore.edit { it[KEY_RECOGNITION_LOG] = enabled }
    }

    suspend fun setHideKeepAliveNotification(hide: Boolean) {
        context.autoLedgerDataStore.edit { it[KEY_HIDE_KEEPALIVE_NOTIFICATION] = hide }
    }

    suspend fun setOcrEnabled(enabled: Boolean) {
        context.autoLedgerDataStore.edit { it[KEY_OCR_ENABLED] = enabled }
    }

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("enabled")
        val KEY_DEFAULT_ACCOUNT = longPreferencesKey("default_account_id")
        val KEY_DEFAULT_CATEGORY = longPreferencesKey("default_category_id")
        val KEY_RECOGNITION_LOG = booleanPreferencesKey("recognition_log_enabled")
        val KEY_OCR_ENABLED = booleanPreferencesKey("ocr_enabled")
        val KEY_HIDE_KEEPALIVE_NOTIFICATION = booleanPreferencesKey("hide_keepalive_notification")
    }
}
