package com.yungsamd17.singlenote.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Re-renders widgets when the system switches between light and dark.
 *
 * Glance resolves a "system" theme `ColorProvider` (day/night text colors)
 * into concrete RemoteViews colors at composition time. A theme switch only
 * changes the configuration — it does not re-compose the widget — so the text
 * kept the old color until some unrelated update happened. Watching for a real
 * night-mode transition and re-rendering fixes that.
 */
object WidgetThemeWatcher {
    @Volatile private var lastNight: Int? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun init(context: Context) {
        lastNight = nightMode(context)
    }

    fun onConfigurationChanged(context: Context) {
        val night = nightMode(context)
        val previous = lastNight
        lastNight = night
        if (previous == null || previous == night) return
        scope.launch { refreshSinglenoteWidgets(context.applicationContext) }
    }

    private fun nightMode(context: Context): Int =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

    /** Re-render after a wallpaper swap: transparent text follows the wallpaper. */
    fun refreshWidgets(context: Context) {
        scope.launch { refreshSinglenoteWidgets(context.applicationContext) }
    }

    fun register(context: Context) {
        init(context)
        val filter = IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED).apply {
            addAction(Intent.ACTION_WALLPAPER_CHANGED)
        }
        @Suppress("UnspecifiedRegisterReceiverFlag")
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(WidgetThemeReceiver(), filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(WidgetThemeReceiver(), filter)
        }
    }
}

class WidgetThemeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_CONFIGURATION_CHANGED -> WidgetThemeWatcher.onConfigurationChanged(context)
            Intent.ACTION_WALLPAPER_CHANGED -> WidgetThemeWatcher.refreshWidgets(context)
        }
    }
}
