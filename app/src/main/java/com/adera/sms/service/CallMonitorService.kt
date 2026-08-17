package com.adera.sms.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import java.util.Calendar
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import com.adera.sms.AderaSmsApplication
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.inappmessaging.FirebaseInAppMessaging
import com.google.firebase.remoteconfig.FirebaseRemoteConfig

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

        fun buildNotification(context: Context): Notification {
            val tapIntent = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            return NotificationCompat.Builder(context, AderaSmsApplication.CHANNEL_ID_SERVICE)
                .setContentTitle(context.getString(R.string.notification_service_title))
                .setContentText(context.getString(R.string.notification_service_text))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(tapIntent)
                // setOngoing(true) intentionally NOT set — on Android 13+ the user may swipe
                // this notification away while the foreground service continues running.
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build()
        }

        fun buildForegroundInfo(context: Context): androidx.work.ForegroundInfo {
            val notification = buildNotification(context)
            // API 34+ (UPSIDE_DOWN_CAKE): foreground service type is required in the
            // ForegroundInfo constructor and must exactly match the manifest declaration.
            // On API 29–33, the 2-arg constructor is correct — passing the type on those
            // versions caused the foregroundServiceType mismatch crash (Crash 1).
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                androidx.work.ForegroundInfo(
                    AderaSmsApplication.NOTIFICATION_ID_SERVICE,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                androidx.work.ForegroundInfo(AderaSmsApplication.NOTIFICATION_ID_SERVICE, notification)
            }
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

    /**
     * Dedicated single-thread Executor for TelephonyCallback delivery (API 31+).
     *
     * WHY NOT mainExecutor:
     *   mainExecutor is a Context property getter that returns the system-managed main-thread
     *   Executor for this Context. On Android 15, Google tightened lifecycle enforcement for
     *   foreground services of type `specialUse` running while the app has no visible activity.
     *   In this state, mainExecutor-backed TelephonyCallback registrations can silently stop
     *   delivering events — no crash, no exception, no logcat output. The service remains alive
     *   (heartbeat stays fresh via serviceScope/Dispatchers.IO) but onCallStateChanged never fires.
     *
     *   Using a service-owned Executor eliminates this dependency on the system's context
     *   lifecycle handle, making callback delivery reliable on Android 15.
     */
    private val telephonyExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "adera-telephony-cb").apply { isDaemon = false }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private val telephonyCallbacks = mutableMapOf<Int, TelephonyCallback>()

    @Suppress("DEPRECATION")
    private val phoneStateListeners = mutableMapOf<Int, PhoneStateListener>()

    // ── Service lifecycle ─────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(applicationContext)
        startForegroundWithNotification()
        val subIds = activeSubscriptionIds()
        registerListeners(subIds)

        // Crashlytics custom keys (Item 9) — device context for future crash reports.
        // These are set once per service lifecycle and appear in every crash report
        // generated while the service is alive.
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setCustomKey("android_version", Build.VERSION.SDK_INT)
        crashlytics.setCustomKey("active_sim_count", subIds.size)
        crashlytics.log("CallMonitorService started: SDK=${Build.VERSION.SDK_INT} sims=${subIds.size}")

        Log.i(TAG, "CallMonitorService started")

        serviceScope.launch {
            while (isActive) {
                try {
                    database.settingsDao().updateHeartbeat(System.currentTimeMillis())
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update heartbeat", e)
                }
                delay(300_000L) // every 5 minutes
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY  // Restart automatically after OEM kill

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterListeners()
        serviceScope.cancel()
        telephonyExecutor.shutdown()
        Log.i(TAG, "CallMonitorService destroyed — will restart via START_STICKY")
        super.onDestroy()
    }

    // ── Foreground notification ───────────────────────────────────────────────

    private fun startForegroundWithNotification() {
        val notification = buildNotification(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                AderaSmsApplication.NOTIFICATION_ID_SERVICE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(AderaSmsApplication.NOTIFICATION_ID_SERVICE, notification)
        }
    }

    // ── Telephony listener registration ──────────────────────────────────────

    private fun registerListeners() {
        val subIds = activeSubscriptionIds()
        registerListeners(subIds)
    }

    private fun registerListeners(subIds: List<Int>) {
        Log.d(TAG, "Registering listeners for subscriptions: $subIds")
        subIds.forEach { subId ->
            callStates[subId] = PerSubState()
            val tm = telephonyManager(subId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val cb = newTelephonyCallback(subId)
                telephonyCallbacks[subId] = cb
                tm.registerTelephonyCallback(telephonyExecutor, cb)
            } else {
                @Suppress("DEPRECATION")
                val listener = newPhoneStateListener(subId)
                phoneStateListeners[subId] = listener
                @Suppress("DEPRECATION")
                tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            }
        }
    }

    private fun activeSubscriptionIds(): List<Int> {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_PHONE_STATE not granted — using default voice sub fallback")
            return listOf(defaultVoiceSubId())
        }
        val subs = try {
            getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList
        } catch (e: Exception) {
            Log.w(TAG, "getActiveSubscriptionInfoList failed: ${e.message}")
            null
        }
        return if (subs.isNullOrEmpty()) {
            // On Android 13+ the list can be null for non-privileged apps even with
            // READ_PHONE_STATE granted. Use the default voice subscription ID instead
            // of bare -1 (base TelephonyManager), which is unreliable on Android 15.
            Log.w(TAG, "No active subscriptions returned — using default voice sub fallback")
            listOf(defaultVoiceSubId())
        } else {
            subs.map { it.subscriptionId }
        }
    }

    /**
     * Returns the system default voice subscription ID, or -1 as a last resort.
     *
     * Registering TelephonyCallback on a TelephonyManager created with a real
     * subscription ID is more reliable than using the base TelephonyManager (subId=-1)
     * on Android 15, where bare-TelephonyManager callback delivery is inconsistent
     * on certain OEM builds.
     */
    private fun defaultVoiceSubId(): Int {
        val id = SubscriptionManager.getDefaultVoiceSubscriptionId()
        return if (id != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            Log.d(TAG, "defaultVoiceSubId: using $id")
            id
        } else {
            Log.w(TAG, "defaultVoiceSubId: INVALID — falling back to base TelephonyManager")
            -1
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun newTelephonyCallback(subId: Int) =
        object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
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
        val crashlytics = FirebaseCrashlytics.getInstance()
        val s = callStates.getOrPut(subId) { PerSubState() }
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                s.isRinging    = true
                s.isOffHook    = false
                s.callerNumber = phoneNumber
                // Breadcrumb: call state received (Item 9)
                crashlytics.log("CALL_STATE_RINGING subId=$subId hasNumber=${phoneNumber != null}")
                Log.d(TAG, "RINGING subId=$subId")
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                s.isOffHook = true
                crashlytics.log("CALL_STATE_OFFHOOK (answered) subId=$subId")
                Log.d(TAG, "OFFHOOK (answered) subId=$subId")
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (s.isRinging && !s.isOffHook) {
                    val numberSnapshot = s.callerNumber
                    crashlytics.log("CALL_STATE_IDLE: missed call detected subId=$subId hasNumber=${numberSnapshot != null}")
                    Log.i(TAG, "MISSED CALL detected subId=$subId hasNumber=${numberSnapshot != null}")
                    serviceScope.launch { processMissedCall(numberSnapshot, subId) }
                }
                callStates[subId] = PerSubState()   // reset for next call
            }
        }
    }

    // ── Core missed-call processing ───────────────────────────────────────────

    private suspend fun processMissedCall(directNumber: String?, subId: Int) {
        val crashlytics = FirebaseCrashlytics.getInstance()

        // Step 1: resolve caller number
        val callerNumber = directNumber
            ?: withTimeoutOrNull(5_000L) {
                crashlytics.log("Querying call log for missed number (TelephonyCallback path)")
                queryCallLogForMissedNumber()
            }
            ?: run {
                crashlytics.log("processMissedCall: call log not updated within 5 s — skipping")
                Log.w(TAG, "processMissedCall: call log not updated within 5 s — skipping auto-reply")
                return
            }

        crashlytics.log("Caller number resolved. Checking settings...")

        val settings = database.settingsDao().getSettings() ?: run {
            crashlytics.log("Settings not found in DB — skipping")
            Log.e(TAG, "Settings not found in DB — skipping"); return
        }

        if (!settings.autoReplyEnabled) {
            crashlytics.log("Auto-reply OFF — skipping")
            Log.d(TAG, "Auto-reply OFF — skipping"); return
        }

        // Step 2: quiet hours check
        if (isWithinQuietHours(settings)) {
            crashlytics.log("Quiet hours active — suppressing reply")
            Log.d(TAG, "Quiet hours active — suppressing")
            writeLogEntry(callerNumber, subId, CallStatus.SUPPRESSED_QUIET_HOURS)
            return
        }

        // Step 3: per-number cooldown check
        val hash = sha256(callerNumber)
        if (isInCooldown(hash)) {
            crashlytics.log("Cooldown active — suppressing duplicate reply")
            Log.d(TAG, "Cooldown active — suppressing duplicate")
            writeLogEntry(callerNumber, subId, CallStatus.SUPPRESSED_COOLDOWN)
            return
        }

        // Step 4: daily cap check — value from Remote Config (default 15)
        val dailyCap = FirebaseRemoteConfig.getInstance()
            .getLong(AderaSmsApplication.RC_KEY_DAILY_SEND_CAP).toInt()
        val since24h   = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        val sentCount  = database.callLogDao().countSentSince(since24h)
        crashlytics.log("Daily cap check: sentToday=$sentCount cap=$dailyCap")
        if (sentCount >= dailyCap) {
            crashlytics.log("Daily cap reached ($sentCount/$dailyCap) — suppressing reply")
            Log.d(TAG, "Daily limit reached — suppressing duplicate")
            writeLogEntry(callerNumber, subId, CallStatus.DAILY_LIMIT_REACHED)
            // Trigger In-App Messaging contextual campaign (Item 5)
            FirebaseInAppMessaging.getInstance().triggerEvent("daily_cap_reached")
            return
        }

        // Step 5: fetch default template
        val template = database.templateDao().getDefaultTemplate() ?: run {
            crashlytics.log("No default template found — cannot send SMS")
            Log.e(TAG, "No default template — cannot send SMS"); return
        }

        // Step 6: enqueue SMS send
        crashlytics.log("Enqueuing SmsSenderWorker for subId=$subId")
        val logId = database.callLogDao().insertEntry(
            CallLogEntry(
                callerNumber       = callerNumber,
                callerNumberHash   = hash,
                timestamp          = System.currentTimeMillis(),
                simSlot            = subId,
                status             = CallStatus.PENDING
            )
        )

        val request = SmsSenderWorker.buildRequest(callerNumber, subId, template.text, logId)
        WorkManager.getInstance(applicationContext).enqueue(request)
        crashlytics.log("SmsSenderWorker enqueued logId=$logId subId=$subId")
        Log.i(TAG, "SmsSenderWorker enqueued logId=$logId subId=$subId")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Used on API 31+ where TelephonyCallback doesn't provide the caller number directly.
     *
     * IMPORTANT — LIMIT in sortOrder:
     *   Appending "LIMIT n" directly in the sortOrder string of ContentResolver.query() throws
     *   IllegalArgumentException: Invalid token LIMIT on Android 11+ (API 30+). The fix is to
     *   use the Bundle-based query overload (API 30+) which accepts QUERY_ARG_LIMIT separately.
     *   The legacy sortOrder string with LIMIT is only used on API < 30 where it is valid.
     */
    private suspend fun queryCallLogForMissedNumber(): String? = suspendCancellableCoroutine { cont ->
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "READ_CALL_LOG not granted — cannot fetch caller number")
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        
        val resolver = contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                try {
                    resolver.unregisterContentObserver(this)
                } catch (e: Exception) {}

                try {
                    val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // API 30+: Bundle-based query — LIMIT in sortOrder is rejected by
                        // the platform on API 30+ with IllegalArgumentException.
                        val args = Bundle().apply {
                            putInt(ContentResolver.QUERY_ARG_LIMIT, 1)
                            putStringArray(
                                ContentResolver.QUERY_ARG_SORT_COLUMNS,
                                arrayOf(CallLog.Calls.DATE)
                            )
                            putInt(
                                ContentResolver.QUERY_ARG_SORT_DIRECTION,
                                ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                            )
                            putString(
                                ContentResolver.QUERY_ARG_SQL_SELECTION,
                                "${CallLog.Calls.TYPE} = ?"
                            )
                            putStringArray(
                                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                                arrayOf(CallLog.Calls.MISSED_TYPE.toString())
                            )
                        }
                        resolver.query(
                            CallLog.Calls.CONTENT_URI,
                            arrayOf(CallLog.Calls.NUMBER),
                            args,
                            null  // CancellationSignal
                        )
                    } else {
                        // API < 30: legacy sortOrder string — LIMIT is accepted here.
                        resolver.query(
                            CallLog.Calls.CONTENT_URI,
                            arrayOf(CallLog.Calls.NUMBER),
                            "${CallLog.Calls.TYPE} = ?",
                            arrayOf(CallLog.Calls.MISSED_TYPE.toString()),
                            "${CallLog.Calls.DATE} DESC LIMIT 1"
                        )
                    }
                    cursor?.use {
                        if (it.moveToFirst()) {
                            if (cont.isActive) cont.resume(it.getString(0))
                        } else {
                            if (cont.isActive) cont.resume(null)
                        }
                    } ?: run {
                        if (cont.isActive) cont.resume(null)
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException querying call log", e)
                    if (cont.isActive) cont.resume(null)
                } catch (e: Exception) {
                    // Catch-all: ensures no uncaught exception can propagate out of the
                    // ContentObserver callback and silently kill the coroutine.
                    Log.e(TAG, "Exception querying call log", e)
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
        
        resolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)
        
        cont.invokeOnCancellation {
            try {
                resolver.unregisterContentObserver(observer)
            } catch (e: Exception) {}
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
                callerNumber       = number,
                callerNumberHash   = sha256(number),
                timestamp          = System.currentTimeMillis(),
                simSlot            = subId,
                status             = status
            )
        )
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
