package com.yungsamd17.singlenote.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/**
 * Opens the system widget picker so the user can place a new widget.
 *
 * Both in-app entry points use this public pin request: it shows the system
 * confirmation and adds the widget without needing a hidden id-allocation API.
 * A new widget picks up the remembered style, because `WidgetStyleStore.get`
 * falls back to it when no style was saved for the new id yet.
 */
internal fun requestAddSinglenoteWidget(context: Context) {
    AppWidgetManager.getInstance(context).requestPinAppWidget(
        ComponentName(context, SinglenoteWidgetReceiver::class.java),
        null,
        null,
    )
}
