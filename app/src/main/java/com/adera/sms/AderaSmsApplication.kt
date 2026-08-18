package com.adera.sms

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import com.adera.sms.data.AppDatabase
import com.adera.sms.data.entity.AppSettings
import com.adera.sms.data.entity.MessageTemplate
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application subclass — registered in AndroidManifest as android:name=".AderaSmsApplication".
 *
 * Responsibilities:
 *   1. Create the foreground-service notification channel (must run before service starts)
 *   2. Seed the Room database with preset templates and default settings on first launch
 *
 * The [applicationScope] coroutine scope survives the entire app lifetime and is used
 * for the database seed operation. It uses [SupervisorJob] so a failure in seeding
 * doesn't cancel other coroutines on the scope.
 */
class AderaSmsApplication : Application() {

    /** App-wide coroutine scope — tied to process lifetime, not any Activity. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        seedDatabaseIfNeeded()
        initRemoteConfig()
    }

    // ── Notification channel ──────────────────────────────────────────────────

    private fun createNotificationChannels() {
        // Foreground service channel — IMPORTANCE_LOW so it shows no sound or heads-up alert.
        // This channel must be created before CallMonitorService calls startForeground().
        val channel = NotificationChannel(
            CHANNEL_ID_SERVICE,
            getString(R.string.notification_channel_service_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_service_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        Log.d(TAG, "Notification channel created: $CHANNEL_ID_SERVICE")
    }

    // ── Database seed ─────────────────────────────────────────────────────────

    /**
     * Seeds the database with:
     *   - 10 preset templates (5 English + 5 Amharic)
     *   - Default [AppSettings] row (id=1, everything off/false)
     *
     * Idempotent: if templates already exist (e.g. app reinstall with data preserved),
     * the seed is skipped.
     */
    private fun seedDatabaseIfNeeded() {
        applicationScope.launch {
            try {
                val db = AppDatabase.getInstance(applicationContext)

                // Seed templates
                if (db.templateDao().getAllTemplates().isEmpty()) {
                    val presets = buildPresetTemplates()
                    db.templateDao().insertAll(presets)
                    Log.i(TAG, "Database seeded with ${presets.size} preset templates")
                }

                // Ensure settings row exists
                if (db.settingsDao().getSettings() == null) {
                    db.settingsDao().upsertSettings(AppSettings())
                    Log.i(TAG, "Default settings row created")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Database seed failed", e)
            }
        }
    }

    /**
     * Builds the 10 preset templates shipped with the app.
     * English + Amharic only (spec §12.8).
     *
     * ⚠ REVIEW NOTE: Amharic strings should be verified by a native speaker before
     * any public/TikTok release. Transliterations are in values-am/strings.xml.
     */
    private fun buildPresetTemplates() = listOf(
        // ── English (3) ──────────────────────────────────────────────────────
        MessageTemplate(text = "Sorry I missed your call. I'll call you back soon.",
            language = "en", isDefault = true,  isPreset = true),
        MessageTemplate(text = "I'm in a meeting. I'll call you back shortly.",
            language = "en", isDefault = false, isPreset = true),
        MessageTemplate(text = "I'm driving right now. I'll call you when I arrive.",
            language = "en", isDefault = false, isPreset = true),
        // ── Amharic (3) ──────────────────────────────────────────────────────
        MessageTemplate(text = "ይቅርታ ጥሪዎን አምልጦኛል። ብዙም ሳይቆይ እደውሎልዎታለሁ።",
            language = "am", isDefault = false, isPreset = true),
        MessageTemplate(text = "ስብሰባ ላይ ነኝ። ብዙም ሳይቆይ እደውሎልዎታለሁ።",
            language = "am", isDefault = false, isPreset = true),
        MessageTemplate(text = "እየነዳሁ ነው። ሲደርስ እደውሎልዎታለሁ።",
            language = "am", isDefault = false, isPreset = true)
    )

    // ── Firebase Remote Config (Items 7 & 8) ─────────────────────────────────

    /**
     * Initialises Remote Config with safe in-app defaults and fetches fresh values.
     *
     * KEY CONSTANTS (see [RC_KEY_DAILY_SEND_CAP] / [RC_KEY_FREE_TEMPLATE_LIMIT]):
     *   daily_send_cap       — max SMS replies per 24-hour window (default: 15)
     *   free_template_limit  — max custom templates on the free tier (default: 6)
     *
     * If Remote Config cannot be reached (offline, quota, etc.) the default values
     * declared here are used — behaviour is identical to the previous hardcoded values.
     *
     * FETCH INTERVAL:
     *   Production: 3600 s (1 hour) — standard Firebase recommendation.
     *   The fetch is fire-and-forget; the activated values take effect on the NEXT
     *   call to FirebaseRemoteConfig.getInstance().getLong(key). CallMonitorService and
     *   TemplateViewModel read the value at the point of use, not at init time, so
     *   fresh values are automatically picked up without an app restart.
     *
     * A/B TESTING (Item 8):
     *   A/B experiments are configured entirely from the Firebase console against these
     *   same Remote Config keys (e.g. test different Pro upsell wording by varying
     *   free_template_limit between experiment variants). No additional app code is
     *   required — A/B Testing is built into Remote Config.
     */
    private fun initRemoteConfig() {
        val rc = FirebaseRemoteConfig.getInstance()

        // Developer mode: allow frequent fetches during development.
        // Remove or set minimumFetchIntervalInSeconds = 3600 before production release.
        val settings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600L
        }
        rc.setConfigSettingsAsync(settings)

        // Defaults match the previously hardcoded values — no behaviour change if
        // Remote Config cannot be fetched.
        rc.setDefaultsAsync(
            mapOf(
                RC_KEY_DAILY_SEND_CAP      to 15L,
                RC_KEY_FREE_TEMPLATE_LIMIT to 6L
            )
        )

        // Fetch and activate in the background — failures are silent and safe.
        rc.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.i(TAG, "Remote Config fetch+activate succeeded. " +
                    "daily_send_cap=${rc.getLong(RC_KEY_DAILY_SEND_CAP)} " +
                    "free_template_limit=${rc.getLong(RC_KEY_FREE_TEMPLATE_LIMIT)}")
            } else {
                Log.w(TAG, "Remote Config fetch failed — using defaults", task.exception)
            }
        }
    }

    companion object {
        private const val TAG = "AderaSMS"
        const val CHANNEL_ID_SERVICE      = "adera_service_channel"
        const val NOTIFICATION_ID_SERVICE = 1001

        // Default fallback values guaranteed if Remote Config returns 0 or fails to fetch
        const val DEFAULT_DAILY_SEND_CAP      = 15
        const val DEFAULT_FREE_TEMPLATE_LIMIT = 6

        // Remote Config keys — shared constants so CallMonitorService and
        // TemplateViewModel use the exact same key strings.
        const val RC_KEY_DAILY_SEND_CAP      = "daily_send_cap"
        const val RC_KEY_FREE_TEMPLATE_LIMIT = "free_template_limit"
    }
}
