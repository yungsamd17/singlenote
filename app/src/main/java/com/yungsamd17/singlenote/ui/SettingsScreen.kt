package com.yungsamd17.singlenote.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yungsamd17.singlenote.BuildConfig
import com.yungsamd17.singlenote.R
import com.yungsamd17.singlenote.data.NotePreferences
import com.yungsamd17.singlenote.data.NotePreferences.Companion.FONTS
import com.yungsamd17.singlenote.data.NotePreferences.Companion.SIZES
import com.yungsamd17.singlenote.data.NotePreferences.Companion.THEMES

private const val GITHUB_URL = "https://github.com/yungsamd17/singlenote"
private const val LICENSE_URL = "https://github.com/yungsamd17/singlenote/blob/main/LICENSE"
private const val MONONOTE_URL = "https://www.digitalminimalist.com/apps/mononote"

private const val DIALOG_NONE = "none"
private const val DIALOG_THEME = "theme"
private const val DIALOG_FONT = "font"
private const val DIALOG_SIZE = "size"
private const val DIALOG_ABOUT = "about"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val fontFamily by viewModel.fontFamily.collectAsStateWithLifecycle()
    val textSize by viewModel.textSize.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()

    var openDialog by remember { mutableStateOf(DIALOG_NONE) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            SectionHeader(
                icon = Icons.Outlined.Palette,
                title = stringResource(R.string.settings_section_appearance)
            )
            SelectRow(
                icon = null,
                title = stringResource(R.string.settings_theme),
                currentValue = themeLabel(themeMode),
                onClick = { openDialog = DIALOG_THEME }
            )
            SelectRow(
                icon = null,
                title = stringResource(R.string.settings_font),
                currentValue = fontLabel(fontFamily),
                onClick = { openDialog = DIALOG_FONT }
            )
            SelectRow(
                icon = null,
                title = stringResource(R.string.settings_text_size),
                currentValue = sizeLabel(textSize),
                onClick = { openDialog = DIALOG_SIZE }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            SectionHeader(
                icon = Icons.Outlined.Notifications,
                title = stringResource(R.string.settings_section_notifications)
            )
            ToggleRow(
                title = stringResource(R.string.setting_show_notifications),
                subtitle = stringResource(R.string.setting_show_notifications_desc),
                checked = notificationsEnabled,
                onCheckedChange = { viewModel.setNotificationsEnabled(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            SectionHeader(
                icon = Icons.Outlined.Info,
                title = stringResource(R.string.settings_section_about)
            )
            LinkRow(
                icon = Icons.Outlined.Info,
                title = stringResource(R.string.about_title)
            ) { openDialog = DIALOG_ABOUT }
            LinkRow(
                icon = Icons.Outlined.Code,
                title = stringResource(R.string.about_source_code)
            ) { openUrl(context, GITHUB_URL) }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    when (openDialog) {
        DIALOG_THEME -> SelectionDialog(
            title = stringResource(R.string.settings_theme),
            options = THEMES.map { it to themeLabel(it) },
            selected = themeMode,
            onSelect = {
                viewModel.setThemeMode(it)
                openDialog = DIALOG_NONE
            },
            onDismiss = { openDialog = DIALOG_NONE }
        )
        DIALOG_FONT -> SelectionDialog(
            title = stringResource(R.string.settings_font),
            options = FONTS.map { it to fontLabel(it) },
            selected = fontFamily,
            onSelect = {
                viewModel.setFontFamily(it)
                openDialog = DIALOG_NONE
            },
            onDismiss = { openDialog = DIALOG_NONE }
        )
        DIALOG_SIZE -> SelectionDialog(
            title = stringResource(R.string.settings_text_size),
            options = SIZES.map { it to sizeLabel(it) },
            selected = textSize,
            onSelect = {
                viewModel.setTextSize(it)
                openDialog = DIALOG_NONE
            },
            onDismiss = { openDialog = DIALOG_NONE }
        )
        DIALOG_ABOUT -> AboutDialog(onDismiss = { openDialog = DIALOG_NONE })
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun SelectRow(
    icon: ImageVector?,
    title: String,
    currentValue: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = false, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = currentValue,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = stringResource(R.string.change),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun LinkRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = false, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Composable
private fun SelectionDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = key == selected, onClick = { onSelect(key) })
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = key == selected, onClick = null)
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    text = stringResource(R.string.about_privacy_blurb),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    TextButton(onClick = { openUrl(context, GITHUB_URL) }) {
                        Text(stringResource(R.string.about_github))
                    }
                    TextButton(onClick = { openUrl(context, LICENSE_URL) }) {
                        Text(stringResource(R.string.about_license))
                    }
                }
                TextButton(onClick = { openUrl(context, MONONOTE_URL) }) {
                    Icon(Icons.Outlined.Lightbulb, contentDescription = null)
                    Text(
                        text = stringResource(R.string.about_inspired),
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

private fun openUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

@Composable
private fun themeLabel(key: String): String = when (key) {
    NotePreferences.THEME_LIGHT -> stringResource(R.string.theme_light)
    NotePreferences.THEME_DARK -> stringResource(R.string.theme_dark)
    else -> stringResource(R.string.theme_system)
}

@Composable
private fun fontLabel(key: String): String = when (key) {
    NotePreferences.FONT_MONO -> stringResource(R.string.font_mono)
    NotePreferences.FONT_SERIF -> stringResource(R.string.font_serif)
    else -> stringResource(R.string.font_default)
}

@Composable
private fun sizeLabel(key: String): String = when (key) {
    NotePreferences.SIZE_SMALL -> stringResource(R.string.size_small)
    NotePreferences.SIZE_LARGE -> stringResource(R.string.size_large)
    else -> stringResource(R.string.size_medium)
}
