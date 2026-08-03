package com.adera.sms.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.CallLog
import android.telephony.PhoneStateListener
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.work.WorkManager
import com.adera.sms.AderaSmsApplication
import com.adera.sms.MainActivity
import com.adera.sms.R
import com.adera.sms.data.AppDatabase
import com.adera.sms.data.entity.AppSettings
import com.adera.sms.data.entity.CallLogEntry
import com.adera.sms.data.entity.CallStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.Calendar

/**
 * Foreground service that monitors phone call state and sends auto-reply SMS on missed calls.
 *
 * ARCHITECTURE:
 * - Registered as a foreground service (spec §12.3) — more resistant to OEM battery-killing
 *   (Tecno/Infinix/Itel) than a plain BroadcastReceiver.
 * - Uses TelephonyCallback (API 31+) or PhoneStateListener (API 26–30) per active SIM.
 * - One listener is registered per subscription ID for correct dual-SIM handling.
 * - START_STICKY: if the OS kills this service, Android restarts it automatically.
 *   Combined with battery optimization whitelist (set up in onboarding), this is the
 *   primary reliability defense on aggressive OEM builds.
 *
 * CALL STATE MACHINE (per subscription):
 *   RINGING → OFFHOOK → IDLE  =  answered call   (no reply)
 *   RINGING → IDLE             =  missed call     (send reply)
 *   Any transition not starting from RINGING is ignored.
 *
 * IMPORTANT — NO SMS LISTENER:
 *   This service only processes missed voice calls. There is intentionally NO incoming-SMS
 *   listener anywhere in this codebase. Adding one would create a reply loop between two
 *   Adera SMS users. Do not add SMS-triggered logic here or anywhere else.
 */
class CallMonitorService : Service() {

