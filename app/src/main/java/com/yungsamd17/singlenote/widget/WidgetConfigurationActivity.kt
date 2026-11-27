package com.yungsamd17.singlenote.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FormatPaint
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.yungsamd17.singlenote.R
import com.yungsamd17.singlenote.data.NotePreferences
import com.yungsamd17.singlenote.ui.SettingsGroup
import com.yungsamd17.singlenote.ui.SettingsSectionHeader
import com.yungsamd17.singlenote.ui.ToggleRow
import com.yungsamd17.singlenote.ui.accentScheme
import com.yungsamd17.singlenote.ui.toMonochrome
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class WidgetConfigurationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val widgetId =
            intent?.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (
            widgetId == AppWidgetManager.INVALID_APPWIDGET_ID ||
                AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider !=
                    android.content.ComponentName(this, SinglenoteWidgetReceiver::class.java)
        ) {
            finish()
            return
        }
        val isEditingWidget = WidgetStyleStore.exists(this, widgetId)
        setContent {
            val systemDark = isSystemInDarkTheme()
            val appAccent by
                NotePreferences.get(applicationContext)
                    .accentColor
                    .collectAsStateWithLifecycle(initialValue = NotePreferences.ACCENT_DEFAULT)
            val appTheme by
                NotePreferences.get(applicationContext)
                    .themeMode
                    .collectAsStateWithLifecycle(initialValue = NotePreferences.THEME_SYSTEM)
            val prefs = NotePreferences.get(applicationContext)
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
                if (
                    dynamic && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                ) {
                    if (appDark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
                } else accentScheme(appAccent, appDark)
            // Monochrome wins over the accent and the wallpaper colors.
            val colorScheme = if (monochrome) baseScheme.toMonochrome() else baseScheme
            val scheme =
                if (amoled && appDark)
                    colorScheme.copy(
                        surface = androidx.compose.ui.graphics.Color.Black,
                        background = androidx.compose.ui.graphics.Color.Black,
                    )
                else colorScheme
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !appDark
                    isAppearanceLightNavigationBars = !appDark
                }
                window.setBackgroundDrawable(
                    android.graphics.drawable.ColorDrawable(scheme.background.toArgb())
                )
            }
            var options by remember { mutableStateOf(WidgetStyleStore.get(this, widgetId)) }
            // The settings are remembered while they change, so a widget that
            // is added later starts with them instead of the defaults. The
            // settings of this widget itself are stored when it is confirmed.
            LaunchedEffect(options) {
                WidgetStyleStore.remember(this@WidgetConfigurationActivity, options)
            }
            val previewText = stringResource(R.string.widget_preview_note)
            val brand = stringResource(R.string.app_name)
            val renderer = remember { GlanceRemoteViews() }
            var previewViews by remember { mutableStateOf<android.widget.RemoteViews?>(null) }
            LaunchedEffect(options, appAccent, dynamic, monochrome, noteFont, appFont, previewText, brand, scheme.background) {
                previewViews =
                    renderer
                        .compose(
                            context = this@WidgetConfigurationActivity,
                            size = DpSize(320.dp, 120.dp),
                        ) {
                            WidgetContent(
                                openIntent = null,
                                content = previewText,
                                fallbackHint = previewText,
                                style = options,
                                appAccent = appAccent,
                                noteFont = noteFont,
                                appFont = appFont,
                                context = this@WidgetConfigurationActivity,
                                brand = brand,
                                appDynamicColors = dynamic,
                                appMonochrome = monochrome,
                                scrollable = false,
                                // The preview sits on the activity background,
                                // not on the home-screen wallpaper.
                                backdropColor = scheme.background,
                            )
                        }
                        .remoteViews
            }
            MaterialTheme(
                colorScheme = scheme,
                typography = com.yungsamd17.singlenote.ui.appTypography(appFont),
            ) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                        WidgetEditorContent(
                            options = options,
                            onOptionsChange = { options = it },
                            previewViews = previewViews,
                        )
                        }
                        Button(
                            onClick = {
                                WidgetStyleStore.set(this@WidgetConfigurationActivity, widgetId, options)
                                lifecycleScope.launch {
                                    refreshSinglenoteWidgets(this@WidgetConfigurationActivity)
                                    setResult(
                                        Activity.RESULT_OK,
                                        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                                    )
                                    finish()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Text(
                                stringResource(
                                    if (isEditingWidget) R.string.widget_config_update
                                    else R.string.widget_config_add
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
