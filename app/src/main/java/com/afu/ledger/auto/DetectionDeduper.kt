package com.afu.ledger.auto

/**
 * 短窗口内同一笔账单只提醒一次（内存级，进程重启即失效）。
 *
 * 长期去重由 [ProcessedBillStore] 负责；这里只是挡住「页面反复刷新导致连续弹窗」。
 *
 * 指纹复用 [PaymentDraft.fingerprint]：有交易单号时只认单号，
 * 没有单号时才退化为「包名 + 金额 + 方向 + 商户 + 账单时间」。
 * 用账单时间而不是识别时刻，才能区分「同一商户同金额的两笔」。
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

        val key = draft.fingerprint()
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

    companion object {
        const val DEFAULT_WINDOW_MILLIS: Long = 60_000L
        const val DEFAULT_MAX_ENTRIES: Int = 32
    }
}
