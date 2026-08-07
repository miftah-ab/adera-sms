package com.adera.sms.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.adera.sms.data.AppDatabase
import com.adera.sms.service.CallMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Restarts [CallMonitorService] after device reboot if auto-reply was enabled (spec §12.7).
 *
 * Listens for both BOOT_COMPLETED and LOCKED_BOOT_COMPLETED:
 * - BOOT_COMPLETED: fires after the user unlocks the device for the first time post-reboot.
 *   Room database is always available here (credential-encrypted storage is unlocked).
 * - LOCKED_BOOT_COMPLETED: fires immediately after kernel boot, before first unlock (API 24+).
 *   Room database may NOT be available yet (device-encrypted storage only).
 *   Strategy: attempt DB read; if it fails, start the service defensively — it will
 *   read settings correctly when it first processes a missed call.
 *
 * [goAsync] is used so we can safely run a coroutine inside the receiver's
 * 10-second execution window without blocking the main thread.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AderaSMS"
        // Not exposed as Intent.ACTION_LOCKED_BOOT_COMPLETED on older SDK targets
        private const val ACTION_LOCKED_BOOT = "android.intent.action.LOCKED_BOOT_COMPLETED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != ACTION_LOCKED_BOOT) return

        Log.i(TAG, "BootReceiver: $action received")

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db       = AppDatabase.getInstance(context.applicationContext)
                val settings = db.settingsDao().getSettings()

                if (settings?.autoReplyEnabled == true) {
                    Log.i(TAG, "BootReceiver: auto-reply was enabled → starting service")
                    CallMonitorService.start(context.applicationContext)
                } else {
                    Log.d(TAG, "BootReceiver: auto-reply was disabled → not starting service")
                }
            } catch (e: Exception) {
                // DB unavailable on LOCKED_BOOT (before first unlock) — start defensively
                Log.w(TAG, "BootReceiver: DB read failed ($action) → starting service defensively", e)
                CallMonitorService.start(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
