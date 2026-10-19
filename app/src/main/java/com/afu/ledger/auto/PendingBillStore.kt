package com.afu.ledger.auto

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

private val Context.pendingBillDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "pending_bills")

/**
 * 「识别到了、但用户还没确认」的账单。
 *
 * ## 为什么必须有这一层
 *
 * 钱迹与 iCost 的官方 FAQ 都写了同一件事：部分系统对支付类应用有**支付保护**，
 * 禁止在其界面上显示第三方悬浮窗，要退出应用才弹得出来。
 * 也就是说「识别成功」不等于「用户看到卡片」。
 *
 * 只在那一个瞬间弹窗、弹不出来就丢掉，等于静默丢账 —— 用户完全不知道漏了。
 * 所以识别成功后**先落库**，再尝试弹窗；弹不出来就靠通知与首页入口兜住。
 *
 * ## 为什么不用 Room
 *
 * 待确认账单是临时态，确认后就写进正式交易表了。加表要动数据库版本与迁移，
 * 而 DataStore 里存一份带 TTL 的快照就够：进程重启、手机重启都不会丢。
 */
class PendingBillStore(
    private val context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** 一条待确认账单。字段与 [PaymentDraft] 对齐，够用来补一次入账。 */
    data class PendingBill(
        val fingerprint: String,
        val amountCents: Long,
        val direction: TransType,
        val merchant: String?,
        val sourcePackage: String,
        val source: String,
        val occurredAt: Long,
        val occurredAtFromPage: Boolean,
        val tradeNo: String?,
        val pageCategory: String?,
        val createdAt: Long,
    ) {
        fun toDraft(): PaymentDraft = PaymentDraft(
            amount = com.afu.ledger.domain.model.Money(amountCents),
            direction = direction,
            merchant = merchant,
            sourcePackage = sourcePackage,
            pageType = PaymentPageType.BILL_DETAIL,
            occurredAt = occurredAt,
            occurredAtSource = if (occurredAtFromPage) TimeSource.PAGE else TimeSource.FALLBACK,
            capturedAt = createdAt,
            tradeNo = tradeNo,
            pageCategory = pageCategory,
        )
    }

    companion object {
        /** 待确认账单保留 7 天，与「忽略 7 天」保持同一节奏。 */
        const val TTL_MILLIS: Long = 7L * 24 * 60 * 60 * 1000

        private const val SEPARATOR = "\u0001"

        fun fromDraft(draft: PaymentDraft, createdAt: Long = System.currentTimeMillis()): PendingBill =
            PendingBill(
                fingerprint = draft.fingerprint(),
                amountCents = draft.amount.cents,
                direction = draft.direction,
                merchant = draft.merchant,
                sourcePackage = draft.sourcePackage,
                source = draft.source,
                occurredAt = draft.occurredAt,
                occurredAtFromPage = draft.occurredAtSource == TimeSource.PAGE,
                tradeNo = draft.tradeNo,
                pageCategory = draft.pageCategory,
                createdAt = createdAt,
            )
    }

    /** 记一条待确认。同指纹覆盖旧的（同一张账单不重复堆积）。 */
    suspend fun add(bill: PendingBill) {
        val now = clock()
        context.pendingBillDataStore.edit { prefs ->
            prefs[key(bill.fingerprint)] = encode(bill, now)
            pruneExpired(prefs, now)
        }
    }

    /** 当前还有效的待确认账单，按发生时间倒序。 */
    suspend fun list(): List<PendingBill> {
        val now = clock()
        val prefs = read()
        val out = ArrayList<PendingBill>()
        var needPrune = false
        prefs.asMap().forEach { (key, value) ->
            val typed = key as? Preferences.Key<String> ?: return@forEach
            val raw = value as? String ?: return@forEach
            val bill = decode(raw)
            if (bill == null) {
                return@forEach
            }
            if (now - bill.createdAt >= TTL_MILLIS) {
                needPrune = true
                return@forEach
            }
            out.add(bill)
        }
        if (needPrune) {
            context.pendingBillDataStore.edit { prefs2 -> pruneExpired(prefs2, now) }
        }
        return out.sortedByDescending { it.occurredAt }
    }

    suspend fun count(): Int = list().size

    suspend fun remove(fingerprint: String) {
        context.pendingBillDataStore.edit { prefs -> prefs.remove(key(fingerprint)) }
    }

    suspend fun clear() {
        context.pendingBillDataStore.edit { it.clear() }
    }

    private suspend fun read(): Preferences =
        context.pendingBillDataStore.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .first()

    private fun pruneExpired(prefs: MutablePreferences, now: Long) {
        val stale = ArrayList<Preferences.Key<String>>()
        prefs.asMap().forEach { (key, value) ->
            val typed = key as? Preferences.Key<String> ?: return@forEach
            val raw = value as? String ?: return@forEach
            val bill = decode(raw) ?: run { stale.add(typed); return@forEach }
            if (now - bill.createdAt >= TTL_MILLIS) stale.add(typed)
        }
        stale.forEach { prefs.remove(it) }
    }

    private fun encode(bill: PendingBill, now: Long): String = listOf(
        bill.fingerprint,
        bill.amountCents.toString(),
        bill.direction.name,
        bill.merchant.orEmpty(),
        bill.sourcePackage,
        bill.source,
        bill.occurredAt.toString(),
        if (bill.occurredAtFromPage) "1" else "0",
        bill.tradeNo.orEmpty(),
        bill.pageCategory.orEmpty(),
        if (bill.createdAt > 0) bill.createdAt else now,
    ).joinToString(SEPARATOR)

    private fun decode(raw: String): PendingBill? {
        val parts = raw.split(SEPARATOR)
        if (parts.size < 11) return null
        val cents = parts[1].toLongOrNull() ?: return null
        val direction = runCatching { TransType.valueOf(parts[2]) }.getOrNull() ?: return null
        return PendingBill(
            fingerprint = parts[0],
            amountCents = cents,
            direction = direction,
            merchant = parts[3].takeIf { it.isNotEmpty() },
            sourcePackage = parts[4],
            source = parts[5],
            occurredAt = parts[6].toLongOrNull() ?: return null,
            occurredAtFromPage = parts[7] == "1",
            tradeNo = parts[8].takeIf { it.isNotEmpty() },
            pageCategory = parts[9].takeIf { it.isNotEmpty() },
            createdAt = parts[10].toLongOrNull() ?: return null,
        )
    }

    /** 指纹原样编码进 key，理由同 [ProcessedBillStore]：避免 hashCode 碰撞。 */
    private fun key(fingerprint: String): Preferences.Key<String> {
        val encoded = android.util.Base64.encodeToString(
            fingerprint.toByteArray(Charsets.UTF_8),
            android.util.Base64.URL_SAFE or
                android.util.Base64.NO_PADDING or
                android.util.Base64.NO_WRAP,
        )
        return stringPreferencesKey("pending_$encoded")
    }
}
