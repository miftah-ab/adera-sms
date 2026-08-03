package com.adera.sms.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A saved SMS reply template — either a preset shipped with the app or a user-created one.
 *
 * Invariant: exactly ONE template has [isDefault] = true at any time.
 * This is enforced transactionally by [TemplateDao.setDefault].
 *
 * [language] is ISO 639-1. Only "en" and "am" are valid values in v1 (spec §12.8).
 * Adding other languages later is a pure translation task — no schema change needed.
 *
 * [isPreset] marks factory-seeded templates. They can be selected but not deleted.
 */
@Entity(tableName = "message_templates")
data class MessageTemplate(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    /** The actual SMS body text sent to the caller. Keep under 160 chars for single-segment SMS. */
    val text: String,

    /** ISO 639-1 language code: "en" or "am" only in v1. */
    val language: String,

    /** True for the one currently active template. Only one row should have this set. */
    val isDefault: Boolean = false,

    /** True for the 10 factory presets seeded at first launch. Cannot be deleted by user. */
    val isPreset: Boolean = true
)
