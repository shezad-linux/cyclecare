package com.cyclecare.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class ReminderRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = runCatching {
        val application = applicationContext as CycleCareApplication
        application.appContainer.reminderScheduler.refreshSchedules()
        Result.success()
    }.getOrElse { Result.retry() }

    companion object {
        internal const val periodicWorkName = "cyclecare-reminder-refresh-periodic"
        internal const val immediateWorkName = "cyclecare-reminder-refresh-immediate"

        fun enqueuePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                periodicWorkName,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ReminderRefreshWorker>(1, TimeUnit.DAYS).build(),
            )
        }

        fun enqueueImmediate(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                immediateWorkName,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ReminderRefreshWorker>().build(),
            )
        }
    }
}
