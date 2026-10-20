package com.afu.ledger.schedule

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.afu.ledger.MainActivity
import com.afu.ledger.R

/**
 * 「先提醒我确认」型定时规则的待确认通知。
 *
 * 一次性通知、可被滑掉即可：它的作用是「让用户知道有一期待入账」，
 * 常驻与计数交给定时记账页顶部的待确认卡片。
 */
object ScheduleNotifications {

    private const val CHANNEL_ID = "schedule_pending"
    private const val NOTIFICATION_ID = 4201

    fun notifyPending(context: Context, count: Int) {
        if (count <= 0) {
            clear(context)
            return
        }
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = "有 $count 期定时记账等你确认，打开「我的 → 定时记账」入账"
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            Notification.Builder(context)
        }
        val notification = builder
            .setSmallIcon(R.drawable.ic_stat_auto_ledger)
            .setContentTitle("$count 期定时账单待确认")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    fun clear(context: Context) {
        runCatching {
            context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "定时记账待确认",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "定时记账到期、等待你确认的提醒"
            },
        )
    }
}
