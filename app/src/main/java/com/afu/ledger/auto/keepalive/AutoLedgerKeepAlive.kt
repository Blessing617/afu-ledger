package com.afu.ledger.auto.keepalive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
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

    // ---------------------------------------------------------------
    // 授权被剥离时的告警
    // ---------------------------------------------------------------

    const val REVOKED_NOTIFICATION_ID: Int = 3002

    private const val REVOKED_CHANNEL_ID = "auto_ledger_revoked"

    /**
     * **这是唯一一个允许打断用户的通道**（IMPORTANCE_DEFAULT：有声音、可横幅）。
     *
     * 理由：HyperOS 在进入超级省电等场景会对本应用执行 force-stop，
     * 而 Android 在 force-stop 无障碍应用时会把它的无障碍权限一并剥离。
     * 此时自动记账**彻底失效，且用户毫无感知** —— 之后每一次付款都不会记账，
     * 用户却以为它在工作。这种「静默的永久失效」值得打断一次。
     *
     * 通道被单独创建，用户随时可以在系统里单独关掉它，不影响常驻状态通知。
     */
    fun ensureRevokedChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(REVOKED_CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            REVOKED_CHANNEL_ID,
            "自动记账已失效",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "系统把无障碍权限撤掉时提醒你重新开启；不开启则自动记账不会工作"
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    /** 权限被剥离后的告警通知：[action] 直接跳到系统的无障碍设置页。 */
    fun buildRevokedNotification(context: Context, frozenInsteadOfRevoked: Boolean): Notification {
        ensureRevokedChannel(context)
        val contentIntent = PendingIntent.getActivity(
            context,
            REVOKED_NOTIFICATION_ID,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val title = if (frozenInsteadOfRevoked) "自动记账可能已停止工作" else "自动记账已被系统关闭"
        val text = if (frozenInsteadOfRevoked) {
            "很久没有收到支付页面事件了，点这里重新开启一次无障碍服务"
        } else {
            "系统撤掉了无障碍权限，之后不会自动记账。点这里重新开启"
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, REVOKED_CHANNEL_ID)
        } else {
            Notification.Builder(context)
        }

        return builder
            .setSmallIcon(R.drawable.ic_stat_auto_ledger)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            // 带「开启」动作：用户点一下就能跳过去，不用在设置里找
            .addAction(
                Notification.Action.Builder(
                    null,
                    "去开启",
                    contentIntent,
                ).build(),
            )
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_ERROR)
            .setPriority(Notification.PRIORITY_DEFAULT)
            .build()
    }

    /** 显示 / 清除失效告警。幂等。 */
    fun showRevokedNotification(context: Context, frozenInsteadOfRevoked: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            manager.notify(
                REVOKED_NOTIFICATION_ID,
                buildRevokedNotification(context, frozenInsteadOfRevoked),
            )
        }
    }

    fun clearRevokedNotification(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching { manager.cancel(REVOKED_NOTIFICATION_ID) }
    }
}
