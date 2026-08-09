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
import com.adera.sms.analytics.AnalyticsManager
import com.adera.sms.data.AppDatabase
import com.adera.sms.data.entity.CallStatus
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * WorkManager worker responsible for sending the auto-reply SMS and updating the log entry.
 *
 * Retry policy: one retry after 30 seconds (LINEAR backoff).
 * If the second attempt also fails, the log entry is marked [CallStatus.FAILED].
 *
 * SIM selection (Item 4): STRICT — only uses the exact subscription ID from the missed call.
 * If the subscription ID is not valid, the entry is logged as FAILED and no SMS is sent.
 * There is NO fallback to a default SIM or the system default SmsManager.
 *
 * Signature (Item 10): " By Adera SMS" is appended to every outgoing message at send time.
 * This is not part of the editable template.
 *
 * SecurityException (SEND_SMS revoked): treated as hard failure — no retry.
 */
class SmsSenderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "AderaSMS"
        const val SIGNATURE = "\n\nBy Adera SMS"

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
                    KEY_CALL_LOG_ENTRY_ID to logEntryId
                )
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun doWork(): Result {
        val callerNumber   = inputData.getString(KEY_CALLER_NUMBER)
        val subscriptionId = inputData.getInt(KEY_SUBSCRIPTION_ID, SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        val templateText   = inputData.getString(KEY_TEMPLATE_TEXT)
        val logEntryId     = inputData.getLong(KEY_CALL_LOG_ENTRY_ID, -1L)

        if (callerNumber.isNullOrBlank() || templateText.isNullOrBlank()) {
            Log.e(TAG, "SmsSenderWorker: missing required input — bug in CallMonitorService")
            return Result.failure()
        }

        val db = AppDatabase.getInstance(applicationContext)

        // ITEM 4: Strict SIM matching — if subscription ID is not valid, fail immediately.
        // Do NOT fall back to a default or system SIM.
        if (subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            Log.e(TAG, "SmsSenderWorker: subscription ID is INVALID — cannot determine which SIM received the call. Marking FAILED.")
            if (logEntryId != -1L) db.callLogDao().updateStatus(logEntryId, CallStatus.FAILED)
            return Result.failure()
        }

        // ITEM 10: Append mandatory signature
        val fullMessage = templateText + SIGNATURE

        Log.i(TAG, "SmsSenderWorker: sending to ${callerNumber.take(3)}***, subId=$subscriptionId, attempt ${runAttemptCount + 1}")

        return try {
            val smsManager = resolveSmsManager(subscriptionId)

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
                        } catch (e: Exception) { /* already unregistered */ }

                        if (resultCode == Activity.RESULT_OK) {
                            Log.i(TAG, "SMS sent successfully (attempt ${runAttemptCount + 1})")
                            if (continuation.isActive) continuation.resume(Result.success())
                        } else {
                            Log.e(TAG, "SMS send failed with resultCode: $resultCode (attempt ${runAttemptCount + 1})")
                            if (runAttemptCount < 1) {
                                Log.d(TAG, "Scheduling one retry in ~30 seconds")
                                if (continuation.isActive) continuation.resume(Result.retry())
                            } else {
                                Log.e(TAG, "SMS failed after retry — marking FAILED")
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
                    try { applicationContext.unregisterReceiver(receiver) } catch (e: Exception) {}
                }

                try {
                    smsManager.sendTextMessage(callerNumber, null, fullMessage, sentIntent, null)
                } catch (e: Exception) {
                    try { applicationContext.unregisterReceiver(receiver) } catch (ex: Exception) {}
                    if (continuation.isActive) continuation.resume(Result.failure())
                }
            }.let { result ->
                when (result) {
                    is Result.Success -> {
                        if (logEntryId != -1L) db.callLogDao().updateStatus(logEntryId, CallStatus.SENT)
                        AnalyticsManager.autoReplySent(applicationContext)
                    }
                    is Result.Failure -> {
                        if (logEntryId != -1L) db.callLogDao().updateStatus(logEntryId, CallStatus.FAILED)
                    }
                    else -> {}
                }
                result
            }

        } catch (e: SecurityException) {
            // SEND_SMS was revoked — no point retrying.
            Log.e(TAG, "SEND_SMS permission denied — user must re-grant in system settings", e)
            if (logEntryId != -1L) db.callLogDao().updateStatus(logEntryId, CallStatus.FAILED)
            Result.failure()

        } catch (e: Exception) {
            Log.e(TAG, "SMS send failed (attempt ${runAttemptCount + 1}): ${e.message}", e)
            if (runAttemptCount < 1) {
                Log.d(TAG, "Scheduling one retry in ~30 seconds")
                Result.retry()
            } else {
                Log.e(TAG, "SMS failed after retry — marking FAILED")
                if (logEntryId != -1L) db.callLogDao().updateStatus(logEntryId, CallStatus.FAILED)
                Result.failure()
            }
        }
    }

    /**
     * ITEM 4: Uses ONLY the exact subscription ID that received the call.
     * No fallback to a default SIM.
     */
    @Suppress("DEPRECATION")
    private fun resolveSmsManager(subscriptionId: Int): SmsManager {
        Log.d(TAG, "SIM: using call subscription $subscriptionId")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            applicationContext.getSystemService(SmsManager::class.java)
                .createForSubscriptionId(subscriptionId)
        else
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
    }
}
