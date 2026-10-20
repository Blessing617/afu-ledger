package com.afu.ledger.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.afu.ledger.AfuApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 系统环境变化后重排闹钟。
 *
 * AlarmManager 里的闹钟**不会**跨重启保留，改时间/时区后旧的绝对时刻也失效了。
 * 这几条广播缺一不可，否则表现为「重启之后定时记账再也不触发」——
 * 而用户根本不知道是重启导致的。
 */
class ScheduleRestoreReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> Unit

            else -> return
        }

        val app = context.applicationContext as? AfuApplication ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 先补算（重启期间可能错过期次），再按最新状态重排
                app.runDueSchedulesNow()
            } finally {
                runCatching { pending.finish() }
            }
        }
    }
}
