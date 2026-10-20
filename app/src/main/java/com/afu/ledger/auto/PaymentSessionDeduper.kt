package com.afu.ledger.auto

/**
 * 会话级去重：挡住「同一笔付款被两个页面各识别一次」。
 *
 * ## 为什么还需要这一层
 *
 * [ProcessedBillStore] 与 [DetectionDeduper] 都按 [PaymentDraft.fingerprint] 比对，
 * 而指纹在「一页有交易单号、另一页没有」时**必然分裂**。真机实测支付宝一笔 20.00 元付款：
 *
 * 1. 「付款成功（聚合收款）」页 —— 有交易单号，没有商户字段
 * 2. 「支付成功」页 —— 没有交易单号，有商户名
 *
 * 两个指纹分别是 `pkg|1004261...` 与 `pkg|2000|EXPENSE|202610202207`，
 * 指纹层互相看不见，于是各弹一次确认、各记一笔。
 *
 * 这里改用**金额 + 方向**作为会话键，在 [DEFAULT_WINDOW_MILLIS] 内视为同一笔。
 *
 * ## 例外：两个单号都非空且不同
 *
 * 「60 秒内买了两瓶水，各 2.00 元」是真实存在的场景，两笔各有自己的交易单号。
 * 因此只要两次命中的单号都解析成功且不同，就 MUST 放过第二次 —— 单号是比金额更强的身份证据。
 * 反过来，一方单号为空时无从比较，按会话层合并（宁可漏一次弹窗，也不要重复入账）。
 *
 * 商户名不参与会话键（同 [PaymentDraft.fingerprint] 的理由：OCR 猜出来会变）。
 */
class PaymentSessionDeduper(
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private data class Entry(val at: Long, val tradeNo: String?)

    private val recent = LinkedHashMap<String, Entry>(maxEntries, 0.75f, true)

    /**
     * @return true 表示这次命中属于会话里已经出现过的那一笔，应当跳过。
     */
    @Synchronized
    fun isDuplicate(draft: PaymentDraft): Boolean {
        val now = clock()
        pruneExpired(now)

        val key = sessionKey(draft)
        val previous = recent[key]
        val currentNo = draft.tradeNo?.takeIf { it.isNotBlank() }

        if (previous == null) {
            remember(key, now, currentNo)
            return false
        }

        val previousNo = previous.tradeNo?.takeIf { it.isNotBlank() }
        val differentTradeNos = previousNo != null && currentNo != null && previousNo != currentNo
        if (differentTradeNos) {
            // 两个单号都是真的且不同 —— 这是两笔，不让会话层误杀
            remember(key, now, currentNo)
            return false
        }

        remember(key, now, currentNo ?: previousNo)
        return true
    }

    @Synchronized
    fun reset() = recent.clear()

    private fun remember(key: String, now: Long, tradeNo: String?) {
        recent[key] = Entry(now, tradeNo)
        while (recent.size > maxEntries) {
            val oldest = recent.entries.firstOrNull() ?: return
            recent.remove(oldest.key)
        }
    }

    private fun sessionKey(draft: PaymentDraft): String =
        "${draft.sourcePackage}|${draft.amount.cents}|${draft.direction.name}"

    private fun pruneExpired(now: Long) {
        val iterator = recent.entries.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value.at >= windowMillis) iterator.remove()
        }
    }

    companion object {
        /**
         * 会话窗口。
         *
         * 支付宝从「付款成功」到「支付成功」通常相隔 1–3 秒，用户手动点进账单详情也常在
         * 半分钟内。两分钟足够覆盖，又不会把「两分钟后又买了同样价钱的东西」吞掉
         * （那种场景通常还带着各自不同的交易单号，会被例外放行）。
         */
        const val DEFAULT_WINDOW_MILLIS: Long = 120_000L
        const val DEFAULT_MAX_ENTRIES: Int = 32
    }
}
