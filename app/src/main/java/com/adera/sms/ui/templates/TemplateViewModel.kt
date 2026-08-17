package com.adera.sms.ui.templates

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adera.sms.AderaSmsApplication
import com.adera.sms.data.AppDatabase
import com.adera.sms.data.entity.MessageTemplate
import com.google.firebase.inappmessaging.FirebaseInAppMessaging
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TemplateViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)

    val templates: StateFlow<List<MessageTemplate>> = db.templateDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setDefault(templateId: Int) {
        viewModelScope.launch { db.templateDao().setDefault(templateId) }
    }

    /**
     * Saves a new custom template, enforcing the Remote Config free-tier limit.
     *
     * LIMIT ENFORCEMENT (Items 5 & 7):
     *   The max number of custom (non-preset) templates on the free tier is controlled
     *   by Remote Config key [AderaSmsApplication.RC_KEY_FREE_TEMPLATE_LIMIT] (default 6).
     *   If the current count already meets or exceeds the limit, the save is rejected
     *   and the In-App Messaging event "template_limit_hit" is triggered so the
     *   configured Pro upgrade prompt displays.
     *
     * @return true if the template was saved, false if the limit was hit.
     */
    suspend fun saveCustomTemplate(text: String, language: String): Boolean {
        val freeLimit = FirebaseRemoteConfig.getInstance()
            .getLong(AderaSmsApplication.RC_KEY_FREE_TEMPLATE_LIMIT).toInt()

        // Count only user-created (non-preset) templates against the free limit.
        val currentCustomCount = db.templateDao().getAllTemplates()
            .count { !it.isPreset }

        if (currentCustomCount >= freeLimit) {
            // Trigger In-App Messaging contextual Pro upgrade prompt (Item 5).
            // The campaign "template_limit_hit" is configured in the Firebase console;
            // it will display a dismissible message informing the user that Pro removes
            // this limit. No blocking dialog is shown from app code.
            FirebaseInAppMessaging.getInstance().triggerEvent("template_limit_hit")
            return false
        }

        db.templateDao().insertTemplate(
            MessageTemplate(text = text, language = language,
                isDefault = false, isPreset = false)
        )
        com.adera.sms.analytics.AnalyticsManager.templateEdited(getApplication())
        return true
    }

    fun deleteCustomTemplate(id: Int) {
        viewModelScope.launch { db.templateDao().deleteUserTemplate(id) }
    }

    fun updateCustomTemplate(template: MessageTemplate) {
        viewModelScope.launch { db.templateDao().updateTemplate(template) }
    }
}
