package com.yungsamd17.singlenote.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
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
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.yungsamd17.singlenote.MainActivity
import com.yungsamd17.singlenote.R
import com.yungsamd17.singlenote.data.AppDatabase
import com.yungsamd17.singlenote.data.NotePreferences
import com.yungsamd17.singlenote.ui.accentScheme
import com.yungsamd17.singlenote.util.Diagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class SinglenoteWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Diagnostics.event(LOG_TAG, "provideGlance start id=$id")
        try {
            val dao = AppDatabase.get(context).noteDao()
            val initialNote = dao.getActive()
            val activeNote = dao.observeActive()
            Diagnostics.event(LOG_TAG, "note loaded active=${initialNote != null}")
            val appAccent = NotePreferences.get(context).accentColor
            val initialAccent = appAccent.first()
            val appDynamic = NotePreferences.get(context).dynamicColors
            val initialDynamic = appDynamic.first()
            val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
            val initialStyle = WidgetStyleStore.get(context, widgetId)
            val widgetStyle = WidgetStyleStore.observe(context, widgetId)
            val fallbackHint = context.getString(R.string.hint_write_one_thing)
            val actionToken = WidgetActionSecurity.token(context)
            Diagnostics.event(
                LOG_TAG,
                "prefs ready widgetId=$widgetId accent=$initialAccent dynamic=$initialDynamic " +
                    "theme=${initialStyle.theme} size=${initialStyle.size}",
            )
            provideContent {
                val note by activeNote.collectAsState(initial = initialNote)
                val style by widgetStyle.collectAsState(initial = initialStyle)
                val selectedAppAccent by appAccent.collectAsState(initial = initialAccent)
                val selectedDynamic by appDynamic.collectAsState(initial = initialDynamic)
                val openIntent =
                    Intent(context, MainActivity::class.java)
                        .setData(android.net.Uri.parse("singlenote://widget/$widgetId"))
                        .putExtra(MainActivity.EXTRA_WIDGET_ACTION, style.action)
                        .putExtra(WidgetActionSecurity.EXTRA_TOKEN, actionToken)
                WidgetContent(
                    openIntent = openIntent,
                    content = note?.content.orEmpty(),
                    fallbackHint = fallbackHint,
                    style = style,
                    appAccent = selectedAppAccent,
                    context = context,
                    brand = context.getString(R.string.app_name),
                    appDynamicColors = selectedDynamic,
                )
            }
            Diagnostics.event(LOG_TAG, "content published id=$id")
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Throwable) {
            // Glance swallows this and leaves the loading layout up, so record it
            // where the diagnostics dialog can show it on a release install.
            Diagnostics.failure(LOG_TAG, error)
            throw error
        }
    }

    private companion object {
        const val LOG_TAG = "widget"
    }
}

/**
 * Re-establishes the widget sessions from a live process.
 *
 * Glance 1.1.1 renders each widget in a WorkManager worker (unique work
 * "appWidget-<id>") that must find the in-memory render session of the
 * process that enqueued the update. When the OS reclaims that process before
 * the worker starts (aggressive background reaping, e.g. MIUI on release
 * installs), the worker's first attempt fails with "No session available for
 * key" and its retry only logs a warning and no-ops, so the widget is stuck on
 * the initial loading layout forever — nothing is logged and no later update
 * can recover it.
 *
 * Re-enqueueing the update from a process that stays alive fixes it: Glance
 * enqueues with ExistingWorkPolicy.REPLACE, so the stuck work is replaced and
 * the new worker renders in the live process, where the session exists. The
 * rendered widget then keeps its content even if a later session is lost.
 */
internal suspend fun refreshSinglenoteWidgets(context: Context) {
    val appContext = context.applicationContext
    val widgetIds = withContext(Dispatchers.IO) {
        val ids = AppWidgetManager.getInstance(appContext)
            .getAppWidgetIds(ComponentName(appContext, SinglenoteWidgetReceiver::class.java))
        if (ids.isEmpty()) return@withContext null
        SinglenoteWidget().updateAll(appContext)
        ids
    } ?: return
    widgetIds.forEach { id ->
        Diagnostics.event("widget", "widget session re-established (id=$id)")
    }
}

