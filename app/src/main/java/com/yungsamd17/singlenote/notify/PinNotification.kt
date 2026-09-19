package com.yungsamd17.singlenote.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.yungsamd17.singlenote.MainActivity
import com.yungsamd17.singlenote.R
import com.yungsamd17.singlenote.data.AppDatabase
import kotlinx.coroutines.flow.first
import com.yungsamd17.singlenote.data.NotePreferences

object PinNotification {

    private const val CHANNEL_ID = "pinned_note_v3"
    private const val LEGACY_CHANNEL_ID = "pinned_note"
    private const val LEGACY_CHANNEL_ID_V2 = "pinned_note_v2"
    private const val NOTIFICATION_ID = 1
    private const val REQUEST_OPEN_APP = 2

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // Channels are immutable once created: the old DEFAULT-importance
        // channels exposed full note text on the lock screen, so the
        // LOW-importance PRIVATE-by-default channel below gets a new id and
        // the legacy ones are removed (their settings would otherwise stick
        // forever).
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID_V2)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_pinned_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_pinned_description)
                setShowBadge(false)
                // Channel default hides content on the lock screen; the
                // notification below stays PRIVATE unless the user opts
                // in via Settings.
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            }
            manager.createNotificationChannel(channel)
        }
    }

    suspend fun refresh(context: Context) {
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        val prefs = NotePreferences.get(context)

        val notificationsEnabled = prefs.notificationsEnabled.first()
        val pinned = prefs.pinned.first()
        val lockscreenVisible = prefs.lockscreenVisible.first()
        val note = AppDatabase.get(context).noteDao().getActive()

        if (!notificationsEnabled || !pinned || note == null || note.content.isBlank()) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        if (!manager.areNotificationsEnabled()) return

        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openAction = PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_pinned_title))
            .setContentText(note.content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(note.content))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // Private by default: full content only on the lock screen when
            // the user opts in via Settings. Ongoing and silent on every
            // refresh (it re-posts on each save while pinned).
            .setVisibility(
                if (lockscreenVisible) NotificationCompat.VISIBILITY_PUBLIC
                else NotificationCompat.VISIBILITY_PRIVATE
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            // Re-post if the user dismisses the pin anyway (some skins let
            // ongoing notifications be swiped away).
            .setDeleteIntent(PinActionReceiver.respawnIntent(context))
            .setShowWhen(false)
            .setContentIntent(openIntent)
            // Single action on purpose: buttons that need background work
            // go dead on restrictive ROMs once the app is closed, but an
            // explicit open always works (user-initiated activity start).
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.notification_action_open),
                openAction
            )
            .build()

        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }
}
