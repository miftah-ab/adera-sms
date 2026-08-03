package com.adera.sms.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.adera.sms.data.dao.CallLogDao
import com.adera.sms.data.dao.SettingsDao
import com.adera.sms.data.dao.TemplateDao
import com.adera.sms.data.entity.AppSettings
import com.adera.sms.data.entity.CallLogEntry
import com.adera.sms.data.entity.MessageTemplate

/**
 * Adera SMS local database. 100% on-device — nothing is synced to any server.
 *
 * Schema version history:
 *   v1 — Initial schema: message_templates, call_log_entries, app_settings
 *
 * Migration strategy: [fallbackToDestructiveMigration] is acceptable for v1 since
 * the database contains no irreplaceable user data (settings are simple, log is local-only).
 * Before bumping [version] in a future release, add a proper Migration object instead.
 *
 * Thread safety: [getInstance] is double-check-locked. All DAO methods are suspend/Flow;
 * Room dispatches them on its own internal executor automatically.
 */
@Database(
    entities = [MessageTemplate::class, CallLogEntry::class, AppSettings::class],
    version = 1,
    exportSchema = true   // Schema JSON written to app/schemas/ for version history
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun templateDao(): TemplateDao
    abstract fun callLogDao(): CallLogDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        private const val DB_NAME = "adera_sms.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
    }
}
