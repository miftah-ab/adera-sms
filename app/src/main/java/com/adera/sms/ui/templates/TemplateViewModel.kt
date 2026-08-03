package com.adera.sms.ui.templates

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adera.sms.data.AppDatabase
import com.adera.sms.data.entity.MessageTemplate
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

    fun saveCustomTemplate(text: String, language: String) {
        viewModelScope.launch {
            db.templateDao().insertTemplate(
                MessageTemplate(text = text, language = language,
                    isDefault = false, isPreset = false)
            )
        }
    }

    fun deleteCustomTemplate(id: Int) {
        viewModelScope.launch { db.templateDao().deleteUserTemplate(id) }
    }

    fun updateCustomTemplate(template: MessageTemplate) {
        viewModelScope.launch { db.templateDao().updateTemplate(template) }
    }
}
