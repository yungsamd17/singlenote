package com.yungsamd17.singlenote.tile

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import com.yungsamd17.singlenote.MainActivity
import com.yungsamd17.singlenote.R
import com.yungsamd17.singlenote.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NoteTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            val summary = withContext(Dispatchers.Default) {
                AppDatabase.get(applicationContext).noteDao().getActive()
                    ?.content
                    ?.lineSequence()
                    ?.firstOrNull { it.isNotBlank() }
                    ?.take(MAX_SUBTITLE_LENGTH)
            }
            val tile = qsTile ?: return@launch
            tile.label = getString(R.string.app_name)
            tile.subtitle = summary ?: getString(R.string.tile_no_note)
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_OPEN_APP,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(launchIntent)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val MAX_SUBTITLE_LENGTH = 40
        private const val REQUEST_CODE_OPEN_APP = 1

        fun componentName(context: Context): ComponentName =
            ComponentName(context, NoteTileService::class.java)
    }
}
