package com.adera.sms.ui.activitylog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adera.sms.data.AppDatabase
import com.adera.sms.data.entity.CallLogEntry
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ActivityLogViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)

    private val _allEntries: StateFlow<List<CallLogEntry>> = db.callLogDao().observeAllEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _searchQuery = MutableStateFlow("")

    /** Exposed immutably — UI must call [onSearchQueryChanged] to update. */
    val searchQuery: StateFlow<String> = _searchQuery

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    // Filtered list: when query is empty, returns all entries; otherwise filters by callerNumber
    val entries: StateFlow<List<CallLogEntry>> = combine(_allEntries, _searchQuery) { entries, query ->
        if (query.isBlank()) entries
        else entries.filter { it.callerNumber.contains(query.trim(), ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
