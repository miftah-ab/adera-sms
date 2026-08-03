package com.adera.sms.analytics

import android.util.Log

/**
 * Analytics wrapper (spec §12.5, Step 11 — opt-in only).
 *
 * This is a STUB for v1. It logs events to Logcat only.
 *
 * When you're ready to add Firebase Analytics:
 *   1. Create a Firebase project and download google-services.json → app/
 *   2. Add `com.google.gms:google-services` and `firebase-analytics-ktx` to the build
 *   3. Replace the Log calls below with FirebaseAnalytics.getInstance(context).logEvent(...)
 *   4. The opt-in gate (analyticsOptIn check) must stay — never call Firebase without consent.
 *
 * Events to track (per spec §10 success metrics):
 *   - onboarding_completed
 *   - auto_reply_toggled (enabled: Boolean)
 *   - sms_sent
 *   - sms_failed
 *   - template_changed
 */
object AnalyticsManager {

    private const val TAG = "AderaSMS_Analytics"

    /**
     * Log an analytics event.
     * [optIn] MUST be true (from AppSettings.analyticsOptIn) before this is called.
     * Call sites are responsible for checking opt-in — this method does NOT enforce it
     * as a second gate because that would hide bugs where opt-in is not being checked.
     */
    fun logEvent(event: String, params: Map<String, Any> = emptyMap()) {
        // Stub: emit to Logcat only in v1
        Log.d(TAG, "Event: $event | params: $params")
        // TODO (Step 11 full implementation): FirebaseAnalytics.getInstance(ctx).logEvent(event, bundle)
    }

    // ── Convenience methods ───────────────────────────────────────────────────

    fun onboardingCompleted() = logEvent("onboarding_completed")

    fun autoReplyToggled(enabled: Boolean) =
        logEvent("auto_reply_toggled", mapOf("enabled" to enabled))

    fun smsSent() = logEvent("sms_sent")

    fun smsFailed(reason: String) = logEvent("sms_failed", mapOf("reason" to reason))

    fun templateChanged(language: String, isPreset: Boolean) =
        logEvent("template_changed", mapOf("language" to language, "is_preset" to isPreset))
}
