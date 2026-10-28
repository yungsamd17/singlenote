package com.yungsamd17.singlenote.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import com.yungsamd17.singlenote.data.NotePreferences
import com.yungsamd17.singlenote.ui.accentScheme
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.text.FontWeight
import androidx.glance.unit.ColorProvider
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.yungsamd17.singlenote.MainActivity
import com.yungsamd17.singlenote.R
import com.yungsamd17.singlenote.data.AppDatabase

class SinglenoteWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = AppDatabase.get(context).noteDao()
        val initialNote = dao.getActive()
        val activeNote = dao.observeActive()
        val appAccent = NotePreferences.get(context).accentColor
        val initialAccent = appAccent.first()
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val initialStyle = WidgetStyleStore.get(context, widgetId)
        val widgetStyle = WidgetStyleStore.observe(context, widgetId)
        val fallbackHint = context.getString(R.string.hint_write_one_thing)
        val actionToken = WidgetActionSecurity.token(context)
        provideContent {
            val note by activeNote.collectAsState(initial = initialNote)
            val style by widgetStyle.collectAsState(initial = initialStyle)
            val selectedAppAccent by appAccent.collectAsState(initial = initialAccent)
            val openIntent = Intent(context, MainActivity::class.java)
                .setData(android.net.Uri.parse("singlenote://widget/$widgetId"))
                .putExtra(MainActivity.EXTRA_WIDGET_ACTION, style.action)
                .putExtra(WidgetActionSecurity.EXTRA_TOKEN, actionToken)
            WidgetContent(openIntent = openIntent, content = note?.content.orEmpty(), fallbackHint = fallbackHint, style = style, appAccent = selectedAppAccent, context = context, brand = context.getString(R.string.app_name))
        }
    }
}

@Composable
internal fun WidgetContent(openIntent: Intent?, content: String, fallbackHint: String, style: WidgetOptions, appAccent: String, context: Context, brand: String) {
    val accentKey = if (style.accent == "app") appAccent else style.accent
    val materialYou = accentKey == NotePreferences.ACCENT_SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val lightScheme = if (materialYou) dynamicLightColorScheme(context) else null
    val darkScheme = if (materialYou) dynamicDarkColorScheme(context) else null
    val opacity = style.opacity.coerceIn(0, 100) / 100f
    val lightBackground = (lightScheme?.surfaceContainerHigh ?: Color(0xFFECE6F0)).copy(alpha = opacity)
    val darkBackground = (darkScheme?.surfaceContainerHigh ?: Color(0xFF2B2930)).copy(alpha = opacity)
    val lightText = lightScheme?.onSurface ?: Color(0xFF1D1B20)
    val darkText = darkScheme?.onSurface ?: Color(0xFFE6E0E9)
    val lightAccent = widgetAccent(context, accentKey, false)
    val darkAccent = widgetAccent(context, accentKey, true)

    fun colors(light: Color, dark: Color): ColorProvider = when (style.theme) {
        "light" -> ColorProvider(light)
        "dark" -> ColorProvider(dark)
        else -> androidx.glance.color.ColorProvider(day = light, night = dark)
    }
    val background = colors(lightBackground, darkBackground)
    val foreground = colors(lightText, darkText)
    val accent = colors(lightAccent, darkAccent)
    val textSize = widgetFontSize(style.size)
    val noteText = content.ifBlank { fallbackHint }
    val topPadding = if (style.showTitle) 10.dp else widgetNoteTopPadding(context, textSize)
    val cardModifier = GlanceModifier.fillMaxSize().background(background).cornerRadius(16.dp)
    val interactiveModifier = if (openIntent == null) cardModifier else cardModifier.clickable(actionStartActivity(openIntent))
    Box(
        modifier = interactiveModifier.padding(start = 14.dp, top = topPadding, end = 14.dp, bottom = 14.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Column {
            if (style.showTitle) {
                Text(brand, style = TextStyle(color = accent, fontSize = style.titleSize.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            }
            val headerHeight = if (style.showTitle) style.titleSize * 1.4f else 0f
            val maxLines = ((LocalSize.current.height - topPadding - 14.dp).value - headerHeight).div(textSize * 1.4f).toInt().coerceIn(1, 12)
            Text(noteText, style = TextStyle(color = foreground, fontSize = textSize.sp), maxLines = maxLines)
        }
    }
}

class SinglenoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SinglenoteWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetStyleStore.remove(context, it) }
        super.onDeleted(context, appWidgetIds)
    }
}

internal fun widgetFontSize(size: String): Int = when (size) { "small" -> 14; "medium" -> 16; "large" -> 20; else -> size.toIntOrNull()?.coerceIn(10, 36) ?: 16 }
internal fun widgetAccent(context: Context, key: String, dark: Boolean): Color =
    if (key == NotePreferences.ACCENT_SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context).primary else dynamicLightColorScheme(context).primary
    } else accentScheme(key, dark).primary

/** Match the visible top and left edges when the note is the first line.
 * Android TextView includes font padding, which grows with the chosen sp size. */
internal fun widgetNoteTopPadding(context: Context, fontSize: Int): androidx.compose.ui.unit.Dp {
    val metrics = context.resources.displayMetrics
    val paint = android.graphics.Paint().apply {
        textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat(), metrics)
        typeface = android.graphics.Typeface.DEFAULT
    }
    // Use a stable cap-height sample so edits do not make the card jump.
    val sample = "H"
    val bounds = android.graphics.Rect()
    paint.getTextBounds(sample, 0, sample.length, bounds)
    val glyphInsetTop = bounds.top - paint.fontMetricsInt.top
    val glyphInsetLeft = bounds.left
    return (14f + (glyphInsetLeft - glyphInsetTop) / metrics.density).coerceIn(0f, 14f).dp
}