    companion object {
        private const val TAG = "AderaSMS"

        /** Per-number reply cooldown window (spec §12.7). Hidden constant — not user-facing. */
        private const val COOLDOWN_MS = 10 * 60 * 1000L  // 10 minutes

        /**
         * Delay after detecting IDLE state before querying the call log for the number.
         * Used only on API 31+ (TelephonyCallback path). The call log may not be updated
         * immediately when IDLE fires — 1.5 s covers all devices tested so far.
         */
        private const val CALL_LOG_QUERY_DELAY_MS = 1500L

        fun start(context: Context) {
            val intent = Intent(context, CallMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else
                context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallMonitorService::class.java))
        }
    }

    private lateinit var database: AppDatabase
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ── Per-subscription call state ───────────────────────────────────────────

    /** Tracks RINGING/OFFHOOK state per SIM during an active call event. */
    private data class PerSubState(
        var isRinging: Boolean   = false,
        var isOffHook: Boolean   = false,
        var callerNumber: String? = null   // populated on API < 31 only
    )

    private val callStates = mutableMapOf<Int, PerSubState>()

    @RequiresApi(Build.VERSION_CODES.S)
    private val telephonyCallbacks = mutableMapOf<Int, TelephonyCallback>()

    @Suppress("DEPRECATION")
    private val phoneStateListeners = mutableMapOf<Int, PhoneStateListener>()

    // ── Service lifecycle ─────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(applicationContext)
        startForegroundWithNotification()
        registerListeners()
        Log.i(TAG, "CallMonitorService started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY  // Restart automatically after OEM kill

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterListeners()
        serviceScope.cancel()
        Log.i(TAG, "CallMonitorService destroyed — will restart via START_STICKY")
        super.onDestroy()
    }

    // ── Foreground notification ───────────────────────────────────────────────

    private fun startForegroundWithNotification() {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, AderaSmsApplication.CHANNEL_ID_SERVICE)
            .setContentTitle(getString(R.string.notification_service_title))
            .setContentText(getString(R.string.notification_service_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                AderaSmsApplication.NOTIFICATION_ID_SERVICE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(AderaSmsApplication.NOTIFICATION_ID_SERVICE, notification)
        }
    }

    // ── Telephony listener registration ──────────────────────────────────────

    private fun registerListeners() {
        val subIds = activeSubscriptionIds()
        Log.d(TAG, "Registering listeners for subscriptions: $subIds")
        subIds.forEach { subId ->
            callStates[subId] = PerSubState()
            val tm = telephonyManager(subId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val cb = newTelephonyCallback(subId)
                telephonyCallbacks[subId] = cb
                tm.registerTelephonyCallback(mainExecutor, cb)
            } else {
                @Suppress("DEPRECATION")
                val listener = newPhoneStateListener(subId)
                phoneStateListeners[subId] = listener
                @Suppress("DEPRECATION")
                tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            }
        }
    }

    /**
     * Returns the list of active subscription IDs.
     * Falls back to [-1] (system default) if READ_PHONE_STATE is not granted or the
     * subscription list is empty (common on some Tecno devices right after permission grant).
     */
    private fun activeSubscriptionIds(): List<Int> {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_PHONE_STATE not granted — using default SIM fallback")
            return listOf(-1)
        }
        val subs = getSystemService(SubscriptionManager::class.java)
            .activeSubscriptionInfoList
        return if (subs.isNullOrEmpty()) {
            Log.w(TAG, "No active subscriptions — using default SIM fallback")
            listOf(-1)
        } else {
            subs.map { it.subscriptionId }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun newTelephonyCallback(subId: Int) =
        object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                // API 31+: number NOT included in callback (privacy change).
                // We query the call log after detecting IDLE (with a small delay).
                onStateChange(state, phoneNumber = null, subId = subId)
            }
        }

    @Suppress("DEPRECATION")
    private fun newPhoneStateListener(subId: Int) =
        object : PhoneStateListener() {
            @Deprecated("Deprecated in API 31")
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                onStateChange(state, phoneNumber, subId)
            }
        }

    // ── State machine ─────────────────────────────────────────────────────────

    private fun onStateChange(state: Int, phoneNumber: String?, subId: Int) {
        val s = callStates.getOrPut(subId) { PerSubState() }
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                s.isRinging    = true
                s.isOffHook    = false
                s.callerNumber = phoneNumber
                Log.d(TAG, "RINGING subId=$subId")
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                s.isOffHook = true
                Log.d(TAG, "OFFHOOK (answered) subId=$subId")
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (s.isRinging && !s.isOffHook) {
                    val numberSnapshot = s.callerNumber
                    Log.i(TAG, "MISSED CALL detected subId=$subId hasNumber=${numberSnapshot != null}")
                    serviceScope.launch { processMissedCall(numberSnapshot, subId) }
                }
                callStates[subId] = PerSubState()   // reset for next call
            }
        }
    }

    // ── Core missed-call processing ───────────────────────────────────────────

    private suspend fun processMissedCall(directNumber: String?, subId: Int) {
        val callerNumber = directNumber ?: queryCallLogForMissedNumber()

        if (callerNumber.isNullOrBlank()) {
            Log.w(TAG, "Could not determine caller number — skipping auto-reply")
            return
        }

        val settings = database.settingsDao().getSettings() ?: run {
            Log.e(TAG, "Settings not found in DB — skipping"); return
        }

        // Gate 1: master toggle
        if (!settings.autoReplyEnabled) {
            Log.d(TAG, "Auto-reply OFF — skipping"); return
        }

        // Gate 2: quiet hours
        if (isWithinQuietHours(settings)) {
            Log.d(TAG, "Quiet hours active — suppressing")
            writeLogEntry(callerNumber, subId, CallStatus.SUPPRESSED_QUIET_HOURS)
            return
        }

        // Gate 3: per-number 10-minute cooldown
        val hash = sha256(callerNumber)
        if (isInCooldown(hash)) {
            Log.d(TAG, "Cooldown active — suppressing duplicate")
            writeLogEntry(callerNumber, subId, CallStatus.SUPPRESSED_COOLDOWN)
            return
        }

        // Get active template
        val template = database.templateDao().getDefaultTemplate() ?: run {
            Log.e(TAG, "No default template — cannot send SMS"); return
        }

        // Write PENDING entry
        val logId = database.callLogDao().insertEntry(
            CallLogEntry(
                callerNumberMasked = maskNumber(callerNumber),
                callerNumberHash   = hash,
                timestamp          = System.currentTimeMillis(),
                simSlot            = subId,
                status             = CallStatus.PENDING
            )
        )

        // Dispatch SMS worker
        val request = SmsSenderWorker.buildRequest(callerNumber, subId, template.text, logId)
        WorkManager.getInstance(applicationContext).enqueue(request)
        Log.i(TAG, "SmsSenderWorker enqueued logId=$logId subId=$subId")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Used on API 31+ where TelephonyCallback doesn't provide the caller number directly. */
    private suspend fun queryCallLogForMissedNumber(): String? {
        delay(CALL_LOG_QUERY_DELAY_MS)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "READ_CALL_LOG not granted — cannot fetch caller number"); return null
        }
        return try {
            contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER),
                "${CallLog.Calls.TYPE} = ${CallLog.Calls.MISSED_TYPE}",
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException querying call log", e); null
        }
    }

    private fun isWithinQuietHours(s: AppSettings): Boolean {
        if (s.quietHoursStart == s.quietHoursEnd) return false
        val cal = Calendar.getInstance()
        val now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return if (s.quietHoursStart < s.quietHoursEnd)
            now in s.quietHoursStart until s.quietHoursEnd       // same-day range
        else
            now >= s.quietHoursStart || now < s.quietHoursEnd    // overnight range
    }

    private suspend fun isInCooldown(hash: String): Boolean {
        val since = System.currentTimeMillis() - COOLDOWN_MS
        return database.callLogDao().getRecentByNumberHash(hash, since).isNotEmpty()
    }

    private suspend fun writeLogEntry(number: String, subId: Int, status: CallStatus) {
        database.callLogDao().insertEntry(
            CallLogEntry(
                callerNumberMasked = maskNumber(number),
                callerNumberHash   = sha256(number),
                timestamp          = System.currentTimeMillis(),
                simSlot            = subId,
                status             = status
            )
        )
    }

    private fun maskNumber(n: String): String {
        val clean = n.filter { it.isDigit() || it == '+' }
        return when {
            clean.length >= 8 -> "${clean.take(3)}•••${clean.takeLast(2)}"
            clean.length >= 5 -> "${clean.take(2)}•••${clean.takeLast(2)}"
            else              -> "•••••"
        }
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun telephonyManager(subId: Int): TelephonyManager {
        val base = getSystemService(TelephonyManager::class.java)
        return if (subId == -1) base else base.createForSubscriptionId(subId)
    }

    private fun unregisterListeners() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallbacks.forEach { (subId, cb) ->
                try { telephonyManager(subId).unregisterTelephonyCallback(cb) }
                catch (e: Exception) { Log.w(TAG, "Unregister error subId=$subId", e) }
            }
            telephonyCallbacks.clear()
        } else {
            @Suppress("DEPRECATION")
            phoneStateListeners.forEach { (subId, l) ->
                try {
                    @Suppress("DEPRECATION")
                    telephonyManager(subId).listen(l, PhoneStateListener.LISTEN_NONE)
                } catch (e: Exception) { Log.w(TAG, "Unregister error subId=$subId", e) }
            }
            phoneStateListeners.clear()
        }
    }
}
