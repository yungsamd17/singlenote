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
import com.yungsamd17.singlenote.data.AppDatabase

class SinglenoteWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val note = AppDatabase.get(context).noteDao().getActive()
        val openIntent = Intent(context, MainActivity::class.java)
        provideContent {
            WidgetContent(openIntent = openIntent, content = note?.content.orEmpty())
        }
    }
}

@Composable
private fun WidgetContent(openIntent: Intent, content: String) {
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
            Text(
                text = content.ifBlank { FALLBACK_HINT },
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 18.sp
                ),
                maxLines = 6
            )
        }
    }
}

class SinglenoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SinglenoteWidget()
}

private const val FALLBACK_HINT = "Write one thing to remember…"
