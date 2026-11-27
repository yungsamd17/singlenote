package com.yungsamd17.singlenote.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yungsamd17.singlenote.R
import com.yungsamd17.singlenote.ui.SettingsGroup
import com.yungsamd17.singlenote.ui.SettingsSectionHeader
import com.yungsamd17.singlenote.ui.ToggleRow
import kotlin.math.roundToInt

@Composable
internal fun WidgetSettingRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.padding(start = 20.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun WidgetChoice(
    icon: ImageVector,
    title: String,
    selected: String,
    options: List<Pair<String, Int>>,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    WidgetSettingRow(
        icon,
        title,
        stringResource(options.firstOrNull { it.first == selected }?.second ?: options.first().second),
    ) { open = true }
    if (open)
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                    options.forEach { (key, label) ->
                        Row(
                            Modifier.fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(selected = selected == key, role = Role.RadioButton, onClick = {
                                    onSelect(key)
                                    open = false
                                })
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected == key, onClick = null)
                            Text(stringResource(label), Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) {
                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
}

@Composable
internal fun WidgetSizeChoice(
    icon: ImageVector,
    title: String,
    value: Int,
    range: IntRange,
    format: Int = R.string.widget_size_sp,
    onSave: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var draft by remember { mutableIntStateOf(value) }
    WidgetSettingRow(icon, title, stringResource(format, value)) {
        draft = value
        open = true
    }
    if (open)
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    Text(stringResource(format, draft), style = MaterialTheme.typography.bodyLarge)
                    Slider(
                        value = draft.toFloat(),
                        onValueChange = { draft = it.roundToInt() },
                        valueRange = range.first.toFloat()..range.last.toFloat(),
                        steps = if (range.last - range.first > 40) 0 else range.last - range.first - 1,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onSave(draft); open = false }) {
                    Text(stringResource(R.string.widget_size_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) {
                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
}

/** The widget preview plus the shared appearance and behaviour rows. */
@Composable
internal fun WidgetEditorContent(
    options: WidgetOptions,
    onOptionsChange: (WidgetOptions) -> Unit,
    previewViews: android.widget.RemoteViews? = null,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth(),
    ) {
        header()
        // The in-app screen supplies its own Compose preview; only reserve
        // space for the Glance RemoteViews when there actually is one.
        if (previewViews != null) {
            Spacer(Modifier.height(12.dp))
            AndroidView(
                factory = { android.widget.FrameLayout(it) },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                update = { parent ->
                    parent.removeAllViews()
                    previewViews.apply(parent.context, parent)?.let { parent.addView(it) }
                },
            )
        }
        SettingsSectionHeader(stringResource(R.string.settings_section_appearance))
        SettingsGroup(
            buildList<@Composable () -> Unit> {
                add {
                    WidgetChoice(
                        Icons.Outlined.Palette,
                        stringResource(R.string.settings_theme),
                        options.theme,
                        listOf(
                            "system" to R.string.widget_config_system,
                            "light" to R.string.widget_config_light,
                            "dark" to R.string.widget_config_dark,
                        ),
                    ) { onOptionsChange(options.copy(theme = it)) }
                }
                add {
                    WidgetChoice(
                        Icons.Outlined.FormatPaint,
                        stringResource(R.string.settings_accent),
                        options.accent,
                        listOf(
                            "app" to R.string.widget_follow_app,
                            "default" to R.string.accent_default,
                            "blue" to R.string.accent_blue,
                            "teal" to R.string.accent_teal,
                            "green" to R.string.accent_green,
                            "orange" to R.string.accent_orange,
                            "pink" to R.string.accent_pink,
                        ),
                    ) { onOptionsChange(options.copy(accent = it)) }
                }
                add {
                    WidgetSizeChoice(
                        Icons.Outlined.Opacity,
                        stringResource(R.string.widget_background_opacity),
                        options.opacity,
                        0..100,
                        R.string.widget_opacity_percent,
                    ) { onOptionsChange(options.copy(opacity = it)) }
                }
                add {
                    WidgetSizeChoice(
                        Icons.Outlined.FormatSize,
                        stringResource(R.string.widget_note_size),
                        widgetFontSize(options.size),
                        10..36,
                    ) { onOptionsChange(options.copy(size = it.toString())) }
                }
                add {
                    ToggleRow(
                        Icons.Outlined.Visibility,
                        stringResource(R.string.widget_show_title),
                        "",
                        options.showTitle,
                        { onOptionsChange(options.copy(showTitle = it)) },
                    )
                }
                if (options.showTitle)
                    add {
                        WidgetSizeChoice(
                            Icons.Outlined.Title,
                            stringResource(R.string.widget_title_size),
                            options.titleSize,
                            8..24,
                        ) { onOptionsChange(options.copy(titleSize = it)) }
                    }
            },
        )
        SettingsSectionHeader(stringResource(R.string.widget_section_behavior))
        SettingsGroup(
            listOf({
                WidgetChoice(
                    Icons.Outlined.TouchApp,
                    stringResource(R.string.widget_tap_action),
                    options.action,
                    listOf(
                        "open" to R.string.widget_action_open,
                        "edit" to R.string.widget_action_edit,
                        "archive" to R.string.widget_action_archive,
                    ),
                ) { onOptionsChange(options.copy(action = it)) }
            }),
        )
        Spacer(Modifier.height(32.dp))
    }
}
