package com.yungsamd17.singlenote.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.math.roundToInt
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.FormatPaint
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.core.view.WindowCompat
import com.yungsamd17.singlenote.ui.accentScheme
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yungsamd17.singlenote.data.NotePreferences
import com.yungsamd17.singlenote.R
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

internal data class WidgetOptions(
    val theme: String = "system",
    val accent: String = "app",
    val size: String = "medium",
    val action: String = "open",
    val titleSize: Int = 12,
    val showTitle: Boolean = true,
    val opacity: Int = 100,
)

internal object WidgetStyleStore {
    private const val PREFS = "widget_styles"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun exists(context: Context, id: Int): Boolean = prefs(context).contains(id.toString())

    fun get(context: Context, id: Int): WidgetOptions {
        val prefs = prefs(context)
        val key = id.toString()
        return WidgetOptions(
            theme = prefs.getString(key, "system") ?: "system",
            accent = prefs.getString("$key.accent", "app") ?: "app",
            size = prefs.getString("$key.size", "medium") ?: "medium",
            action = prefs.getString("$key.action", "open") ?: "open",
            titleSize = prefs.getInt("$key.titleSize", 12).coerceIn(8, 24),
            showTitle = prefs.getBoolean("$key.showTitle", true),
            opacity = prefs.getInt("$key.opacity", 100).coerceIn(0, 100),
        )
    }

    fun set(context: Context, id: Int, options: WidgetOptions) {
        val key = id.toString()
        prefs(context).edit()
            .putString(key, options.theme)
            .putString("$key.accent", options.accent)
            .putString("$key.size", options.size)
            .putString("$key.action", options.action)
            .putInt("$key.titleSize", options.titleSize)
            .putBoolean("$key.showTitle", options.showTitle)
            .putInt("$key.opacity", options.opacity)
            .apply()
    }

    fun remove(context: Context, id: Int) {
        val key = id.toString()
        prefs(context).edit()
            .remove(key)
            .remove("$key.accent")
            .remove("$key.size")
            .remove("$key.action")
            .remove("$key.titleSize")
            .remove("$key.showTitle")
            .remove("$key.opacity")
            .apply()
    }

    fun observe(context: Context, id: Int) = callbackFlow {
        val prefs = prefs(context)
        val key = id.toString()
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == key || changed?.startsWith("$key.") == true) trySend(get(context, id))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(get(context, id))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}

