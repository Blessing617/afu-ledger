package com.afu.ledger.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.assetDataStore: DataStore<Preferences> by preferencesDataStore(name = "asset_settings")

/**
 * 资产页的显示偏好。
 *
 * 目前只有「金额隐私遮罩」一项，但它是**持久化**的：用户点一下遮罩后，离开资产页、
 * 重启应用都应该继续保持隐藏，直到再次点按。存在这里而不是数据库，是因为它不属于账本数据，
 * 也不需要参与备份/导入。
 */
class AssetPreferences(
    private val context: Context,
) {

    val amountHidden: Flow<Boolean> = context.assetDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs -> prefs[KEY_AMOUNT_HIDDEN] ?: false }

    suspend fun setAmountHidden(hidden: Boolean) {
        context.assetDataStore.edit { it[KEY_AMOUNT_HIDDEN] = hidden }
    }

    private companion object {
        val KEY_AMOUNT_HIDDEN = booleanPreferencesKey("assets_amount_hidden")
    }
}
