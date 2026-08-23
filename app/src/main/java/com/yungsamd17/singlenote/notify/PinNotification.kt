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

    private const val CHANNEL_ID = "pinned_note"
    private const val NOTIFICATION_ID = 1

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_pinned_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_pinned_description)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    suspend fun refresh(context: Context) {
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        val prefs = NotePreferences(context)

        val notificationsEnabled = prefs.notificationsEnabled.first()
        val pinned = prefs.pinned.first()
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

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(note.content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(note.content))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(openIntent)
            .build()

        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }
}
