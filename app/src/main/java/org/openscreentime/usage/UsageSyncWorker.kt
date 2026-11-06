package org.openscreentime.usage

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import org.openscreentime.OpenScreenTimeApplication
import java.util.concurrent.TimeUnit

/**
 * Runs every few hours in the background: saves recent usage before Android
 * forgets the detailed events, and deletes history older than the user's
 * setting. WorkManager keeps it scheduled across reboots.
 */
class UsageSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as OpenScreenTimeApplication).container
        return try {
            val settings = container.settingsRepository.settings.first()
            if (settings.trackingEnabled && container.usageAccessChecker.hasUsageAccess()) {
                container.usageRepository.sync()
            }
            container.usageRepository.deleteOldHistory(settings.historyLength)
            Result.success()
        } catch (e: Exception) {
            // Only the error type is logged: never app names or usage data.
            Log.w(TAG, "Usage sync failed: ${e.javaClass.simpleName}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "UsageSyncWorker"
        private const val WORK_NAME = "usage-sync"

        /** Schedules the job. Safe to call often: an existing schedule is kept. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UsageSyncWorker>(3, TimeUnit.HOURS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
