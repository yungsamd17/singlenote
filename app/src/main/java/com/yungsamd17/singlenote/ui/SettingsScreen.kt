package com.yungsamd17.singlenote.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yungsamd17.singlenote.R
import com.yungsamd17.singlenote.data.NotePreferences.Companion.FONTS
import com.yungsamd17.singlenote.data.NotePreferences.Companion.SIZES
import com.yungsamd17.singlenote.data.NotePreferences.Companion.THEMES

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val fontFamily by viewModel.fontFamily.collectAsStateWithLifecycle()
    val textSize by viewModel.textSize.collectAsStateWithLifecycle()

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
            SettingsGroup(title = stringResource(R.string.settings_theme)) {
                THEMES.forEach { option ->
                    RadioRow(
                        selected = themeMode == option,
                        label = themeLabel(option),
                        onClick = { viewModel.setThemeMode(option) }
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SettingsGroup(title = stringResource(R.string.settings_font)) {
                FONTS.forEach { option ->
                    RadioRow(
                        selected = fontFamily == option,
                        label = fontLabel(option),
                        onClick = { viewModel.setFontFamily(option) }
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SettingsGroup(title = stringResource(R.string.settings_text_size)) {
                SIZES.forEach { option ->
                    RadioRow(
                        selected = textSize == option,
                        label = sizeLabel(option),
                        onClick = { viewModel.setTextSize(option) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
    }
}

@Composable
private fun RadioRow(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun themeLabel(key: String): String = when (key) {
    "light" -> stringResource(R.string.theme_light)
    "dark" -> stringResource(R.string.theme_dark)
    else -> stringResource(R.string.theme_system)
}

@Composable
private fun fontLabel(key: String): String = when (key) {
    "mono" -> stringResource(R.string.font_mono)
    "serif" -> stringResource(R.string.font_serif)
    else -> stringResource(R.string.font_default)
}

@Composable
private fun sizeLabel(key: String): String = when (key) {
    "small" -> stringResource(R.string.size_small)
    "large" -> stringResource(R.string.size_large)
    else -> stringResource(R.string.size_medium)
}
