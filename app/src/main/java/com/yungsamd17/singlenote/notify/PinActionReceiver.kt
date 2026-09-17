package com.yungsamd17.singlenote.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-posts the pinned notification after the user dismisses it (some skins
 * let ongoing notifications be swiped away). The delete intent fires only
 * on user dismissal, never on programmatic cancels, so this can't loop.
 * Work runs on IO with goAsync held so the process survives the refresh.
 */
class PinActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RESPAWN) return
        val pendingResult = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                PinNotification.refresh(app)
            } catch (e: Exception) {
                // Never swallow silently: a dead respawn with no trace is
                // undebuggable, and logcat is the only witness when the app
                // itself was closed.
                Log.w(TAG, "re-posting pinned note failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "PinActionReceiver"
        const val ACTION_RESPAWN = "com.yungsamd17.singlenote.RESPAWN_NOTE"

        private const val REQUEST_RESPAWN = 1003

        fun respawnIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                REQUEST_RESPAWN,
                Intent(context, PinActionReceiver::class.java).setAction(ACTION_RESPAWN),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
