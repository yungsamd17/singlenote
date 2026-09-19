package com.yungsamd17.singlenote.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService
import android.util.Log
import com.yungsamd17.singlenote.data.ACTION_NOTE_UPDATED
import com.yungsamd17.singlenote.notify.PinNotification
import com.yungsamd17.singlenote.tile.NoteTileService
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NoteChangedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_NOTE_UPDATED) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        // Room + Glance are blocking IO: Default would starve compute work,
        // and the silent catch-all made dead updates undebuggable.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SinglenoteWidget().updateAll(appContext)
                PinNotification.refresh(appContext)
                TileService.requestListeningState(appContext, NoteTileService.componentName(appContext))
            } catch (e: Exception) {
                Log.e(TAG, "handling note update failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "NoteChangedReceiver"
    }
}
