package com.yungsamd17.singlenote.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.GlanceRemoteViews
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
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.yungsamd17.singlenote.MainActivity
import com.yungsamd17.singlenote.R
import com.yungsamd17.singlenote.util.ActionSecurity
import com.yungsamd17.singlenote.data.AppDatabase
import com.yungsamd17.singlenote.data.NotePreferences
import com.yungsamd17.singlenote.ui.accentScheme
import com.yungsamd17.singlenote.ui.toMonochrome
import com.yungsamd17.singlenote.util.Diagnostics
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class SinglenoteWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Diagnostics.event(LOG_TAG, "provideGlance start id=$id")
        try {
            provideSinglenoteContent(context, id)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            // Glance swallows this and leaves the loading layout up, so record it
            // where the diagnostics dialog can show it on a release install.
            Diagnostics.failure(LOG_TAG, error)
            throw error
        }
    }
}

private const val LOG_TAG = "widget"

/** Loads the note and the widget style, then hands them to the composition. */
private suspend fun GlanceAppWidget.provideSinglenoteContent(
    context: Context,
    id: GlanceId,
) {
    val dao = AppDatabase.get(context).noteDao()
    val initialNote = dao.getActive()
    val activeNote = dao.observeActive()
    Diagnostics.event(LOG_TAG, "note loaded active=${initialNote != null}")
    val appAccent = NotePreferences.get(context).accentColor
    val initialAccent = appAccent.first()
    val appDynamic = NotePreferences.get(context).dynamicColors
    val initialDynamic = appDynamic.first()
    val appMonochrome = NotePreferences.get(context).monochrome
    val initialMonochrome = appMonochrome.first()
    val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
    val initialStyle = WidgetStyleStore.get(context, widgetId)
    val widgetStyle = WidgetStyleStore.observe(context, widgetId)
    val fallbackHint = context.getString(R.string.hint_write_one_thing)
    val actionToken = ActionSecurity.token(context)
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
        val selectedMonochrome by appMonochrome.collectAsState(initial = initialMonochrome)
        val openIntent =
            Intent(context, MainActivity::class.java)
                .setData(android.net.Uri.parse("singlenote://widget/$widgetId"))
                .putExtra(MainActivity.EXTRA_WIDGET_ACTION, style.action)
                .putExtra(ActionSecurity.EXTRA_TOKEN, actionToken)
        WidgetContent(
            openIntent = openIntent,
            content = note?.content.orEmpty(),
            fallbackHint = fallbackHint,
            style = style,
            appAccent = selectedAppAccent,
            context = context,
            brand = context.getString(R.string.app_name),
            appDynamicColors = selectedDynamic,
            appMonochrome = selectedMonochrome,
        )
    }
}

/**
 * Refreshes every Singlenote widget.
 *
 * Glance renders each widget in a WorkManager worker (unique work
 * "appWidget-<id>") that has to find the in-memory render session of the
 * process that requested the update. Two things go wrong on some devices
 * (MIUI on release installs):
 *
 *  - The worker never finds the session. The launcher places the widget and
 *    the system reaps the app right afterwards, so the worker starts in a
 *    fresh process where the session is gone: the work fails and the widget
 *    stays on the initial loading layout, no matter how often an update is
 *    requested.
 *  - The worker is stuck in the queue while its session is still open. Glance
 *    then believes the session is running and drops every later update
 *    silently, so the widget stays on the loading layout forever.
 *
 * So this renders the widget right here, where the update was requested: that
 * path does not depend on the job scheduler at all. Glance is asked for an
 * update afterwards, so devices where its worker runs keep the regular
 * behaviour.
 */
internal suspend fun refreshSinglenoteWidgets(context: Context) {
    val appContext = context.applicationContext
    val widgetIds = widgetIdsOf(appContext)
    if (widgetIds.isEmpty()) return
    widgetIds.forEach { id -> clearStuckGlanceSession(appContext, id) }
    // Render first: this path needs no worker, while the Glance request can
    // take a while (or never finish) on a device that reaps the app right
    // after the broadcast that asked for the update.
    if (canRenderListInProcess) {
        widgetIds.forEach { id -> renderWidgetInProcess(appContext, id, trigger = "refresh") }
    }
    // Then ask Glance for its own update, so devices where its worker runs
    // keep the regular behaviour. The request is bounded: it must not hold
    // up the caller (the widget is already up to date at this point).
    withTimeoutOrNull(GLANCE_UPDATE_TIMEOUT_MS) {
        withContext(Dispatchers.IO) { SinglenoteWidget().updateAll(appContext) }
    }
}

