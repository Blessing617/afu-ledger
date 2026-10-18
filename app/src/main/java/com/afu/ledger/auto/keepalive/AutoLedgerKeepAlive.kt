package com.afu.ledger.auto.keepalive

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
 * 自动记账的运行状态与常驻通知。
 *
 * HyperOS 会在长时间后台后冻结进程（绑定还在、事件不来）。
 * 前台服务把进程优先级抬出 cached 区间，是降低冻结概率的主力手段；
 * [lastEventAt] 则让「冻结」变得可被发现，而不是静默失效。
 */
object AutoLedgerKeepAlive {

    const val NOTIFICATION_ID: Int = 3001

    private const val CHANNEL_ID = "auto_ledger_keepalive"

    /** 超过这个时长没有任何受支持应用的事件，就认为「疑似被冻结」。 */
    const val SUSPECT_FROZEN_AFTER_MS: Long = 30 * 60 * 1000L

    /** 最近一次收到受支持应用无障碍事件的时间；0 表示本次进程还没收到过。 */
    @Volatile
    var lastEventAt: Long = 0L
        private set

    /** 前台服务是否已启动。 */
    @Volatile
    var foregroundRunning: Boolean = false
        private set

    fun onAccessibilityEvent() {
        lastEventAt = System.currentTimeMillis()
    }

    fun markForegroundRunning(running: Boolean) {
        foregroundRunning = running
    }

    /** 距最近事件过去了多久；还没收到过事件时返回 -1。 */
    fun sinceLastEventMs(now: Long = System.currentTimeMillis()): Long =
        if (lastEventAt <= 0L) -1L else (now - lastEventAt)

    fun isSuspectFrozen(now: Long = System.currentTimeMillis()): Boolean {
        val since = sinceLastEventMs(now)
        return since > 0 && since > SUSPECT_FROZEN_AFTER_MS
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "自动记账运行状态",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "保持自动记账在后台可靠工作；不发声、不弹出"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    fun buildNotification(context: Context): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            Notification.Builder(context)
        }

        return builder
            .setSmallIcon(R.drawable.ic_stat_auto_ledger)
            .setContentTitle("自动记账运行中")
            .setContentText("识别微信 / 支付宝支付页，数据全部留在本机")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(Notification.PRIORITY_LOW)
            .build()
    }
}