@Composable
internal fun WidgetContent(
    openIntent: Intent?,
    content: String,
    fallbackHint: String,
    style: WidgetOptions,
    appAccent: String,
    context: Context,
    brand: String,
    appDynamicColors: Boolean = false,
    preview: Boolean = false,
) {
    val followApp = widgetAccentSetting(style.accent) == "app"
    val accentKey = if (followApp) appAccent else style.accent
    val materialYou =
        followApp && appDynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val lightScheme = if (materialYou) dynamicLightColorScheme(context) else null
    val darkScheme = if (materialYou) dynamicDarkColorScheme(context) else null
    val opacity = style.opacity.coerceIn(0, 100) / 100f
    val lightBackground =
        (lightScheme?.surfaceContainerHigh ?: Color(0xFFECE6F0)).copy(alpha = opacity)
    val darkBackground =
        (darkScheme?.surfaceContainerHigh ?: Color(0xFF2B2930)).copy(alpha = opacity)
    val lightText = lightScheme?.onSurface ?: Color(0xFF1D1B20)
    val darkText = darkScheme?.onSurface ?: Color(0xFFE6E0E9)
    val lightAccent = widgetAccent(context, accentKey, false, materialYou)
    val darkAccent = widgetAccent(context, accentKey, true, materialYou)

    fun colors(light: Color, dark: Color): ColorProvider =
        when (style.theme) {
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
    val interactiveModifier =
        if (openIntent == null) cardModifier
        else cardModifier.clickable(actionStartActivity(openIntent))
    Box(
        modifier =
            interactiveModifier.padding(
                start = 14.dp,
                top = topPadding,
                end = 14.dp,
                bottom = 14.dp,
            ),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            if (style.showTitle) {
                Text(
                    brand,
                    modifier = if (openIntent == null) GlanceModifier else GlanceModifier.clickable(actionStartActivity(openIntent)),
                    style =
                        TextStyle(
                            color = accent,
                            fontSize = style.titleSize.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    maxLines = 1,
                )
            }
            if (preview) {
                // A standalone RemoteViews preview has no collection host to bind a list adapter.
                Text(
                    noteText,
                    modifier = GlanceModifier.fillMaxWidth(),
                    style = TextStyle(color = foreground, fontSize = textSize.sp),
                )
            } else {
                val titleHeight =
                    if (style.showTitle) widgetTextHeight(context, style.titleSize) else 0.dp
                val viewportHeight =
                    (LocalSize.current.height - topPadding - 14.dp - titleHeight).coerceAtLeast(
                        0.dp
                    )
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    item {
                        val rowModifier =
                            GlanceModifier.fillMaxWidth().let {
                                if (openIntent == null) it
                                else it.clickable(actionStartActivity(openIntent))
                            }
                        // A single wrap-content row grows with the full note. The spacer gives
                        // short notes a full-height tap target, including the blank list area.
                        Box(modifier = rowModifier, contentAlignment = Alignment.TopStart) {
                            Spacer(GlanceModifier.width(1.dp).height(viewportHeight))
                            Text(
                                noteText,
                                modifier = if (openIntent == null) GlanceModifier.fillMaxWidth() else GlanceModifier.fillMaxWidth().clickable(actionStartActivity(openIntent)),
                                style = TextStyle(color = foreground, fontSize = textSize.sp),
                            )
                        }
                    }
                }
            }
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

internal fun widgetFontSize(size: String): Int =
    when (size) {
        "small" -> 14
        "medium" -> 16
        "large" -> 20
        else -> size.toIntOrNull()?.coerceIn(10, 36) ?: 16
    }

internal fun widgetAccent(
    context: Context,
    key: String,
    dark: Boolean,
    dynamic: Boolean = false,
): Color =
    if (dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context).primary
        else dynamicLightColorScheme(context).primary
    } else accentScheme(key, dark).primary

/**
 * Match the visible top and left edges when the note is the first line. Android TextView includes
 * font padding, which grows with the chosen sp size.
 */
internal fun widgetNoteTopPadding(context: Context, fontSize: Int): androidx.compose.ui.unit.Dp {
    val metrics = context.resources.displayMetrics
    val paint =
        android.graphics.Paint().apply {
            textSize =
                android.util.TypedValue.applyDimension(
                    android.util.TypedValue.COMPLEX_UNIT_SP,
                    fontSize.toFloat(),
                    metrics,
                )
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

/** Legacy per-widget wallpaper selection now follows the app's Dynamic colors setting. */
internal fun widgetAccentSetting(stored: String?): String =
    if (stored == null || stored == NotePreferences.ACCENT_SYSTEM) "app" else stored

private fun widgetTextHeight(context: Context, fontSize: Int): androidx.compose.ui.unit.Dp {
    val metrics = context.resources.displayMetrics
    val paint =
        android.graphics.Paint().apply {
            textSize =
                android.util.TypedValue.applyDimension(
                    android.util.TypedValue.COMPLEX_UNIT_SP,
                    fontSize.toFloat(),
                    metrics,
                )
            typeface = android.graphics.Typeface.DEFAULT
        }
    val font = paint.fontMetricsInt
    return ((font.bottom - font.top) / metrics.density).dp
}
