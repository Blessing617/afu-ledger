package com.afu.ledger.schedule

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.afu.ledger.domain.model.TransType
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

private val Context.pendingOccurrenceDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "pending_occurrences")

/**
 * 待确认期次的写入端抽象。
 *
 * 抽成接口只为一个目的：执行引擎的单元测试不需要 Context / DataStore，
 * 用内存实现就能验证「提醒型规则只落待确认、不写交易」。
 */
interface PendingOccurrenceSink {
    suspend fun add(occurrence: PendingOccurrenceStore.Occurrence)
    suspend fun list(): List<PendingOccurrenceStore.Occurrence>
    suspend fun remove(key: String)
}

/**
 * 「提醒确认」型定时规则的待确认期次。
 *
 * 与 [com.afu.ledger.auto.PendingBillStore] 分开存：识别来的账单靠「默认账户 / 默认分类」补确认，
 * 而定时记账的每一期都已经指定了账户与分类，直接复用会把两类语义搅在一起。
 *
 * 用 DataStore 而不是 Room：这是临时态，确认或忽略后就删；不参与任何查询与统计。
 * 保留期与待确认账单一致（7 天），超时自动清掉，避免积压。
 */
class PendingOccurrenceStore(
    private val context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) : PendingOccurrenceSink {

    data class Occurrence(
        val ruleId: Long,
        val occurredAt: Long,
        val amountCents: Long,
        val type: TransType,
        val accountId: Long,
        val categoryId: Long,
        val note: String,
        val createdAt: Long,
    ) {
        val key: String get() = "$ruleId|$occurredAt"
    }

    override suspend fun add(occurrence: Occurrence) {
        val now = clock()
        context.pendingOccurrenceDataStore.edit { prefs ->
            prefs[prefKey(occurrence.key)] = encode(occurrence.copy(createdAt = if (occurrence.createdAt > 0) occurrence.createdAt else now))
            pruneExpired(prefs, now)
        }
    }

    override suspend fun list(): List<Occurrence> {
        val now = clock()
        val prefs = read()
        val out = ArrayList<Occurrence>()
        var needPrune = false
        prefs.asMap().forEach { (key, value) ->
            val typed = key as? Preferences.Key<String> ?: return@forEach
            val raw = value as? String ?: return@forEach
            val occurrence = decode(raw) ?: return@forEach
            if (now - occurrence.createdAt >= TTL_MILLIS) {
                needPrune = true
                return@forEach
            }
            out.add(occurrence)
        }
        if (needPrune) context.pendingOccurrenceDataStore.edit { pruneExpired(it, now) }
        return out.sortedBy { it.occurredAt }
    }

    override suspend fun remove(key: String) {
        context.pendingOccurrenceDataStore.edit { prefs -> prefs.remove(prefKey(key)) }
    }

    suspend fun clear() {
        context.pendingOccurrenceDataStore.edit { it.clear() }
    }

    private suspend fun read(): Preferences =
        context.pendingOccurrenceDataStore.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .first()

    private fun pruneExpired(prefs: MutablePreferences, now: Long) {
        val stale = ArrayList<Preferences.Key<String>>()
        prefs.asMap().forEach { (key, value) ->
            val typed = key as? Preferences.Key<String> ?: return@forEach
            val raw = value as? String ?: return@forEach
            val occurrence = decode(raw) ?: run { stale.add(typed); return@forEach }
            if (now - occurrence.createdAt >= TTL_MILLIS) stale.add(typed)
        }
        stale.forEach { prefs.remove(it) }
    }

    private fun encode(occurrence: Occurrence): String = listOf(
        occurrence.ruleId.toString(),
        occurrence.occurredAt.toString(),
        occurrence.amountCents.toString(),
        occurrence.type.name,
        occurrence.accountId.toString(),
        occurrence.categoryId.toString(),
        occurrence.note,
        occurrence.createdAt.toString(),
    ).joinToString(SEPARATOR)

    private fun decode(raw: String): Occurrence? {
        val parts = raw.split(SEPARATOR)
        if (parts.size < 8) return null
        return Occurrence(
            ruleId = parts[0].toLongOrNull() ?: return null,
            occurredAt = parts[1].toLongOrNull() ?: return null,
            amountCents = parts[2].toLongOrNull() ?: return null,
            type = runCatching { TransType.valueOf(parts[3]) }.getOrNull() ?: return null,
            accountId = parts[4].toLongOrNull() ?: return null,
            categoryId = parts[5].toLongOrNull() ?: return null,
            note = parts[6],
            createdAt = parts[7].toLongOrNull() ?: return null,
        )
    }

    private fun prefKey(key: String): Preferences.Key<String> {
        val encoded = android.util.Base64.encodeToString(
            key.toByteArray(Charsets.UTF_8),
            android.util.Base64.URL_SAFE or
                android.util.Base64.NO_PADDING or
                android.util.Base64.NO_WRAP,
        )
        return stringPreferencesKey("occ_$encoded")
    }

    companion object {
        /** 待确认期次保留 7 天。 */
        const val TTL_MILLIS: Long = 7L * 24 * 60 * 60 * 1000
        private const val SEPARATOR = "\u0001"
    }
}
