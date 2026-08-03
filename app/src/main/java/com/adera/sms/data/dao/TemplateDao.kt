package com.adera.sms.data.dao

import androidx.room.*
import com.adera.sms.data.entity.MessageTemplate
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {

    // ── Observers (for Compose UI) ────────────────────────────────────────────

    /** Emits the active default template whenever it changes. Collected by HomeViewModel. */
    @Query("SELECT * FROM message_templates WHERE isDefault = 1 LIMIT 1")
    fun observeDefault(): Flow<MessageTemplate?>

    /** Emits the full template list whenever any template changes. Used by TemplateEditorScreen. */
    @Query("SELECT * FROM message_templates ORDER BY language ASC, isPreset DESC, id ASC")
    fun observeAll(): Flow<List<MessageTemplate>>

    // ── One-shot reads (for service layer) ───────────────────────────────────

    @Query("SELECT * FROM message_templates WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultTemplate(): MessageTemplate?

    @Query("SELECT * FROM message_templates ORDER BY language ASC, isPreset DESC, id ASC")
    suspend fun getAllTemplates(): List<MessageTemplate>

    // ── Writes ────────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: MessageTemplate): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(templates: List<MessageTemplate>)

    /**
     * Sets [templateId] as the new default. Uses a transaction to guarantee
     * exactly one row has isDefault=true at all times.
     */
    @Transaction
    suspend fun setDefault(templateId: Int) {
        clearDefault()
        markAsDefault(templateId)
    }

    @Query("UPDATE message_templates SET isDefault = 0")
    suspend fun clearDefault()

    @Query("UPDATE message_templates SET isDefault = 1 WHERE id = :templateId")
    suspend fun markAsDefault(templateId: Int)

    @Update
    suspend fun updateTemplate(template: MessageTemplate)

    /** User-created templates only (isPreset = false). Preset templates cannot be deleted. */
    @Query("DELETE FROM message_templates WHERE id = :id AND isPreset = 0")
    suspend fun deleteUserTemplate(id: Int)
}
