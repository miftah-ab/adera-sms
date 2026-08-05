package com.adera.sms.ui.settings

import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adera.sms.data.AppDatabase
import com.adera.sms.data.entity.AppSettings
import com.adera.sms.update.UpdateChecker
import com.adera.sms.update.UpdateStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)

    val settings: StateFlow<AppSettings?> = db.settingsDao().observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _updateStatus = MutableStateFlow<UpdateStatus?>(null)
    val updateStatus: StateFlow<UpdateStatus?> = _updateStatus

    private val _batteryIgnored = MutableStateFlow(false)
    val batteryIgnored: StateFlow<Boolean> = _batteryIgnored

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate

    fun refreshBatteryStatus() {
        val pm = getApplication<Application>().getSystemService(PowerManager::class.java)
        _batteryIgnored.value = pm.isIgnoringBatteryOptimizations(
            getApplication<Application>().packageName
        )
    }

    fun setQuietHours(start: Int, end: Int) {
        viewModelScope.launch { db.settingsDao().setQuietHours(start, end) }
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            val result = withContext(Dispatchers.IO) {
                UpdateChecker.check(getApplication())
            }
            _updateStatus.value = result
            _isCheckingUpdate.value = false
            db.settingsDao().setLastUpdateCheck(System.currentTimeMillis())
        }
    }

    fun setAnalyticsOptIn(optIn: Boolean) {
        viewModelScope.launch { db.settingsDao().setAnalyticsOptIn(optIn) }
    }

    fun clearAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            db.clearAllTables()
            // Setting up initial DB state if needed can be done here or on restart
        }
    }
}
