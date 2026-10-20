package com.afu.ledger.schedule

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.afu.ledger.AfuApplication
import java.util.concurrent.TimeUnit

/**
 * 每日兜底：闹钟被省电策略吞掉时，至少每天检查一次。
 *
 * WorkManager 的周期任务最小间隔是 15 分钟且受 Doze 影响，**不能**当作准点手段；
 * 它的价值是「闹钟完全失效时仍然会发现到期期次并补记」。
 */
class ScheduleFallbackWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? AfuApplication ?: return Result.success()
        return runCatching {
            app.runDueSchedulesNow()
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        private const val WORK_NAME = "schedule_fallback_daily"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScheduleFallbackWorker>(1, TimeUnit.DAYS)
                .build()
            runCatching {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
            }
        }
    }
}
