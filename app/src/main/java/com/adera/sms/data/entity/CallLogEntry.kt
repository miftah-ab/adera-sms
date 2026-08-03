package com.adera.sms.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single missed-call auto-reply event, written to the local database after each attempt.
 *
 * PRIVACY CONTRACT (spec §12.2):
 * - [callerNumberMasked]  → shown in the UI, e.g. "091•••42". Cannot be reversed.
 * - [callerNumberHash]    → SHA-256 of the full number. Used ONLY for per-number cooldown
 *                           matching. Never displayed. Cannot be reversed.
 * - The full phone number exists only transiently in memory inside [CallMonitorService] and
 *   [SmsSenderWorker] and is never written to disk in plain form.
 *
 * NOTE: This table has no incoming-SMS data. The app listens only for missed voice calls.
 * Adding SMS-triggered rows would risk a reply loop between two Adera SMS users — do not do it.
 */
@Entity(tableName = "call_log_entries")
data class CallLogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    /** Display-safe masked number, e.g. "091•••42". */
    val callerNumberMasked: String,

    /** SHA-256 hex of the full E.164 number. Used for cooldown lookups only. */
    val callerNumberHash: String,

    /** Unix epoch milliseconds when the missed call was detected. */
    val timestamp: Long,

    /**
     * Subscription ID of the SIM that received the call (-1 = unknown/single SIM fallback).
     * Not a slot index (0/1) — this is the integer subscription ID from TelephonyManager.
     */
    val simSlot: Int,

    val status: CallStatus
)

/**
 * All possible outcomes for an auto-reply attempt.
 *
 * PENDING   → worker enqueued, result not yet known.
 * SENT      → SmsManager accepted the message for delivery.
 * FAILED    → all retry attempts exhausted (no signal, no balance, permission revoked).
 * SUPPRESSED_QUIET_HOURS → auto-reply was intentionally skipped (within quiet window).
 * SUPPRESSED_COOLDOWN    → same caller rang again within the 10-minute cooldown window.
 */
enum class CallStatus {
    PENDING,
    SENT,
    FAILED,
    SUPPRESSED_QUIET_HOURS,
    SUPPRESSED_COOLDOWN
}
