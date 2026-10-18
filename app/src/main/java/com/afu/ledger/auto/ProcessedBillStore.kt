package com.afu.ledger.auto

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

private val Context.processedBillDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "processed_bills")

/**
 * 已处理过的账单指纹，持久化在 DataStore 里。
 *
 * ## 为什么必须持久化
 *
 * 原先是纯内存的 [DetectionDeduper]，60 秒过期。真机踩到的后果是：
 * 用户对某张账单点了「忽略」，退出再把同一张账单打开 —— 又弹一次；
 * 重启应用更是每次都弹。用户只能反复点忽略，体验上等于「忽略按钮没用」。
 *
 * ## 两种有效期
 *
 * - **弹窗展示过**：短有效期（[SHOWN_TTL_MILLIS]）。用户可能只是没注意到卡片就超时消失了，
 *   十分钟内重开同一张账单不必再打扰，但过一会儿仍有机会补记。
 * - **忽略 / 已入账**：长有效期（[DEFAULT_TTL_MILLIS]）。这是用户的明确决定，必须尊重。
 *
 * ## 为什么不用 Room
 *
 * 记一条「这笔账单处理过」不属于账本数据，也不需要查询/关联。加表要动数据库版本、
 * 写迁移，代价远大于收益。隐私上也更干净：只存指纹与时间戳。
 */
class ProcessedBillStore(
    private val context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** 该指纹当前是否仍在有效期内（即不该再提示）。 */
    suspend fun contains(fingerprint: String): Boolean {
        val raw = read()[prefKey(fingerprint)] ?: return false
        val entry = parse(raw) ?: return false
        if (clock() - entry.stamp >= entry.ttlMillis) {
            remove(listOf(prefKey(fingerprint)))
            return false
        }
        return true
    }

    /** 记下「这张账单已经处理过」。 */
    suspend fun mark(fingerprint: String, ttlMillis: Long = DEFAULT_TTL_MILLIS) {
        val now = clock()
        context.processedBillDataStore.edit { prefs ->
            prefs[prefKey(fingerprint)] = "$now:$ttlMillis"
            pruneExpired(prefs, now)
        }
    }

    /** 弹窗刚展示时就记一次，防止卡片还没关就被同一页面反复命中。 */
    suspend fun markShown(fingerprint: String) = mark(fingerprint, SHOWN_TTL_MILLIS)

    suspend fun clear() {
        context.processedBillDataStore.edit { it.clear() }
    }

    private suspend fun read(): Preferences =
        context.processedBillDataStore.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .first()

    private suspend fun remove(keys: List<Preferences.Key<String>>) {
        context.processedBillDataStore.edit { prefs -> keys.forEach { prefs.remove(it) } }
    }

    private fun pruneExpired(prefs: MutablePreferences, now: Long) {
        prefs.asMap().toMap().forEach { (key, value) ->
            @Suppress("UNCHECKED_CAST")
            val typed = key as? Preferences.Key<String> ?: return@forEach
            val raw = value as? String ?: return@forEach
            val entry = parse(raw) ?: run {
                prefs.remove(typed)
                return@forEach
            }
            if (now - entry.stamp >= entry.ttlMillis) prefs.remove(typed)
        }
    }

    private data class Entry(val stamp: Long, val ttlMillis: Long)

    private fun parse(raw: String): Entry? {
        val parts = raw.split(':')
        if (parts.size != 2) return null
        val stamp = parts[0].toLongOrNull() ?: return null
        val ttl = parts[1].toLongOrNull() ?: return null
        return Entry(stamp, ttl)
    }

    /**
     * 指纹原样编码进 key。
     *
     * 不用 `hashCode()`：碰撞会让两张不同账单共用一个指纹，直接表现为「某张账单永远不弹」，
     * 这是最难排查的一类漏记。Base64 编码的 key 更长，但没有碰撞风险。
     */
    private fun prefKey(fingerprint: String): Preferences.Key<String> {
        val encoded = android.util.Base64.encodeToString(
            fingerprint.toByteArray(Charsets.UTF_8),
            android.util.Base64.URL_SAFE or
                android.util.Base64.NO_PADDING or
                android.util.Base64.NO_WRAP,
        )
        return stringPreferencesKey("bill_$encoded")
    }

    companion object {
        /** 忽略 / 已入账的记录保留 7 天。 */
        const val DEFAULT_TTL_MILLIS: Long = 7L * 24 * 60 * 60 * 1000

        /** 「刚弹过一次」的记录保留 10 分钟。 */
        const val SHOWN_TTL_MILLIS: Long = 10L * 60 * 1000
    }
}
