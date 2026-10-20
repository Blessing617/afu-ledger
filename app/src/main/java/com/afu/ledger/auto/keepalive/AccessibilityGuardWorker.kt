package com.afu.ledger.auto.keepalive

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * 无障碍守护巡检。
 *
 * 每 15 分钟（WorkManager 的周期下限）检查一次：
 * 服务还在不在启用列表里，不在就用 Shizuku 静默写回去。
 *
 * 这是「划掉也不影响使用」的关键一环：用户划掉应用后系统会撤销无障碍权限，
 * 等 Shizuku 巡检到就会自动恢复，用户下次付款时自动记账照常工作。
 *
 * 没有 Shizuku 时这个 Worker 什么都不做，直接返回成功 ——
 * 它在那种情况下没有能力，也不该报错或重试。
 */
class AccessibilityGuardWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!ShizukuGuard.hasPermission()) {
            // 没有 Shizuku 权限：无事可做，也不必重试
            return Result.success()
        }

        ShizukuGuard.grantBackgroundKeepAlive(applicationContext)

        if (!AccessibilityWatcher.isServiceEnabled(applicationContext)) {
            ShizukuGuard.enableService(applicationContext)
        }

        // 巡检完刷新一次状态，让 UI 与通知跟上
        AccessibilityWatcher.refresh(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "auto_ledger_accessibility_guard"

        /** 幂等；重复调用安全。 */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AccessibilityGuardWorker>(
                15,
                TimeUnit.MINUTES,
            ).setConstraints(Constraints.NONE).build()

            runCatching {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
            }
        }

        fun cancel(context: Context) {
            runCatching { WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME) }
        }
    }
}