@Composable
private fun WidgetSettingRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.padding(start = 20.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun WidgetChoice(icon: ImageVector, title: String, selected: String, options: List<Pair<String, Int>>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    WidgetSettingRow(icon, title, stringResource(options.firstOrNull { it.first == selected }?.second ?: options.first().second)) { open = true }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (key, label) ->
                    Row(Modifier.fillMaxWidth().selectable(selected = selected == key, role = Role.RadioButton, onClick = { onSelect(key); open = false }), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected == key, onClick = null)
                        Text(stringResource(label), Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun WidgetSizeChoice(icon: ImageVector, title: String, value: Int, range: IntRange, format: Int = R.string.widget_size_sp, onSave: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var draft by remember { mutableIntStateOf(value) }
    WidgetSettingRow(icon, title, stringResource(format, value)) { draft = value; open = true }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(title) },
        text = {
            Column {
                Text(stringResource(format, draft), style = MaterialTheme.typography.bodyLarge)
                Slider(value = draft.toFloat(), onValueChange = { draft = it.roundToInt() }, valueRange = range.first.toFloat()..range.last.toFloat(), steps = if (range.last - range.first > 40) 0 else range.last - range.first - 1)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft); open = false }) { Text(stringResource(R.string.widget_size_save)) } },
        dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } }
    )
}

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class WidgetConfigurationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID ||
            AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider !=
            android.content.ComponentName(this, SinglenoteWidgetReceiver::class.java)
        ) { finish(); return }
        val isEditingWidget = WidgetStyleStore.exists(this, widgetId)
        setContent {
            val systemDark = isSystemInDarkTheme()
            val appAccent by NotePreferences.get(applicationContext).accentColor.collectAsStateWithLifecycle(initialValue = NotePreferences.ACCENT_DEFAULT)
            val appTheme by NotePreferences.get(applicationContext).themeMode.collectAsStateWithLifecycle(initialValue = NotePreferences.THEME_SYSTEM)
            val appDark = when (appTheme) {
                NotePreferences.THEME_LIGHT -> false
                NotePreferences.THEME_DARK -> true
                else -> systemDark
            }
            val scheme = if (appAccent == NotePreferences.ACCENT_SYSTEM && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (appDark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
            } else accentScheme(appAccent, appDark)
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !appDark
                    isAppearanceLightNavigationBars = !appDark
                }
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(scheme.background.toArgb()))
            }
            var options by remember { mutableStateOf(WidgetStyleStore.get(this, widgetId)) }
            val previewText = stringResource(R.string.widget_preview_note)
            val brand = stringResource(R.string.app_name)
            val renderer = remember { GlanceRemoteViews() }
            var previewViews by remember { mutableStateOf<android.widget.RemoteViews?>(null) }
            LaunchedEffect(options, appAccent, previewText, brand) {
                previewViews = renderer.compose(
                    context = this@WidgetConfigurationActivity,
                    size = DpSize(320.dp, 120.dp)
                ) {
                    WidgetContent(
                        openIntent = null,
                        content = previewText,
                        fallbackHint = previewText,
                        style = options,
                        appAccent = appAccent,
                        context = this@WidgetConfigurationActivity,
                        brand = brand
                    )
                }.remoteViews
            }
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp)) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.widget_config_title), style = MaterialTheme.typography.headlineSmall)
                            Text(stringResource(R.string.widget_config_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            AndroidView(
                                factory = { android.widget.FrameLayout(it) },
                                modifier = Modifier.fillMaxWidth().height(120.dp),
                                update = { parent ->
                                    parent.removeAllViews()
                                    previewViews?.apply(parent.context, parent)?.let { parent.addView(it) }
                                }
                            )
                            Text(stringResource(R.string.settings_section_appearance), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                            WidgetChoice(Icons.Outlined.Palette, stringResource(R.string.settings_theme), options.theme, listOf("system" to R.string.widget_config_system, "light" to R.string.widget_config_light, "dark" to R.string.widget_config_dark)) { options = options.copy(theme = it) }
                            WidgetChoice(Icons.Outlined.FormatPaint, stringResource(R.string.settings_accent), options.accent, listOf("app" to R.string.widget_follow_app, "default" to R.string.accent_default, "blue" to R.string.accent_blue, "teal" to R.string.accent_teal, "green" to R.string.accent_green, "orange" to R.string.accent_orange, "pink" to R.string.accent_pink, "system" to R.string.accent_system)) { options = options.copy(accent = it) }
                            WidgetSizeChoice(Icons.Outlined.Opacity, stringResource(R.string.widget_background_opacity), options.opacity, 0..100, R.string.widget_opacity_percent) { options = options.copy(opacity = it) }
                            WidgetSizeChoice(Icons.Outlined.FormatSize, stringResource(R.string.widget_note_size), widgetFontSize(options.size), 10..36) { options = options.copy(size = it.toString()) }
                            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(stringResource(R.string.widget_show_title), modifier = Modifier.weight(1f).padding(start = 20.dp), style = MaterialTheme.typography.bodyLarge)
                                    Switch(checked = options.showTitle, onCheckedChange = { options = options.copy(showTitle = it) })
                                }
                            }
                            if (options.showTitle) WidgetSizeChoice(Icons.Outlined.Title, stringResource(R.string.widget_title_size), options.titleSize, 8..24) { options = options.copy(titleSize = it) }
                            Text(stringResource(R.string.widget_section_behavior), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                            WidgetChoice(Icons.Outlined.TouchApp, stringResource(R.string.widget_tap_action), options.action, listOf("open" to R.string.widget_action_open, "edit" to R.string.widget_action_edit, "archive" to R.string.widget_action_archive)) { options = options.copy(action = it) }
                        }
                        Button(onClick = {
                            WidgetStyleStore.set(this@WidgetConfigurationActivity, widgetId, options)
                            lifecycleScope.launch {
                                SinglenoteWidget().update(this@WidgetConfigurationActivity, GlanceAppWidgetManager(this@WidgetConfigurationActivity).getGlanceIdBy(widgetId))
                                setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                                finish()
                            }
                        }, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                            Text(stringResource(if (isEditingWidget) R.string.widget_config_update else R.string.widget_config_add))
                        }
                    }
                }
            }
        }
    }
}
