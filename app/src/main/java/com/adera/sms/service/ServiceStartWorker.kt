package com.adera.sms.service

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExpeditedRetryPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.adera.sms.data.AppDatabase

/**
 * WorkManager expedited worker that starts [CallMonitorService].
 *
 * WHY THIS EXISTS (Android 14+ Fix):
 *   On Android 14+, calling startForegroundService() directly from a BroadcastReceiver context
 *   (especially on LOCKED_BOOT_COMPLETED before first unlock) can be silently blocked by the
 *   "FGS from background" restriction introduced in Android 14. Using an expedited WorkManager
 *   job is a documented, Android-approved way to reliably start a foreground service from
 *   any background context, including boot.
 *
 * Reference: https://developer.android.com/guide/background/persistent/getting-started/define-work#expedited
 */
class ServiceStartWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "AderaSMS"

        /**
         * Schedule an expedited WorkManager job to start the CallMonitorService.
         * Safe to call from any context including BroadcastReceiver and boot.
         */
        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<ServiceStartWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueue(request)
            Log.d(TAG, "ServiceStartWorker: expedited job enqueued")
        }
    }

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        val settings = try {
            db.settingsDao().getSettings()
        } catch (e: Exception) {
            // DB unavailable (direct boot, before first unlock) — start defensively
            Log.w(TAG, "ServiceStartWorker: DB read failed — starting service defensively", e)
            null
        }

        return if (settings == null || settings.autoReplyEnabled) {
            Log.i(TAG, "ServiceStartWorker: starting CallMonitorService")
            CallMonitorService.start(applicationContext)
            Result.success()
        } else {
            Log.d(TAG, "ServiceStartWorker: auto-reply disabled — not starting service")
            Result.success()
        }
    }

    override suspend fun getForegroundInfo() =
        // Required for expedited workers on Android 12+
        CallMonitorService.buildForegroundInfo(applicationContext)
}
