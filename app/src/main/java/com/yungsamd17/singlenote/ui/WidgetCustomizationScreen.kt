package com.yungsamd17.singlenote.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yungsamd17.singlenote.R
import com.yungsamd17.singlenote.data.NotePreferences
import com.yungsamd17.singlenote.widget.SinglenoteWidgetReceiver
import com.yungsamd17.singlenote.widget.WidgetContent
import com.yungsamd17.singlenote.widget.WidgetEditorContent
import com.yungsamd17.singlenote.widget.WidgetOptions
import com.yungsamd17.singlenote.widget.WidgetStyleStore
import com.yungsamd17.singlenote.widget.refreshSinglenoteWidgets
import com.yungsamd17.singlenote.widget.rememberAddWidgetRequest
import com.yungsamd17.singlenote.widget.widgetPreviewPlaceholder
import kotlinx.coroutines.launch

/**
 * In-app widget customization. Unlike the add-widget configuration activity,
 * this works on the remembered style and applies it to every widget already on
 * the home screen, with a save FAB that only appears after a real change.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlanceRemoteViewsApi::class)
@Composable
fun WidgetCustomizationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val addWidget = rememberAddWidgetRequest(snackbarHostState)
    val systemDark = isSystemInDarkTheme()

    val prefs = remember { NotePreferences.get(context.applicationContext) }
    val appAccent by prefs.accentColor.collectAsStateWithLifecycle(initialValue = NotePreferences.ACCENT_DEFAULT)
    val appTheme by prefs.themeMode.collectAsStateWithLifecycle(initialValue = NotePreferences.THEME_SYSTEM)
    val dynamic by prefs.dynamicColors.collectAsStateWithLifecycle(initialValue = false)
    val amoled by prefs.amoled.collectAsStateWithLifecycle(initialValue = false)
    val monochrome by prefs.monochrome.collectAsStateWithLifecycle(initialValue = false)
    val appFont by prefs.appFont.collectAsStateWithLifecycle(initialValue = "system")
    val noteFont by prefs.fontFamily.collectAsStateWithLifecycle(initialValue = NotePreferences.FONT_DEFAULT)

    val appDark =
        when (appTheme) {
            NotePreferences.THEME_LIGHT -> false
            NotePreferences.THEME_DARK -> true
            else -> systemDark
        }
    val baseScheme =
        if (dynamic && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (appDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            accentScheme(appAccent, appDark)
        }
    val colored = if (monochrome) baseScheme.toMonochrome() else baseScheme
    val scheme =
        if (amoled && appDark) {
            colored.copy(
                surface = androidx.compose.ui.graphics.Color.Black,
                background = androidx.compose.ui.graphics.Color.Black,
            )
        } else {
            colored
        }
    var options by remember {
        mutableStateOf(
            try {
                WidgetStyleStore.remembered(context) ?: WidgetOptions()
            } catch (_: Exception) {
                WidgetOptions()
            },
        )
    }
    var savedOptions by remember { mutableStateOf(options) }
    val dirty = options != savedOptions

    // Live Glance preview, exactly the renderer used on the home screen.
    val previewText = stringResource(R.string.widget_preview_note)
    val brand = stringResource(R.string.app_name)
    val renderer = remember { GlanceRemoteViews() }
    var previewViews by remember {
        mutableStateOf<android.widget.RemoteViews?>(widgetPreviewPlaceholder(context))
    }
    LaunchedEffect(options, appAccent, dynamic, monochrome, noteFont, appFont, previewText, brand, scheme.background) {
        try {
            previewViews =
                renderer
                    .compose(context = context, size = androidx.compose.ui.unit.DpSize(320.dp, 120.dp)) {
                        WidgetContent(
                            openIntent = null,
                            content = previewText,
                            fallbackHint = previewText,
                            style = options,
                            appAccent = appAccent,
                            noteFont = noteFont,
                            appFont = appFont,
                            context = context,
                            brand = brand,
                            appDynamicColors = dynamic,
                            appMonochrome = monochrome,
                            scrollable = false,
                            backdropColor = scheme.background,
                        )
                    }
                    .remoteViews
        } catch (_: Exception) {
            // Keep the first-frame placeholder when Glance cannot compose.
        }
    }

    fun save() {
        try {
            WidgetStyleStore.remember(context, options)
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, SinglenoteWidgetReceiver::class.java))
            ids.forEach { WidgetStyleStore.set(context, it, options) }
        } catch (_: Exception) {
            // The remembered style is enough for the next widget even when
            // the launcher refuses to enumerate the current ones.
        }
        savedOptions = options
        scope.launch {
            try { refreshSinglenoteWidgets(context) } catch (_: Exception) { }
            snackbarHostState.showSnackbar(context.getString(R.string.widget_saved))
        }
    }

    MaterialTheme(colorScheme = scheme, typography = appTypography(appFont)) {
        SettingsPage(
            stringResource(R.string.widget_customize),
            onBack,
            snackbarHost = {
                SwipeableSnackbarHost(snackbarHostState, modifier = AppSnackbarHostModifier)
            },
            floatingActionButton = {
                if (dirty) {
                    TooltipBubble(tooltip = stringResource(R.string.widget_save)) {
                        FloatingActionButton(onClick = ::save, shape = CircleShape) {
                            Icon(Icons.Filled.Save, contentDescription = null)
                        }
                    }
                }
            },
            actions = {
                TooltipIconButton(
                    tooltip = stringResource(R.string.widget_add_home),
                    onClick = addWidget,
                ) {
                    Icon(
                        Icons.Outlined.DashboardCustomize,
                        contentDescription = stringResource(R.string.widget_add_home),
                    )
                }
            },
        ) {
            WidgetEditorContent(
                options = options,
                onOptionsChange = { options = it },
                header = {
                    SettingsSectionHeader(stringResource(R.string.widget_preview))
                    AndroidView(
                        factory = { android.widget.FrameLayout(it) },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        update = { parent ->
                            parent.removeAllViews()
                            previewViews?.apply(parent.context, parent)?.let { parent.addView(it) }
                        },
                    )
                },
            )
        }
    }
}
