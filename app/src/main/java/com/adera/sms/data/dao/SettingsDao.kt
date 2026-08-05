package com.adera.sms.data.dao

import androidx.room.*
import com.adera.sms.data.entity.AppSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {

    // ── Observer ──────────────────────────────────────────────────────────────

    /** Live settings stream — collected by ViewModels to drive UI state. */
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun observeSettings(): Flow<AppSettings?>

    // ── One-shot reads ────────────────────────────────────────────────────────

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun getSettings(): AppSettings?

    // ── Writes ────────────────────────────────────────────────────────────────

    /** Insert or replace the single settings row (id is always 1). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: AppSettings)

    @Query("UPDATE app_settings SET autoReplyEnabled = :enabled WHERE id = 1")
    suspend fun setAutoReplyEnabled(enabled: Boolean)

    @Query("UPDATE app_settings SET quietHoursStart = :start, quietHoursEnd = :end WHERE id = 1")
    suspend fun setQuietHours(start: Int, end: Int)

    @Query("UPDATE app_settings SET consentGiven = 1, consentTimestamp = :timestamp WHERE id = 1")
    suspend fun markConsentGiven(timestamp: Long)

    @Query("UPDATE app_settings SET lastUpdateCheck = :timestamp WHERE id = 1")
    suspend fun setLastUpdateCheck(timestamp: Long)

    @Query("UPDATE app_settings SET analyticsOptIn = :optIn WHERE id = 1")
    suspend fun setAnalyticsOptIn(optIn: Boolean)
}
