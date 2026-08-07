package com.adera.sms.ui.home

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adera.sms.data.AppDatabase
import com.adera.sms.data.entity.AppSettings
import com.adera.sms.data.entity.MessageTemplate
import com.adera.sms.service.CallMonitorService
import com.adera.sms.data.entity.CallLogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PermissionStatus(
    val hasPhoneState: Boolean,
    val hasCallLog: Boolean,
    val hasSendSms: Boolean,
    val hasBatteryExemption: Boolean
) {
    val allCoreGranted get() = hasPhoneState && hasCallLog && hasSendSms
    val allGranted     get() = allCoreGranted && hasBatteryExemption
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)

    val settings: StateFlow<AppSettings?> = db.settingsDao().observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val defaultTemplate: StateFlow<MessageTemplate?> = db.templateDao().observeDefault()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recentLogs: StateFlow<List<CallLogEntry>> = db.callLogDao().observeRecent(3)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _permissionStatus = MutableStateFlow(checkPermissions())
    val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus

    /** Called from onResume to pick up permission changes while app was backgrounded. */
    fun refreshPermissions() {
        _permissionStatus.value = checkPermissions()
    }

    fun toggleAutoReply(enabled: Boolean) {
        viewModelScope.launch {
            db.settingsDao().setAutoReplyEnabled(enabled)
            val ctx = getApplication<Application>()
            com.adera.sms.analytics.AnalyticsManager.toggleChanged(ctx, enabled)
            if (enabled) CallMonitorService.start(ctx)
            else         CallMonitorService.stop(ctx)
        }
    }

    private fun checkPermissions(): PermissionStatus {
        val ctx = getApplication<Application>()
        val pm  = getApplication<Application>().getSystemService(PowerManager::class.java)
        return PermissionStatus(
            hasPhoneState      = has(ctx, Manifest.permission.READ_PHONE_STATE),
            hasCallLog         = has(ctx, Manifest.permission.READ_CALL_LOG),
            hasSendSms         = has(ctx, Manifest.permission.SEND_SMS),
            hasBatteryExemption = pm.isIgnoringBatteryOptimizations(ctx.packageName)
        )
    }

    private fun has(ctx: android.content.Context, perm: String) =
        ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
}
