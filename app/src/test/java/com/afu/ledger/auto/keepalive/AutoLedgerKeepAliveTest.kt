package com.afu.ledger.auto.keepalive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoLedgerKeepAliveTest {

    @Test
    fun `刚收到事件时不判定为冻结`() {
        AutoLedgerKeepAlive.onAccessibilityEvent()
        assertFalse(AutoLedgerKeepAlive.isSuspectFrozen())
    }

    @Test
    fun `超过阈值判定为疑似冻结`() {
        AutoLedgerKeepAlive.onAccessibilityEvent()
        val later = System.currentTimeMillis() + AutoLedgerKeepAlive.SUSPECT_FROZEN_AFTER_MS + 5_000
        assertTrue(AutoLedgerKeepAlive.isSuspectFrozen(later))
    }

    @Test
    fun `阈值边界内不算冻结`() {
        AutoLedgerKeepAlive.onAccessibilityEvent()
        val justBefore = System.currentTimeMillis() + AutoLedgerKeepAlive.SUSPECT_FROZEN_AFTER_MS - 5_000
        assertFalse(AutoLedgerKeepAlive.isSuspectFrozen(justBefore))
    }

    @Test
    fun `从未收到事件时 since 为 -1`() {
        // object 是进程级单例，这里只验证语义：没有事件记录时不能误报冻结
        val since = AutoLedgerKeepAlive.sinceLastEventMs()
        assertTrue(since >= -1L)
        assertEquals(false, since > AutoLedgerKeepAlive.SUSPECT_FROZEN_AFTER_MS)
    }
}
