package com.yungsamd17.singlenote.ui

import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yungsamd17.singlenote.R
import com.yungsamd17.singlenote.data.NotePreferences
import com.yungsamd17.singlenote.data.NotePreferences.Companion.ACCENTS
import com.yungsamd17.singlenote.data.NotePreferences.Companion.FONTS
import com.yungsamd17.singlenote.data.NotePreferences.Companion.SIZES
import com.yungsamd17.singlenote.data.NotePreferences.Companion.THEMES

private const val DIALOG_NONE = "none"
private const val DIALOG_THEME = "theme"
private const val DIALOG_ACCENT = "accent"
private const val DIALOG_FONT = "font"
private const val DIALOG_SIZE = "size"

// Post-gate entrance: fast and subtle, just enough to avoid a pop-in.
private const val APPEAR_MS = 150

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val accentColor by viewModel.accentColor.collectAsStateWithLifecycle()
    val fontFamily by viewModel.fontFamily.collectAsStateWithLifecycle()
    val textSize by viewModel.textSize.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val lockscreenVisible by viewModel.lockscreenVisible.collectAsStateWithLifecycle()
    val ready by viewModel.ready.collectAsStateWithLifecycle()

    var openDialog by remember { mutableStateOf(DIALOG_NONE) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
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
        // First frame waits for stored truth (same as the note screen):
        // rows appear with the saved values — nothing flashes defaults.
        // A labelled spinner keeps TalkBack informed instead of silence.
        if (!ready) {
            val loadingLabel = stringResource(R.string.loading)
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.semantics {
                        contentDescription = loadingLabel
                    }
                )
            }
            return@Scaffold
        }
        // Subtle entrance after the blank gate: a fast fade with a small
        // rise so the rows arrive together instead of popping in.
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(APPEAR_MS)) + slideInVertically(
                tween(APPEAR_MS)
            ) { it / 16 },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SectionLabel(text = stringResource(R.string.settings_section_appearance))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingCard {
                    ValueRow(
                        icon = Icons.Outlined.Palette,
                        title = stringResource(R.string.settings_theme),
                        value = themeLabel(themeMode),
                        onClick = { openDialog = DIALOG_THEME }
                    )
                }
                SettingCard {
                    ValueRow(
                        icon = painterResource(R.drawable.ic_format_paint),
                        title = stringResource(R.string.settings_accent),
                        value = accentLabel(accentColor),
                        onClick = { openDialog = DIALOG_ACCENT }
                    )
                }
                SettingCard {
                    ValueRow(
                        icon = Icons.Outlined.FontDownload,
                        title = stringResource(R.string.settings_font),
                        value = fontLabel(fontFamily),
                        onClick = { openDialog = DIALOG_FONT }
                    )
                }
                SettingCard {
                    ValueRow(
                        icon = Icons.Outlined.FormatSize,
                        title = stringResource(R.string.settings_text_size),
                        value = sizeLabel(textSize),
                        onClick = { openDialog = DIALOG_SIZE }
                    )
                }
            }

            SectionLabel(text = stringResource(R.string.settings_section_notifications))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingCard {
                    ToggleRow(
                        icon = Icons.Outlined.Notifications,
                        title = stringResource(R.string.setting_show_notifications),
                        subtitle = stringResource(R.string.setting_show_notifications_desc),
                        checked = notificationsEnabled,
                        onCheckedChange = { viewModel.setNotificationsEnabled(it) }
                    )
                }
                SettingCard {
                    ToggleRow(
                        icon = Icons.Outlined.Notifications,
                        title = stringResource(R.string.setting_lockscreen_visible),
                        subtitle = stringResource(R.string.setting_lockscreen_visible_desc),
                        checked = lockscreenVisible,
                        onCheckedChange = { viewModel.setLockscreenVisible(it) }
                    )
                }
            }

            SectionLabel(text = stringResource(R.string.settings_section_about))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingCard {
                    ValueRow(
                        icon = Icons.Outlined.Info,
                        title = stringResource(R.string.about_title),
                        value = stringResource(R.string.about_subtitle),
                        onClick = onOpenAbout
                    )
                }
            }
        }
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
        DIALOG_ACCENT -> SelectionDialog(
            title = stringResource(R.string.settings_accent),
            options = ACCENTS.map { it to accentLabel(it) },
            selected = accentColor,
            onSelect = {
                viewModel.setAccentColor(it)
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
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    // No inner padding: the row below fills the card edge to edge, so the
    // touch ripple covers the full card exactly like About rows do.
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column { content() }
    }
}

@Composable
private fun ValueRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    ValueRow(
        icon = rememberVectorPainter(icon),
        title = title,
        value = value,
        onClick = onClick
    )
}

@Composable
private fun ValueRow(
    icon: Painter,
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.padding(start = 20.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 20.dp, end = 12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            thumbContent = {
                Icon(
                    imageVector = if (checked) Icons.Filled.Check else Icons.Outlined.Close,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize)
                )
            }
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
            Column(Modifier.selectableGroup()) {
                options.forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected = key == selected,
                                onClick = { onSelect(key) },
                                role = Role.RadioButton
                            )
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
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun themeLabel(key: String): String = when (key) {
    NotePreferences.THEME_LIGHT -> stringResource(R.string.theme_light)
    NotePreferences.THEME_DARK -> stringResource(R.string.theme_dark)
    else -> stringResource(R.string.theme_system)
}

@Composable
private fun accentLabel(key: String): String = when (key) {
    NotePreferences.ACCENT_BLUE -> stringResource(R.string.accent_blue)
    NotePreferences.ACCENT_TEAL -> stringResource(R.string.accent_teal)
    NotePreferences.ACCENT_GREEN -> stringResource(R.string.accent_green)
    NotePreferences.ACCENT_ORANGE -> stringResource(R.string.accent_orange)
    NotePreferences.ACCENT_PINK -> stringResource(R.string.accent_pink)
    else -> stringResource(R.string.accent_default)
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
