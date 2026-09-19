package com.yungsamd17.singlenote.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.yungsamd17.singlenote.MainActivity
import com.yungsamd17.singlenote.R
import com.yungsamd17.singlenote.data.AppDatabase

class SinglenoteWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val note = AppDatabase.get(context).noteDao().getActive()
        val openIntent = Intent(context, MainActivity::class.java)
        val fallbackHint = context.getString(R.string.hint_write_one_thing)
        provideContent {
            WidgetContent(openIntent = openIntent, content = note?.content.orEmpty(), fallbackHint = fallbackHint)
        }
    }
}

@Composable
private fun WidgetContent(openIntent: Intent, content: String, fallbackHint: String) {
    GlanceTheme {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(16.dp)
                .clickable(actionStartActivity(openIntent))
                .padding(14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            // 2x1 cells fit ~2 lines: 6 lines always ellipsized into an
            // unreadable block, so cap at 2 with ellipsis.
            Text(
                text = content.ifBlank { fallbackHint },
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp
                ),
                maxLines = 2
            )
        }
    }
}

class SinglenoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SinglenoteWidget()
}