/** Renders the widgets in the process the launcher asked for them in. */
internal suspend fun renderSinglenoteWidgetsInProcess(context: Context) {
    if (!canRenderListInProcess) return
    val appContext = context.applicationContext
    widgetIdsOf(appContext).forEach { id ->
        renderWidgetInProcess(appContext, id, trigger = "receiver")
    }
}

/** The ids of the Singlenote widgets on the home screen. */
private suspend fun widgetIdsOf(context: Context): IntArray =
    withContext(Dispatchers.IO) {
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, SinglenoteWidgetReceiver::class.java))
    }

/**
 * From Android 12 on, a one-shot render is the same as Glance's: the list
 * items travel with the RemoteViews, so the widget scrolls like the one
 * Glance renders.
 *
 * Below that a lazy list needs a bound collection service, which only the
 * Glance session has: a one-shot render would draw the note as one text
 * block, without scrolling. There Glance's own render is kept.
 */
private val canRenderListInProcess: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Cancels the worker of a widget that is queued but never started.
 *
 * Glance skips enqueueing an update while the session of a widget is still
 * open and its worker is still queued: the session lives in the process and
 * the worker was never picked up, so every later update is dropped without a
 * trace. Cancelling the queued worker makes Glance start a fresh session.
 */
private suspend fun clearStuckGlanceSession(context: Context, appWidgetId: Int) {
    val states = glanceWorkStates(context, appWidgetId)
    // A running worker is rendering right now: leave it alone.
    if (states.any { it == WorkInfo.State.RUNNING }) return
    if (!states.any { it == WorkInfo.State.ENQUEUED }) return
    Diagnostics.event("widget", "clearing stuck glance worker (id=$appWidgetId)")
    withContext(Dispatchers.IO) {
        runCatching {
            WorkManager.getInstance(context)
                .cancelUniqueWork(glanceWorkName(appWidgetId))
                .result
                .get(WORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }.onFailure { Diagnostics.failure("widget", it) }
    }
}

/** The states of the worker Glance enqueued for this widget. */
private suspend fun glanceWorkStates(context: Context, appWidgetId: Int): List<WorkInfo.State> =
    withContext(Dispatchers.IO) {
        runCatching {
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(glanceWorkName(appWidgetId))
                .get(WORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .map { it.state }
        }.onFailure { Diagnostics.failure("widget", it) }.getOrDefault(emptyList())
    }

/** The unique work name Glance uses for the worker of a widget. */
private fun glanceWorkName(appWidgetId: Int): String = "appWidget-$appWidgetId"

/**
 * Renders the widget in this process and publishes it right away.
 *
 * Glance normally renders widgets in a WorkManager worker. When that worker
 * cannot find the session of the process that asked for the update, the
 * widget keeps the loading layout even though the app is in the foreground
 * and asked for an update. Composing the same content here does not depend
 * on the job scheduler at all.
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
private suspend fun renderWidgetInProcess(
    context: Context,
    appWidgetId: Int,
    trigger: String,
): Boolean =
    try {
        val dao = AppDatabase.get(context).noteDao()
        val note = dao.getActive()?.content.orEmpty()
        val style = WidgetStyleStore.get(context, appWidgetId)
        val appAccent = NotePreferences.get(context).accentColor.first()
        val appDynamic = NotePreferences.get(context).dynamicColors.first()
        val appMonochrome = NotePreferences.get(context).monochrome.first()
        val fallbackHint = context.getString(R.string.hint_write_one_thing)
        val brand = context.getString(R.string.app_name)
        val openIntent =
            Intent(context, MainActivity::class.java)
                .setData(android.net.Uri.parse("singlenote://widget/$appWidgetId"))
                .putExtra(MainActivity.EXTRA_WIDGET_ACTION, style.action)
                .putExtra(ActionSecurity.EXTRA_TOKEN, ActionSecurity.token(context))
        val views =
            withContext(Dispatchers.Main) {
                GlanceRemoteViews()
                    .compose(
                        context = context,
                        size = widgetSize(context, appWidgetId),
                        appWidgetOptions =
                            AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId),
                    ) {
                        WidgetContent(
                            openIntent = openIntent,
                            content = note,
                            fallbackHint = fallbackHint,
                            style = style,
                            appAccent = appAccent,
                            context = context,
                            brand = brand,
                            appDynamicColors = appDynamic,
                            appMonochrome = appMonochrome,
                            // From Android 12 on, the list items travel with the
                            // RemoteViews, so the widget scrolls like the one
                            // Glance renders. Older versions need a bound
                            // collection service, which a one-shot render
                            // cannot provide: there the note is one text block.
                            scrollable = canRenderListInProcess,
                        )
                    }
                    .remoteViews
            }
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
        Diagnostics.event("widget", "widget rendered in-process (id=$appWidgetId, trigger=$trigger)")
        true
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Diagnostics.failure("widget", error)
        false
    }

/** The size the launcher gave the widget, used to lay the content out. */
private fun widgetSize(context: Context, appWidgetId: Int): DpSize {
    val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
    val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
    val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 40)
    return DpSize(width.dp, height.dp)
}

private const val WORK_TIMEOUT_SECONDS = 5L
private const val GLANCE_UPDATE_TIMEOUT_MS = 5_000L

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
    // The widget only follows the monochrome mode when it follows the app
    // accent: a widget with its own accent keeps that accent.
    appMonochrome: Boolean = false,
    // A lazy list is served by a bound collection service, which only has
    // items while the app process lives. Previews and the in-process render
    // use one text block instead, so the note stays visible either way.
    scrollable: Boolean = true,
) {
    val followApp = widgetAccentSetting(style.accent) == "app"
    val accentKey = if (followApp) appAccent else style.accent
    val monochrome = followApp && appMonochrome
    val materialYou =
        followApp && appDynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dynamicLight = if (materialYou) dynamicLightColorScheme(context) else null
    val dynamicDark = if (materialYou) dynamicDarkColorScheme(context) else null
    val lightScheme = if (monochrome) dynamicLight?.toMonochrome() else dynamicLight
    val darkScheme = if (monochrome) dynamicDark?.toMonochrome() else dynamicDark
    val opacity = style.opacity.coerceIn(0, 100) / 100f
    val lightBackground =
        (lightScheme?.surfaceContainerHigh ?: Color(0xFFECE6F0)).copy(alpha = opacity)
    val darkBackground =
        (darkScheme?.surfaceContainerHigh ?: Color(0xFF2B2930)).copy(alpha = opacity)
    val lightText = lightScheme?.onSurface ?: Color(0xFF1D1B20)
    val darkText = darkScheme?.onSurface ?: Color(0xFFE6E0E9)
    val lightAccent = widgetAccent(context, accentKey, false, materialYou, monochrome)
    val darkAccent = widgetAccent(context, accentKey, true, materialYou, monochrome)

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
            if (!scrollable) {
                // No collection host to bind a list adapter to.
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

    /**
     * Renders the widgets in the process that receives the broadcast.
     *
     * This is the path the launcher takes when a widget is added, resized or
     * refreshed. Glance answers such a broadcast by queueing a worker that
     * has to find the in-memory session of this process. When the system
     * reaps the app right after the launcher placed the widget, the worker
     * starts in a fresh process, finds no session and fails: the widget keeps
     * its loading layout until something else updates it.
     *
     * Rendering here fills the widget right away, without the job scheduler.
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_DELETED) {
            // Glance cleans up after itself here and takes the pending result.
            super.onReceive(context, intent)
            return
        }
        // Everything else is handled here.
        //
        // Glance's own hooks (onUpdate, onAppWidgetOptionsChanged,
        // onDeleted), its locale handling and its action handling call
        // goAsync() as well, and a receiver can hold only one pending
        // result: the second call returns null, and Glance finishes that
        // null result -> the app crashes. So the update is driven from here,
        // where the pending result belongs to this class.
        //
        // The broadcasts that reach this branch:
        // - APPWIDGET_UPDATE: the launcher adds, resizes or refreshes a widget
        // - APPWIDGET_OPTIONS_CHANGED: a widget was resized
        // - APPWIDGET_RESTORED: the launcher restored a widget (it reaches
        //   Glance's onUpdate, because AppWidgetProvider restores a widget
        //   by updating it)
        // - LOCALE_CHANGED: the app language changed (Glance renders on a
        //   locale change as well)
        // - Glance's own debug update
        renderFromReceiver(context, goAsync())
    }
}

/**
 * Renders the widgets and asks Glance for an update, then releases the
 * pending result of the broadcast.
 */
private fun renderFromReceiver(context: Context, pendingResult: BroadcastReceiver.PendingResult?) {
    val appContext = context.applicationContext
    receiverScope.launch {
        try {
            renderSinglenoteWidgetsInProcess(appContext)
            // Glance gets its own update as well, so devices where its worker
            // runs keep the regular behaviour (there, and below Android 12,
            // it is the only path that renders the widget).
            withTimeoutOrNull(GLANCE_UPDATE_TIMEOUT_MS) {
                withContext(Dispatchers.IO) { SinglenoteWidget().updateAll(appContext) }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Diagnostics.failure(LOG_TAG, error)
        } finally {
            pendingResult?.finish()
        }
    }
}

/** The receiver's renders outlive the broadcast, but not the process. */
private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
    monochrome: Boolean = false,
): Color {
    val scheme =
        if (dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else accentScheme(key, dark)
    return if (monochrome) scheme.toMonochrome().primary else scheme.primary
}

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
