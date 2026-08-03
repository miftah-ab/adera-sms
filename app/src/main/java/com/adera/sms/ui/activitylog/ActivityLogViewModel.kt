package com.adera.sms.ui.activitylog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adera.sms.data.AppDatabase
import com.adera.sms.data.entity.CallLogEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ActivityLogViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)

    val entries: StateFlow<List<CallLogEntry>> = db.callLogDao().observeAllEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
