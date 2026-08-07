package com.adera.sms.analytics

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Mandatory analytics wrapper using Firebase Analytics.
 *
 * Analytics is always active — no user-facing opt-in or opt-out exists anywhere in the app.
 * PRIVACY BOUNDARY: Never log phone numbers, message content, or any call data through
 * this class. Events track only behavioral signals (toggle state, completion, etc.).
 *
 * Tracked events (per product spec):
 *   - app_open
 *   - onboarding_complete
 *   - autoreply_sent
 *   - template_edited
 *   - toggle_changed (param: enabled Boolean)
 */
object AnalyticsManager {

    private const val TAG = "AderaSMS_Analytics"

    // ── Event name constants ──────────────────────────────────────────────────

    const val EVENT_APP_OPEN           = "app_open"
    const val EVENT_ONBOARDING_COMPLETE = "onboarding_complete"
    const val EVENT_AUTOREPLY_SENT     = "autoreply_sent"
    const val EVENT_TEMPLATE_EDITED    = "template_edited"
    const val EVENT_TOGGLE_CHANGED     = "toggle_changed"

    // ── Core logging ──────────────────────────────────────────────────────────

    fun logEvent(context: Context, eventName: String, params: Bundle? = null) {
        try {
            FirebaseAnalytics.getInstance(context).logEvent(eventName, params)
            Log.d(TAG, "Analytics event: $eventName")
        } catch (e: Exception) {
            Log.w(TAG, "Analytics logEvent failed for $eventName: ${e.message}")
        }
    }

    // ── Convenience methods ───────────────────────────────────────────────────

    fun appOpen(context: Context) = logEvent(context, EVENT_APP_OPEN)

    fun onboardingComplete(context: Context) = logEvent(context, EVENT_ONBOARDING_COMPLETE)

    fun autoReplySent(context: Context) = logEvent(context, EVENT_AUTOREPLY_SENT)

    fun templateEdited(context: Context) = logEvent(context, EVENT_TEMPLATE_EDITED)

    fun toggleChanged(context: Context, enabled: Boolean) {
        val bundle = Bundle().apply { putBoolean("enabled", enabled) }
        logEvent(context, EVENT_TOGGLE_CHANGED, bundle)
    }
}
