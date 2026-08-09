package com.adera.sms.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.adera.sms.service.ServiceStartWorker

/**
 * Restarts [com.adera.sms.service.CallMonitorService] after device reboot.
 *
 * Listens for both BOOT_COMPLETED and LOCKED_BOOT_COMPLETED:
 * - BOOT_COMPLETED: fires after the user unlocks the device for the first time post-reboot.
 *   Room database is always available here (credential-encrypted storage is unlocked).
 * - LOCKED_BOOT_COMPLETED: fires immediately after kernel boot, before first unlock (API 24+).
 *   Room database may NOT be available yet (device-encrypted storage only).
 *
 * ANDROID 14+ FIX:
 *   Direct startForegroundService() from a BroadcastReceiver can be silently blocked on
 *   Android 14+ due to "FGS from background" restrictions. We now delegate to
 *   [ServiceStartWorker] — an expedited WorkManager job — which is the documented way to
 *   reliably start a foreground service from a boot receiver context on modern Android.
 *
 * The [ServiceStartWorker] itself handles the DB-unavailability case (LOCKED_BOOT) by
 * starting the service defensively when the DB cannot be read.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AderaSMS"
        private const val ACTION_LOCKED_BOOT = "android.intent.action.LOCKED_BOOT_COMPLETED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != ACTION_LOCKED_BOOT) return

        Log.i(TAG, "BootReceiver: $action received → scheduling ServiceStartWorker")

        // Delegate to WorkManager expedited job. This is safe on all Android versions
        // including Android 14+ where direct startForegroundService() from a receiver
        // is restricted. goAsync() is not needed — WorkManager handles its own lifecycle.
        ServiceStartWorker.schedule(context.applicationContext)
    }
}
