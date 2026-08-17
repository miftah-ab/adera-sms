package com.adera.sms.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.adera.sms.MainActivity
import com.adera.sms.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Handles Firebase Cloud Messaging (FCM) token refresh and incoming push messages.
 *
 * PURPOSE (Item 6):
 *   SDK integration only. This service ensures the app can RECEIVE push notifications
 *   sent from the Firebase console. No specific campaign content is configured here —
 *   that is done from the Firebase console when needed.
 *
 * TOKEN LIFECYCLE:
 *   onNewToken fires when FCM assigns a new registration token (first install, token
 *   rotation, or app reinstall). The token is logged so it can be inspected during
 *   development. In a future server-driven version, this would be sent to a backend.
 *
 * NOTIFICATION DISPLAY:
 *   When a data message is received (e.g. a Pro launch announcement sent from the
 *   Firebase console), onMessageReceived builds and posts a notification. If the
 *   message is a notification message (has a "notification" payload), FCM handles
 *   display automatically when the app is in the background.
 */
class AderaFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "AderaSMS_FCM"
        private const val CHANNEL_ID = "adera_push_channel"
        private const val CHANNEL_NAME = "Adera SMS Updates"
        private const val NOTIFICATION_ID = 2001
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Log the token for development inspection.
        // When a server-side component is added, send this token there.
        Log.i(TAG, "FCM token refreshed: $token")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "FCM message received from: ${message.from}")

        // Build notification title/body from the message payload.
        // Firebase console sends these as notification.title / notification.body,
        // or as data map keys for data messages.
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "Adera SMS"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: return  // No displayable content — skip

        postNotification(title, body)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun postNotification(title: String, body: String) {
        val manager = getSystemService(NotificationManager::class.java)

        // Ensure notification channel exists (required API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Adera SMS news and announcements"
                }
                manager.createNotificationChannel(channel)
            }
        }

        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }
}
