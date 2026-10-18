package com.afu.ledger.auto.keepalive

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.guardDataStore: DataStore<Preferences> by preferencesDataStore(name = "keepalive_state")

/**
 * 记录「无障碍曾经被成功启用过」。
 *
 * ## 为什么需要这个标记
 *
 * 只有在**曾经开过、后来没了**的时候，才能确定是「被系统撤销」；
 * 如果从没开过，那就是用户还没设置，两种情况给用户的引导应该不一样：
 *
 * - 从没开过 → 「去开启」
 * - 开过又没了 → 「被撤销了，大概率是把它从最近任务划掉导致，再开一次」
 *
 * 没有这个标记时，两种状态在 UI 上都长得一样，用户只会看到一句
 * 「未开启」而不知道发生了什么。
 */
class KeepAliveStateStore(private val context: Context) {

    val wasEverEnabled: Flow<Boolean> = context.guardDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs -> prefs[KEY_EVER_ENABLED] ?: false }

    suspend fun markEverEnabled() {
        context.guardDataStore.edit { it[KEY_EVER_ENABLED] = true }
    }

    suspend fun wasEverEnabledNow(): Boolean = wasEverEnabled.first()

    private companion object {
        val KEY_EVER_ENABLED = booleanPreferencesKey("accessibility_ever_enabled")
    }
}
