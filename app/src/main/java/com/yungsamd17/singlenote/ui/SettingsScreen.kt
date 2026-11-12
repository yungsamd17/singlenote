package com.yungsamd17.singlenote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yungsamd17.singlenote.R
import com.yungsamd17.singlenote.data.NotePreferences
import com.yungsamd17.singlenote.data.NotePreferences.Companion.ACCENTS
import com.yungsamd17.singlenote.data.NotePreferences.Companion.FONTS
import com.yungsamd17.singlenote.data.NotePreferences.Companion.SIZES
import com.yungsamd17.singlenote.data.NotePreferences.Companion.THEMES
import com.yungsamd17.singlenote.util.AppLanguage

/** Shared rounded settings cards: callers provide row content, never nested cards. */
@Composable
fun SettingsGroup(
    items: List<@Composable () -> Unit>,
    colors: @Composable (Int) -> androidx.compose.material3.CardColors = {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    },
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            Card(
                shape = RoundedCornerShape(
                    topStart = if (index == 0) 28.dp else 4.dp,
                    topEnd = if (index == 0) 28.dp else 4.dp,
                    bottomStart = if (index == items.lastIndex) 28.dp else 4.dp,
                    bottomEnd = if (index == items.lastIndex) 28.dp else 4.dp,
                ),
                colors = colors(index),
                modifier = Modifier.fillMaxWidth(),
            ) {
                item()
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        // Section labels are what TalkBack users navigate by, so expose
        // them as headings rather than plain text nodes.
        modifier = Modifier
            .padding(start = 12.dp, top = 24.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    val scroll = rememberSettingsScrollState()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            // Centered like the note and archive toolbars: the title sits in
            // the middle, and the bar stays put. It never collapses on
            // scroll, so the page keeps its frame while the content moves.
            CenterAlignedTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    TooltipIconButton(
                        onClick = onBack,
                        tooltip = stringResource(R.string.cd_back),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize()
                .padding(padding)
                .onSizeChanged { scroll.viewportHeight = it.height }
                .verticalScroll(scroll.content, enabled = scroll.overflows)
                .wrapContentHeight(align = Alignment.Top)
                .onSizeChanged { scroll.contentHeight = it.height }
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            content()
        }
    }
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenScreen: (String) -> Unit,
    screen: String = "settings",
) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val theme by viewModel.themeMode.collectAsStateWithLifecycle()
    val accent by viewModel.accentColor.collectAsStateWithLifecycle()
    val font by viewModel.fontFamily.collectAsStateWithLifecycle()
    val size by viewModel.textSize.collectAsStateWithLifecycle()
    val notifications by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val lockscreen by viewModel.lockscreenVisible.collectAsStateWithLifecycle()
    val titleFirstLine by viewModel.notificationTitleFirstLine.collectAsStateWithLifecycle()
    val dynamic by viewModel.dynamicColors.collectAsStateWithLifecycle()
    val amoled by viewModel.amoled.collectAsStateWithLifecycle()
    val monochrome by viewModel.monochrome.collectAsStateWithLifecycle()
    val appFont by viewModel.appFont.collectAsStateWithLifecycle()
    val appIcon by viewModel.appIcon.collectAsStateWithLifecycle()
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf("") }
    if (!ready) return
    val title =
        when (screen) {
            "appearance" -> R.string.settings_section_appearance
            "typography" -> R.string.settings_typography
            "language" -> R.string.settings_language
            "notifications" -> R.string.settings_section_notifications
            else -> R.string.settings_title
        }
    SettingsPage(stringResource(title), onBack) {
        when (screen) {
            "settings" -> {
                SettingsGroup(
                    listOf(
                        {
                            ValueRow(
                                Icons.Outlined.Palette,
                                stringResource(R.string.settings_section_appearance),
                                stringResource(R.string.settings_appearance_summary),
                                { onOpenScreen("appearance") },
                            )
                        },
                        {
                            ValueRow(
                                Icons.Outlined.FontDownload,
                                stringResource(R.string.settings_typography),
                                stringResource(R.string.settings_typography_summary),
                                { onOpenScreen("typography") },
                            )
                        },
                        {
                            ValueRow(
                                Icons.Outlined.Language,
                                stringResource(R.string.settings_language),
                                if (AppLanguage.getLanguageTag(context).isBlank())
                                    stringResource(R.string.theme_system)
                                else languageLabel(AppLanguage.getLanguageTag(context)),
                                { onOpenScreen("language") },
                            )
                        },
                    )
                )
                androidx.compose.foundation.layout.Spacer(Modifier.size(16.dp))
                SettingsGroup(
                    listOf({
                        ValueRow(
                            Icons.Outlined.Notifications,
                            stringResource(R.string.settings_section_notifications),
                            stringResource(R.string.settings_notifications_summary),
                            { onOpenScreen("notifications") },
                        )
                    })
                )
                androidx.compose.foundation.layout.Spacer(Modifier.size(16.dp))
                SettingsGroup(
                    listOf({
                        ValueRow(
                            Icons.Outlined.Info,
                            stringResource(R.string.about_title),
                            stringResource(R.string.about_subtitle),
                            { onOpenScreen("about") },
                        )
                    })
                )
            }
            "appearance" -> {
                SettingsSectionHeader(stringResource(R.string.settings_app_looks))
                SettingsGroup(
                    listOf(
                        {
                            ValueRow(
                                Icons.Outlined.Palette,
                                stringResource(R.string.settings_theme),
                                themeLabel(theme),
                                { dialog = "theme" },
                            )
                        },
                        {
                            ValueRow(
                                painterResource(R.drawable.ic_format_paint),
                                stringResource(R.string.settings_accent),
                                accentLabel(accent),
                                { dialog = "accent" },
                                // Monochrome replaces every color: the accent
                                // (and the wallpaper colors) no longer apply.
                                enabled = !dynamic && !monochrome,
                            )
                        },
                        {
                            ToggleRow(
                                painterResource(R.drawable.ic_symbol_image),
                                stringResource(R.string.settings_dynamic),
                                stringResource(R.string.settings_dynamic_summary),
                                dynamic,
                                viewModel::setDynamicColors,
                                summaryAccent = true,
                                enabled = android.os.Build.VERSION.SDK_INT >= 31 && !monochrome,
                            )
                        },
                        {
                            ToggleRow(
                                painterResource(R.drawable.ic_symbol_monochrome),
                                stringResource(R.string.settings_monochrome),
                                stringResource(R.string.settings_monochrome_summary),
                                monochrome,
                                viewModel::setMonochrome,
                                summaryAccent = true,
                            )
                        },
                        {
                            ToggleRow(
                                painterResource(R.drawable.ic_symbol_moon_stars),
                                stringResource(R.string.settings_amoled),
                                stringResource(R.string.settings_amoled_summary),
                                amoled,
                                viewModel::setAmoled,
                                summaryAccent = true,
                            )
                        },
                    )
                )
                SettingsSectionHeader(stringResource(R.string.settings_app_icon))
                SettingsGroup(
                    listOf({
                        ValueRow(
                            painterResource(R.drawable.ic_symbol_app_badging),
                            stringResource(R.string.settings_app_icon),
                            stringResource(
                                when (appIcon) { "accent" -> R.string.settings_icon_accent; "dynamic" -> R.string.settings_icon_dynamic; else -> R.string.accent_default }
                            ),
                            { dialog = "icon" },
                        )
                    })
                )
            }
            "typography" -> {
                SettingsSectionHeader(stringResource(R.string.settings_font_category))
                SettingsGroup(
                    listOf(
                        {
                            ValueRow(
                                Icons.Outlined.FontDownload,
                                stringResource(R.string.settings_app_font),
                                stringResource(
                                    if (appFont == "flex") R.string.font_google_flex
                                    else R.string.font_system
                                ),
                                { dialog = "appFont" },
                            )
                        },
                        {
                            ValueRow(
                                painterResource(R.drawable.ic_symbol_serif),
                                stringResource(R.string.settings_font),
                                fontLabel(font),
                                { dialog = "font" },
                            )
                        },
                    )
                )
                SettingsSectionHeader(stringResource(R.string.settings_size_category))
                SettingsGroup(
                    listOf({
                        ValueRow(
                            Icons.Outlined.FormatSize,
                            stringResource(R.string.settings_text_size),
                            sizeLabel(size),
                            { dialog = "size" },
                        )
                    })
                )
            }
            "notifications" -> {
                SettingsSectionHeader(stringResource(R.string.settings_note_notification))
                SettingsGroup(
                    listOf(
                        {
                            ToggleRow(
                                Icons.Outlined.Notifications,
                                stringResource(R.string.setting_show_notifications),
                                stringResource(R.string.setting_show_notifications_desc),
                                notifications,
                                viewModel::setNotificationsEnabled,
                            )
                        },
                        {
                            ToggleRow(
                                painterResource(R.drawable.ic_symbol_notifications_unread),
                                stringResource(R.string.setting_lockscreen_visible),
                                stringResource(R.string.setting_lockscreen_visible_desc),
                                lockscreen,
                                viewModel::setLockscreenVisible,
                                enabled = notifications,
                            )
                        },
                        {
                            ToggleRow(
                                Icons.Outlined.Title,
                                stringResource(R.string.setting_notification_title_first_line),
                                stringResource(R.string.setting_notification_title_first_line_desc),
                                titleFirstLine,
                                viewModel::setNotificationTitleFirstLine,
                                enabled = notifications,
                            )
                        },
                    )
                )
            }
            "language" -> {
                SettingsSectionHeader(stringResource(R.string.settings_contribute))
                SettingsGroup(
                    listOf({
                        ValueRow(
                            Icons.Outlined.Language,
                            stringResource(R.string.settings_translate),
                            stringResource(R.string.settings_translate_summary),
                            {
                                uri.openUri(
                                    "https://github.com/yungsamd17/singlenote/blob/main/docs/TRANSLATE-APP.md"
                                )
                            },
                        )
                    })
                )
                SettingsSectionHeader(stringResource(R.string.settings_app_language))
                val selected = AppLanguage.getLanguageTag(context)
                Column(Modifier.selectableGroup()) {
                    SettingsGroup(
                        languageEntries.map { entry ->
                            {
                                val checked = selected == entry.code
                                Row(
                                    Modifier.fillMaxWidth()
                                        .background(
                                            if (checked)
                                                MaterialTheme.colorScheme.secondaryContainer
                                            else MaterialTheme.colorScheme.surfaceContainer
                                        )
                                        .selectable(
                                            checked,
                                            role = Role.RadioButton,
                                            onClick = {
                                                AppLanguage.setLanguage(context, entry.code)
                                            },
                                        )
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    LanguageBadge(entry)
                                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                        Text(
                                            text = stringResource(entry.englishName),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Text(
                                            text = stringResource(entry.nativeName),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    RadioButton(
                                        selected = checked,
                                        onClick = null,
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = MaterialTheme.colorScheme.primary,
                                            unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
    val options =
        when (dialog) {
            "theme" -> THEMES.map { it to themeLabel(it) }
            "accent" -> ACCENTS.map { it to accentLabel(it) }
            "font" -> FONTS.map { it to fontLabel(it) }
            "size" -> SIZES.map { it to sizeLabel(it) }
            "appFont" ->
                listOf(
                    "system" to stringResource(R.string.font_system),
                    "flex" to stringResource(R.string.font_google_flex),
                )
            "icon" ->
                listOf(
                    "default" to stringResource(R.string.accent_default),
                    "accent" to stringResource(R.string.settings_icon_accent),
                ) + if (android.os.Build.VERSION.SDK_INT >= 31) listOf("dynamic" to stringResource(R.string.settings_icon_dynamic)) else emptyList()
            else -> emptyList()
        }
    if (options.isNotEmpty())
        SelectionDialog(
            title =
                stringResource(
                    when (dialog) {
                        "theme" -> R.string.settings_theme
                        "accent" -> R.string.settings_accent
                        "font" -> R.string.settings_font
                        "size" -> R.string.settings_text_size
                        "appFont" -> R.string.settings_app_font
                        else -> R.string.settings_app_icon
                    }
                ),
            options = options,
            notice = when (dialog) {
                // Changing the accent repaints the launcher icon as well, so
                // both pickers warn that the app closes itself.
                "accent" ->
                    if (appIcon == "accent") {
                        stringResource(R.string.accent_icon_restart_notice)
                    } else {
                        null
                    }
                "icon" -> stringResource(R.string.app_icon_restart_notice)
                else -> null
            },
            selected =
                when (dialog) {
                    "theme" -> theme
                    "accent" -> accent
                    "font" -> font
                    "size" -> size
                    "appFont" -> appFont
                    else -> appIcon
                },
            onSelect = { value ->
                when (dialog) {
                    "theme" -> viewModel.setThemeMode(value)
                    "accent" -> viewModel.setAccentColor(value)
                    "font" -> viewModel.setFontFamily(value)
                    "size" -> viewModel.setTextSize(value)
                    "appFont" -> viewModel.setAppFont(value)
                    "icon" -> viewModel.setAppIcon(value)
                }
                dialog = ""
            },
            onDismiss = { dialog = "" },
        )
}

/**
 * Round badge in front of a language row: the region code of the locale
 * (`en-US` → `US`) like the language pickers of other apps, or a language
 * glyph for the device row, which has no locale of its own.
 */
@Composable
private fun LanguageBadge(entry: LanguageEntry) {
    val region = entry.code.substringAfter('-', "")
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (region.isEmpty()) {
            Icon(
                Icons.Outlined.Language,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = region,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal data class LanguageEntry(
    val code: String,
    /**
     * Name of the language in English, shown on the first line: every reader
     * recognises "German" even when they cannot read "Deutsch".
     */
    val englishName: Int,
    /**
     * Native name of the language, shown on the second line, so the row also
     * shows the language as its own speakers write it.
     */
    val nativeName: Int,
)

/**
 * Rows of the language picker. The device row comes first because it is not a
 * language but the "follow the system" choice; English follows it, and the
 * rest are alphabetical by their English name so the list can be scanned
 * without reading every native spelling.
 */
internal val languageEntries =
    listOf(
        // The device row keeps the same two lines as the others: "System" as
        // the name and the app following the device language underneath.
        LanguageEntry("", R.string.theme_system, R.string.settings_device_language),
        LanguageEntry("en-US", R.string.language_english_us, R.string.language_english),
        LanguageEntry("zh-CN", R.string.language_chinese_english, R.string.language_chinese),
        LanguageEntry("nl-NL", R.string.language_dutch_english, R.string.language_dutch),
        LanguageEntry("fr-FR", R.string.language_french_english, R.string.language_french),
        LanguageEntry("de-DE", R.string.language_german_english, R.string.language_german),
        LanguageEntry("it-IT", R.string.language_italian_english, R.string.language_italian),
        LanguageEntry("pl-PL", R.string.language_polish_english, R.string.language_polish),
        LanguageEntry("pt-BR", R.string.language_portuguese_english, R.string.language_portuguese),
        LanguageEntry("ru-RU", R.string.language_russian_english, R.string.language_russian),
        LanguageEntry("sk-SK", R.string.language_slovak_english, R.string.language_slovak),
        LanguageEntry("es-ES", R.string.language_spanish_english, R.string.language_spanish),
        LanguageEntry("tr-TR", R.string.language_turkish_english, R.string.language_turkish),
    )

@Composable
internal fun ValueRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    ValueRow(
        icon = rememberVectorPainter(icon),
        title = title,
        value = value,
        onClick = onClick,
        enabled = enabled,
    )
}

@Composable
internal fun ValueRow(
    icon: Painter,
    title: String,
    value: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .alpha(if (enabled) 1f else 0.38f)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.padding(start = 20.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
internal fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    ToggleRow(rememberVectorPainter(icon), title, subtitle, checked, onCheckedChange, enabled)
}

@Composable
internal fun ToggleRow(
    icon: Painter, title: String, subtitle: String, checked: Boolean,
    onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true, summaryAccent: Boolean = false,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    enabled = enabled,
                    onValueChange = onCheckedChange,
                )
                .alpha(if (enabled) 1f else 0.38f)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f).padding(start = 20.dp, end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotEmpty())
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (summaryAccent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            thumbContent = {
                Icon(
                    imageVector = if (checked) Icons.Filled.Check else Icons.Outlined.Close,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            },
        )
    }
}

@Composable
private fun SelectionDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    notice: String? = null,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                if (notice != null) Text(notice, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp))
                options.forEach { (key, label) ->
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = key == selected,
                                    onClick = { onSelect(key) },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = key == selected, onClick = null)
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
    )
}

@Composable
private fun themeLabel(key: String): String =
    when (key) {
        NotePreferences.THEME_LIGHT -> stringResource(R.string.theme_light)
        NotePreferences.THEME_DARK -> stringResource(R.string.theme_dark)
        else -> stringResource(R.string.theme_system)
    }

@Composable
private fun accentLabel(key: String): String =
    when (key) {
        NotePreferences.ACCENT_BLUE -> stringResource(R.string.accent_blue)
        NotePreferences.ACCENT_TEAL -> stringResource(R.string.accent_teal)
        NotePreferences.ACCENT_GREEN -> stringResource(R.string.accent_green)
        NotePreferences.ACCENT_ORANGE -> stringResource(R.string.accent_orange)
        NotePreferences.ACCENT_PINK -> stringResource(R.string.accent_pink)
        else -> stringResource(R.string.accent_default)
    }

@Composable
private fun fontLabel(key: String): String =
    when (key) {
        NotePreferences.FONT_MONO -> stringResource(R.string.font_mono)
        NotePreferences.FONT_SERIF -> stringResource(R.string.font_serif)
        else -> stringResource(R.string.font_default)
    }

@Composable
private fun sizeLabel(key: String): String =
    when (key) {
        NotePreferences.SIZE_SMALL -> stringResource(R.string.size_small)
        NotePreferences.SIZE_LARGE -> stringResource(R.string.size_large)
        else -> stringResource(R.string.size_medium)
    }

@Composable
private fun languageLabel(languageTag: String): String =
    stringResource(
        // The same name the picker shows on the first line, so the hub and the
        // list never disagree about which language is active.
        languageEntries.firstOrNull { it.code == languageTag }?.englishName ?: R.string.theme_system
    )
