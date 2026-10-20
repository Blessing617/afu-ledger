package com.afu.ledger.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.afu.ledger.AfuApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 定时闹钟到点。
 *
 * 必须用 `goAsync()`：`onReceive` 返回后广播所在的进程随时可能被回收，
 * 而入账是数据库 IO，不能靠 `onReceive` 的默认生命周期保命。
 */
class ScheduleAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ScheduleScheduler.ACTION_RUN_DUE) return
        val app = context.applicationContext as? AfuApplication ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.runDueSchedulesNow()
            } finally {
                runCatching { pending.finish() }
            }
        }
    }
}
