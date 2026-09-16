package com.yungsamd17.singlenote.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import com.yungsamd17.singlenote.SinglenoteApplication
import com.yungsamd17.singlenote.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles pinned-notification buttons without opening the app. Unpin just
 * drops the pin; archive files the note away and unpins with it (repository
 * logic mirrors the editor: the next note starts unpinned). Work runs on IO
 * with goAsync held so the process survives until the database write and
 * the notification refresh finish.
 */
class PinActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext
                when (intent.action) {
                    ACTION_COPY -> {
                        val note = AppDatabase.get(app).noteDao().getActive()
                        val text = note?.content.orEmpty()
                        if (text.isNotBlank()) {
                            val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("note", text))
                        }
                        return@launch
                    }
                    else -> {
                        val repository = (app as SinglenoteApplication).repository
                        when (intent.action) {
                            ACTION_UNPIN -> repository.setPinned(false)
                            ACTION_ARCHIVE -> repository.archiveActive()
                        }
                        PinNotification.refresh(app)
                    }
                }
            } catch (e: Exception) {
                // Never swallow silently: a dead action with no trace is
                // undebuggable, and logcat is the only witness when the app
                // itself was closed.
                Log.w(TAG, "notification action failed: " + intent.action, e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "PinActionReceiver"
        const val ACTION_COPY = "com.yungsamd17.singlenote.COPY_NOTE"
        const val ACTION_UNPIN = "com.yungsamd17.singlenote.UNPIN_NOTE"
        const val ACTION_ARCHIVE = "com.yungsamd17.singlenote.ARCHIVE_NOTE"

        private const val REQUEST_COPY = 1000
        private const val REQUEST_UNPIN = 1001
        private const val REQUEST_ARCHIVE = 1002

        fun copyIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                REQUEST_COPY,
                Intent(context, PinActionReceiver::class.java).setAction(ACTION_COPY),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        fun unpinIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                REQUEST_UNPIN,
                Intent(context, PinActionReceiver::class.java).setAction(ACTION_UNPIN),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        fun archiveIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                REQUEST_ARCHIVE,
                Intent(context, PinActionReceiver::class.java).setAction(ACTION_ARCHIVE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
