package com.yungsamd17.singlenote.notify

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.yungsamd17.singlenote.R
import com.yungsamd17.singlenote.util.Diagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the app process alive while a note is pinned.
 *
 * HyperOS kills background processes and drops their manifest receivers, so a
 * plain BroadcastReceiver cannot be relied on to hide the note on lock and
 * restore it on unlock. A foreground service — whose notification is the
 * pinned note itself — keeps the live receiver working for as long as the pin
 * is on, without adding a second notification.
 */
class PinnedNoteService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService() gives us ~5s before Android kills the app.
        // Post a generic notification immediately, then let the refresh replace
        // it — the old order crashed with ForegroundServiceDidNotStartInTime.
        PinNotification.ensureChannel(this)
        instance = this
        promote(buildInitialNotification())
        scope.launch {
            try {
                PinNotification.refresh(applicationContext)
                val notification = PinNotification.lastNotification
                if (notification != null) {
                    promote(notification)
                } else {
                    // Nothing pinned: don't keep a pointless service around.
                    stopSelf()
                }
            } catch (e: Exception) {
                Diagnostics.failure("pin", e)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun buildInitialNotification(): Notification =
        NotificationCompat.Builder(this, PinNotification.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_pinned_title))
            .setContentText(getString(R.string.notification_hidden_body))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun promote(notification: Notification) {
        ServiceCompat.startForeground(
            this,
            PinNotification.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        instance = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        @Volatile private var instance: PinnedNoteService? = null

        /**
         * Forces the system to replace the foreground service notification,
         * not just the notification row — some OEM lock screens keep the row
         * captured at startForeground() time.
         */
        fun updateNotification(notification: Notification) {
            instance?.promote(notification)
        }

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, PinnedNoteService::class.java))
            } catch (e: Exception) {
                // Background start limits on some ROMs; the live receiver still
                // works while the process happens to be around.
                Diagnostics.failure("pin", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PinnedNoteService::class.java))
        }
    }
}
