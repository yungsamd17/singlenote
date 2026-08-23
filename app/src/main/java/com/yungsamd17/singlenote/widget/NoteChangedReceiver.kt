package com.yungsamd17.singlenote.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yungsamd17.singlenote.data.ACTION_NOTE_UPDATED
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NoteChangedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_NOTE_UPDATED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                SinglenoteWidget().updateAll(context)
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }
}
