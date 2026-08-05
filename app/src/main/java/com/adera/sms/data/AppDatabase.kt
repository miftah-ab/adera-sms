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
    version = 2,
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
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { INSTANCE = it }
            }

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 1. Add consent fields to app_settings. v1 users haven't consented yet.
                database.execSQL("ALTER TABLE app_settings ADD COLUMN consentGiven INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN consentTimestamp INTEGER NOT NULL DEFAULT 0")
                
                // 2. Force autoReplyEnabled to 0 for users who haven't consented yet
                database.execSQL("UPDATE app_settings SET autoReplyEnabled = 0 WHERE consentGiven = 0")

                // 3. Rename callerNumberMasked to callerNumber in call_log_entries
                // Since SQLite ALTER TABLE RENAME COLUMN is only available on newer versions, 
                // we recreate the table and copy data (retaining the masked format for historical logs).
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `call_log_entries_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `callerNumber` TEXT NOT NULL, 
                        `callerNumberHash` TEXT NOT NULL, 
                        `timestamp` INTEGER NOT NULL, 
                        `simSlot` INTEGER NOT NULL, 
                        `status` TEXT NOT NULL
                    )
                """.trimIndent())
                
                database.execSQL("""
                    INSERT INTO call_log_entries_new (id, callerNumber, callerNumberHash, timestamp, simSlot, status)
                    SELECT id, callerNumberMasked, callerNumberHash, timestamp, simSlot, status FROM call_log_entries
                """.trimIndent())
                
                database.execSQL("DROP TABLE call_log_entries")
                database.execSQL("ALTER TABLE call_log_entries_new RENAME TO call_log_entries")
            }
        }
    }
}
