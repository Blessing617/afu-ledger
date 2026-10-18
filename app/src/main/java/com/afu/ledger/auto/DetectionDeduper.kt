package com.afu.ledger.auto

/**
 * 同一笔支付只提醒一次。
 *
 * 指纹 = 包名 + 金额 + 商户名。商户名参与指纹是有意的：
 * 每天都吃同一家的同一份饭、金额一样是常态，不能当成重复。
 */
class DetectionDeduper(
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val lastSeen = LinkedHashMap<String, Long>(maxEntries, 0.75f, true)

    @Synchronized
    fun shouldNotify(draft: PaymentDraft): Boolean {
        val now = clock()
        pruneExpired(now)

        val key = fingerprint(draft)
        val previous = lastSeen[key]
        if (previous != null && now - previous < windowMillis) return false

        lastSeen[key] = now
        trimToMaxSize()
        return true
    }

    @Synchronized
    fun reset() = lastSeen.clear()

    private fun pruneExpired(now: Long) {
        val iterator = lastSeen.entries.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value >= windowMillis) iterator.remove()
        }
    }

    private fun trimToMaxSize() {
        while (lastSeen.size > maxEntries) {
            val oldest = lastSeen.entries.firstOrNull() ?: return
            lastSeen.remove(oldest.key)
        }
    }

    private fun fingerprint(draft: PaymentDraft): String {
        val merchant = draft.merchant?.trim()?.lowercase().orEmpty()
        return "${draft.sourcePackage}|${draft.amount.cents}|$merchant"
    }

    companion object {
        const val DEFAULT_WINDOW_MILLIS: Long = 60_000L
        const val DEFAULT_MAX_ENTRIES: Int = 32
    }
}
