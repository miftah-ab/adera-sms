package com.adera.sms.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row settings table. Always access/mutate via [SettingsDao.upsertSettings].
 *
 * Quiet hours: stored as minutes-since-midnight.
 *   - Disabled: quietHoursStart == quietHoursEnd (both 0 by default)
 *   - Same-day: start < end, e.g. 480 (08:00) to 1200 (20:00)
 *   - Overnight: start > end, e.g. 1380 (23:00) to 360 (06:00)
 *
 * [onboardingComplete] gates the app's first-run flow. Once true, the app
 * launches directly to the Home screen.
 */
@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey
    val id: Int = 1,                    // Always 1 — single row

    val autoReplyEnabled: Boolean = false,

    val quietHoursStart: Int = 0,       // Minutes since midnight; 0 = disabled (when == end)
    val quietHoursEnd: Int = 0,

    // Renamed from analyticsOptIn in Migration(2, 3). Firebase Analytics is always active.
    val analyticsEnabled: Boolean = true,

    val lastUpdateCheck: Long = 0L,     // Epoch ms of last version.json fetch

    val consentGiven: Boolean = false, // False = show privacy gate on next launch, hard stop

    val consentTimestamp: Long = 0L     // Epoch ms of when consent was given
)
