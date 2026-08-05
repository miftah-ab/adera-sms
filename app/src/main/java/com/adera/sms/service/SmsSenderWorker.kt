package com.adera.sms.service

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.adera.sms.data.AppDatabase
import com.adera.sms.data.entity.CallStatus
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * WorkManager worker responsible for sending the auto-reply SMS and updating the log entry.
 *
 * Retry policy (spec §12.7): one retry after 30 seconds (LINEAR backoff).
 * If the second attempt also fails, the log entry is marked [CallStatus.FAILED].
 *
 * SIM selection — three-level fallback (spec §12.7):
 *   1. Use the subscription ID from the missed call (most accurate)
 *   2. Use the device's default SMS subscription ID
 *   3. Use SmsManager.getDefault() — last resort, logs a warning
 *
 * SecurityException (SEND_SMS revoked by user after grant): treated as hard failure.
 * No retry — the user must go to system settings and restore the permission, where
 * the Home screen will show a persistent warning banner (runtime permission recovery,
 * spec §12.7).
 */
class SmsSenderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "AderaSMS"

        const val KEY_CALLER_NUMBER     = "caller_number"
        const val KEY_SUBSCRIPTION_ID   = "subscription_id"
        const val KEY_TEMPLATE_TEXT     = "template_text"
        const val KEY_CALL_LOG_ENTRY_ID = "call_log_entry_id"

        /** Convenience builder used by [CallMonitorService]. */
        fun buildRequest(
            callerNumber: String,
            subscriptionId: Int,
            templateText: String,
            logEntryId: Long
        ) = OneTimeWorkRequestBuilder<SmsSenderWorker>()
            .setInputData(
                workDataOf(
                    KEY_CALLER_NUMBER     to callerNumber,
                    KEY_SUBSCRIPTION_ID   to subscriptionId,
                    KEY_TEMPLATE_TEXT     to templateText,
                    KEY_CALL_LOG_ENTRY_ID to logEntryId.toInt()
                )
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun doWork(): Result {
        val callerNumber   = inputData.getString(KEY_CALLER_NUMBER)
        val subscriptionId = inputData.getInt(KEY_SUBSCRIPTION_ID, SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        val templateText   = inputData.getString(KEY_TEMPLATE_TEXT)
        val logEntryId     = inputData.getInt(KEY_CALL_LOG_ENTRY_ID, -1)

        if (callerNumber.isNullOrBlank() || templateText.isNullOrBlank()) {
            Log.e(TAG, "SmsSenderWorker: missing required input — bug in CallMonitorService")
            return Result.failure()
        }

        Log.i(TAG, "SmsSenderWorker: sending to ${callerNumber.take(3)}***, attempt ${runAttemptCount + 1}")

        val db = AppDatabase.getInstance(applicationContext)

        return try {
            val smsManager = selectSmsManager(subscriptionId)
            
            suspendCancellableCoroutine { continuation ->
                val intentAction = "com.adera.sms.SMS_SENT_${UUID.randomUUID()}"
                
                val sentIntent = PendingIntent.getBroadcast(
                    applicationContext,
                    0,
                    Intent(intentAction).setPackage(applicationContext.packageName),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        try {
                            applicationContext.unregisterReceiver(this)
                        } catch (e: Exception) {
                            // Already unregistered
                        }
                        
                        if (resultCode == Activity.RESULT_OK) {
                            Log.i(TAG, "SMS sent successfully (attempt ${runAttemptCount + 1})")
                            if (logEntryId != -1) {
                                // DB ops run on Room's dispatcher, so we don't need a specific scope here
                                // wait, we shouldn't block the receiver thread, but updateStatus is suspend
                                // We can use GlobalScope, but better to do it synchronously if possible, 
                                // wait, updateStatus is a suspend function. Let's just return Result and do DB ops after.
                            }
                            if (continuation.isActive) continuation.resume(Result.success())
                        } else {
                            Log.e(TAG, "SMS send failed with resultCode: $resultCode (attempt ${runAttemptCount + 1})")
                            if (runAttemptCount < 1) {
                                Log.d(TAG, "Scheduling one retry in ~30 seconds")
                                if (continuation.isActive) continuation.resume(Result.retry())
                            } else {
                                Log.e(TAG, "SMS failed after retry - marking FAILED")
                                if (continuation.isActive) continuation.resume(Result.failure())
                            }
                        }
                    }
                }

                val filter = IntentFilter(intentAction)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    applicationContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    applicationContext.registerReceiver(receiver, filter)
                }

                continuation.invokeOnCancellation {
                    try {
                        applicationContext.unregisterReceiver(receiver)
                    } catch (e: Exception) {}
                }

                try {
                    smsManager.sendTextMessage(callerNumber, null, templateText, sentIntent, null)
                } catch (e: Exception) {
                    try {
                        applicationContext.unregisterReceiver(receiver)
                    } catch (ex: Exception) {}
                    if (continuation.isActive) continuation.resumeWith(Result.failure(e))
                }
            }.let { result ->
                // Now we update the DB based on the result
                when (result) {
                    is Result.Success -> {
                        if (logEntryId != -1) db.callLogDao().updateStatus(logEntryId, CallStatus.SENT)
                    }
                    is Result.Failure -> {
                        if (logEntryId != -1) db.callLogDao().updateStatus(logEntryId, CallStatus.FAILED)
                    }
                    else -> {}
                }
                result
            }

        } catch (e: SecurityException) {
            // SEND_SMS was revoked — no point retrying. The Home screen warning will prompt
            // the user to re-grant the permission.
            Log.e(TAG, "SEND_SMS permission denied — user must re-grant in system settings", e)
            if (logEntryId != -1) db.callLogDao().updateStatus(logEntryId, CallStatus.FAILED)
            Result.failure()

        } catch (e: Exception) {
            Log.e(TAG, "SMS send failed (attempt ${runAttemptCount + 1}): ${e.message}", e)
            if (runAttemptCount < 1) {
                Log.d(TAG, "Scheduling one retry in ~30 seconds")
                Result.retry()
            } else {
                Log.e(TAG, "SMS failed after retry — marking FAILED")
                if (logEntryId != -1) db.callLogDao().updateStatus(logEntryId, CallStatus.FAILED)
                Result.failure()
            }
        }
    }

    /**
     * Three-level SIM selection fallback chain (spec §12.7).
     * Logs a warning at each fallback level so the Activity Log can surface SIM issues.
     */
    @Suppress("DEPRECATION")
    private fun selectSmsManager(subscriptionId: Int): SmsManager {
        // Level 1: Use the SIM that received the missed call
        if (subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            Log.d(TAG, "SIM: using call subscription $subscriptionId")
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                applicationContext.getSystemService(SmsManager::class.java)
                    .createForSubscriptionId(subscriptionId)
            else
                SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        }

        // Level 2: Device default SMS SIM
        val defaultSubId = SmsManager.getDefaultSmsSubscriptionId()
        if (defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            Log.w(TAG, "SIM: call SIM unknown → falling back to default SMS sub $defaultSubId")
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                applicationContext.getSystemService(SmsManager::class.java)
                    .createForSubscriptionId(defaultSubId)
            else
                SmsManager.getSmsManagerForSubscriptionId(defaultSubId)
        }

        // Level 3: System default — OEM may choose any available SIM
        Log.w(TAG, "SIM: no subscription ID available → using system default SmsManager")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            applicationContext.getSystemService(SmsManager::class.java)
        else
            SmsManager.getDefault()
    }
}
