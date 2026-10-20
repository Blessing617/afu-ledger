package com.afu.ledger.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.afu.ledger.data.repository.ScheduleRuleRepository
import com.afu.ledger.domain.schedule.ScheduleCalculator
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 为「最近的下一期」排一个**单次**闹钟，触发后由 [ScheduleAlarmReceiver] 再排下一个。
 *
 * ## 为什么不用重复闹钟
 *
 * `setRepeating` 本身不精确（从 API 19 起被系统对齐到窗口），而且没有办法表达
 * 「每月 31 日收敛到月末」这种规则。单次闹钟 + 每次触发后重排是唯一能同时满足
 * 精确性与日历语义的做法。
 *
 * ## 精确闹钟拿不到时
 *
 * Android 12+ 的 `SCHEDULE_EXACT_ALARM` 可能被用户关掉（Android 14 新装默认就不给）。
 * 拿不到就退化为 `setAndAllowWhileIdle`：允许几分钟到十几分钟的延迟，记账场景可接受，
 * 迟到的期次也会被 [RunDueSchedulesUseCase] 按计划时刻补记。
 *
 * 不申请 `INTERNET`，这里全部是本地调度。
 */
object ScheduleScheduler {

    const val ACTION_RUN_DUE = "com.afu.ledger.action.RUN_DUE_SCHEDULES"

    private const val REQUEST_CODE = 4101

    fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            action = ACTION_RUN_DUE
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 把闹钟重置到「所有启用规则里最早的下一次」。没有可用期次就取消闹钟。 */
    suspend fun scheduleNext(
        context: Context,
        rules: ScheduleRuleRepository,
        now: LocalDateTime = LocalDateTime.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val next = rules.enabled()
            .mapNotNull { ScheduleCalculator.nextOccurrence(it, now) }
            .minOrNull()

        val pi = pendingIntent(context)
        if (next == null) {
            manager.cancel(pi)
            return
        }

        val triggerAt = next.atZone(zone).toInstant().toEpochMilli()
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        runCatching {
            if (canExact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }

    /** 精确闹钟是否可用（设置页用来显示状态与引导）。 */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        return manager.canScheduleExactAlarms()
    }
}
