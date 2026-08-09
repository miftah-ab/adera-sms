package com.adera.sms.data.dao

import androidx.room.*
import com.adera.sms.data.entity.CallLogEntry
import com.adera.sms.data.entity.CallStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface CallLogDao {

    // ── Observers (for Compose UI) ────────────────────────────────────────────

    /** Emits the full activity log in reverse-chronological order. */
    @Query("SELECT * FROM call_log_entries ORDER BY timestamp DESC")
    fun observeAllEntries(): Flow<List<CallLogEntry>>

    @Query("SELECT * FROM call_log_entries ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<CallLogEntry>>

    // ── One-shot reads ────────────────────────────────────────────────────────

    @Query("SELECT * FROM call_log_entries ORDER BY timestamp DESC")
    suspend fun getAllEntries(): List<CallLogEntry>

    /**
     * Used for per-number cooldown check (spec §12.7).
     * Queries by [callerNumberHash] — the SHA-256 of the full number — so two different
     * numbers that happen to produce the same masked display string are never confused.
     *
     * Returns any SENT or PENDING entry for this number after [since] timestamp.
     * If the list is non-empty, the cooldown is still active.
     */
    @Query("""
        SELECT * FROM call_log_entries
        WHERE callerNumberHash = :numberHash
          AND timestamp > :since
          AND status IN ('SENT', 'PENDING')
        LIMIT 1
    """)
    suspend fun getRecentByNumberHash(numberHash: String, since: Long): List<CallLogEntry>

    @Query("SELECT COUNT(*) FROM call_log_entries WHERE timestamp > :since AND status = 'SENT'")
    suspend fun countSentSince(since: Long): Int

    @Query("SELECT timestamp FROM call_log_entries WHERE timestamp > :since AND status = 'SENT' ORDER BY timestamp ASC LIMIT 1")
    suspend fun getOldestSentSince(since: Long): Long?

    // ── Writes ────────────────────────────────────────────────────────────────

    @Insert
    suspend fun insertEntry(entry: CallLogEntry): Long

    @Query("UPDATE call_log_entries SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: CallStatus)

    /** Housekeeping — delete entries older than [before] epoch ms. Not called in v1 UI yet. */
    @Query("DELETE FROM call_log_entries WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}
