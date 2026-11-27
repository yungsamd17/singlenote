package com.yungsamd17.singlenote.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yungsamd17.singlenote.R
import com.yungsamd17.singlenote.util.BACKUP_FILE_NAME
import com.yungsamd17.singlenote.util.BACKUP_MAX_BYTES
import com.yungsamd17.singlenote.util.BACKUP_NOTES_FILE_NAME
import com.yungsamd17.singlenote.util.BackupSelection
import com.yungsamd17.singlenote.util.RestoreSelection
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    viewModel: BackupViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    // With no notes anywhere there is nothing to merge or replace, so the
    // restore dialog drops those choices entirely (until counts have loaded).
    val hasNotes = !preview.loaded || preview.activeNote || preview.archivedCount > 0
    val snackbarHostState = remember { SnackbarHostState() }

    var includeActive by rememberSaveable { mutableStateOf(true) }
    var includeArchived by rememberSaveable { mutableStateOf(true) }
    var includeSettings by rememberSaveable { mutableStateOf(true) }
    var includeWidgets by rememberSaveable { mutableStateOf(true) }

    var restoreNotes by rememberSaveable { mutableStateOf(true) }
    var restoreSettings by rememberSaveable { mutableStateOf(true) }
    var restoreWidgets by rememberSaveable { mutableStateOf(true) }
    var replaceNotes by rememberSaveable { mutableStateOf(false) }

    var showCreate by remember { mutableStateOf(false) }
    var showRestore by remember { mutableStateOf(false) }
    var showNotesExport by remember { mutableStateOf(false) }
    var showNotesImport by remember { mutableStateOf(false) }
    var exportJson by rememberSaveable { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var importMode by rememberSaveable { mutableStateOf("full") }

    val saved = stringResource(R.string.backup_saved)
    val saveFailed = stringResource(R.string.backup_save_failed)
    val restored = stringResource(R.string.backup_restored)
    val settingsRestored = stringResource(R.string.backup_restored_settings)
    val widgetSaved = stringResource(R.string.widget_saved)
    val nothingRestored = stringResource(R.string.backup_nothing_restored)
    val restoreFailed = stringResource(R.string.backup_restore_failed)
    val tooLarge = stringResource(R.string.backup_too_large, BACKUP_MAX_BYTES / (1024 * 1024))

    LaunchedEffect(Unit) { viewModel.refresh() }

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            val content = viewModel.pendingExport
            viewModel.pendingExport = null
            if (uri != null && content != null) scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {
                            it.write(content)
                        } != null
                    } catch (_: IOException) {
                        false
                    } catch (_: SecurityException) {
                        false
                    }
                }
                snackbarHostState.showSnackbar(if (ok) saved else saveFailed)
            }
        }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) scope.launch {
                val raw = withContext(Dispatchers.IO) {
                    try {
                        readDocument(context, uri)
                    } catch (_: Exception) {
                        null
                    }
                }
                when {
                    raw == null -> snackbarHostState.showSnackbar(restoreFailed)
                    raw.length > BACKUP_MAX_BYTES -> snackbarHostState.showSnackbar(tooLarge)
                    else -> {
                        val selection =
                            if (importMode == "notes") {
                                RestoreSelection(notes = true, appSettings = false, widgetSettings = false)
                            } else {
                                RestoreSelection(
                                    restoreNotes,
                                    restoreSettings,
                                    restoreWidgets,
                                    replaceNotes && hasNotes,
                                )
                            }
                        val result =
                            try {
                                withContext(Dispatchers.IO) { viewModel.restore(raw, selection) }
                            } catch (_: Exception) {
                                null
                            }
                        val message =
                            if (result == null) {
                                restoreFailed
                            } else {
                                val noNotes = result.archivedAdded == 0 && result.archivedSkipped == 0 && !result.activeRestored
                                when {
                                    noNotes && result.settingsRestored && !result.widgetStylesRestored -> settingsRestored
                                    noNotes && result.widgetStylesRestored && !result.settingsRestored -> widgetSaved
                                    noNotes && !result.settingsRestored && !result.widgetStylesRestored -> nothingRestored
                                    else -> restored.format(result.archivedAdded, result.archivedSkipped)
                                }
                            }
                        snackbarHostState.showSnackbar(message)
                    }
                }
            }
        }

    fun launchExport(content: String, fileName: String) {
        viewModel.pendingExport = content
        exportLauncher.launch(fileName)
    }

    SettingsPage(
        stringResource(R.string.backup_title),
        onBack,
        snackbarHost = {
            SwipeableSnackbarHost(snackbarHostState, modifier = AppSnackbarHostModifier)
        },
    ) {
        // Same card treatment as the About header, without the accent split:
        // the explanation and the two live counts read as one surface.
        SettingsGroup(
            listOf({
                Column {
                    Text(
                        stringResource(R.string.backup_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    // A bounded row height lets the divider fill the whole
                    // stats area, from the horizontal rule to the card bottom.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(88.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BackupStat(
                            value = if (preview.activeNote) "1" else "0",
                            label = stringResource(R.string.backup_include_active),
                            modifier = Modifier.weight(1f),
                        )
                        VerticalDivider(
                            modifier = Modifier.fillMaxHeight(),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        BackupStat(
                            value = preview.archivedCount.toString(),
                            label = stringResource(R.string.backup_include_archived),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            })
        )

        SettingsSectionHeader(stringResource(R.string.backup_section_create))
        SettingsGroup(
            listOf(
                {
                    ValueRow(
                        painterResource(R.drawable.ic_symbol_upload),
                        stringResource(R.string.backup_create),
                        stringResource(R.string.backup_create_message),
                        { showCreate = true },
                        enabled = includeActive || includeArchived || includeSettings || includeWidgets,
                    )
                },
                {
                    ToggleRow(
                        Icons.Outlined.Edit,
                        stringResource(R.string.backup_include_active),
                        stringResource(R.string.backup_include_active_desc),
                        includeActive,
                        { includeActive = it },
                    )
                },
                {
                    ToggleRow(
                        Icons.Outlined.Archive,
                        stringResource(R.string.backup_include_archived),
                        stringResource(R.string.backup_include_archived_desc),
                        includeArchived,
                        { includeArchived = it },
                    )
                },
                {
                    ToggleRow(
                        Icons.Outlined.Settings,
                        stringResource(R.string.backup_include_settings),
                        stringResource(R.string.backup_include_settings_desc),
                        includeSettings,
                        { includeSettings = it },
                    )
                },
                {
                    ToggleRow(
                        painterResource(R.drawable.ic_format_paint),
                        stringResource(R.string.backup_include_widgets),
                        stringResource(R.string.backup_include_widgets_desc),
                        includeWidgets,
                        { includeWidgets = it },
                    )
                },
            ),
        )

        SettingsSectionHeader(stringResource(R.string.backup_section_restore))
        SettingsGroup(
            listOf(
                {
                    ValueRow(
                        Icons.Outlined.SaveAlt,
                        stringResource(R.string.backup_restore_file),
                        stringResource(R.string.backup_restore_message),
                        { showRestore = true },
                        enabled = restoreNotes || restoreSettings || restoreWidgets,
                    )
                },
                {
                    ToggleRow(
                        Icons.Outlined.Edit,
                        stringResource(R.string.backup_restore_notes),
                        stringResource(R.string.backup_restore_notes_desc),
                        restoreNotes,
                        { restoreNotes = it },
                    )
                },
                {
                    ToggleRow(
                        Icons.Outlined.Settings,
                        stringResource(R.string.backup_restore_settings),
                        stringResource(R.string.backup_restore_settings_desc),
                        restoreSettings,
                        { restoreSettings = it },
                    )
                },
                {
                    ToggleRow(
                        painterResource(R.drawable.ic_format_paint),
                        stringResource(R.string.backup_restore_widgets),
                        stringResource(R.string.backup_restore_widgets_desc),
                        restoreWidgets,
                        { restoreWidgets = it },
                    )
                },
            ),
        )

        SettingsSectionHeader(stringResource(R.string.backup_section_notes))
        SettingsGroup(
            listOf(
                {
                    ValueRow(
                        painterResource(R.drawable.ic_symbol_upload),
                        stringResource(R.string.backup_notes_export),
                        stringResource(R.string.backup_notes_export_short),
                        { showNotesExport = true },
                    )
                },
                {
                    ValueRow(
                        Icons.Outlined.SaveAlt,
                        stringResource(R.string.backup_notes_import),
                        stringResource(R.string.backup_notes_import_short),
                        {
                            importMode = "notes"
                            showNotesImport = true
                        },
                    )
                },
            ),
        )
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            icon = {
                Icon(
                    painterResource(R.drawable.ic_symbol_upload),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            title = { Text(stringResource(R.string.backup_create)) },
            text = { Text(stringResource(R.string.backup_create_message)) },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        showCreate = false
                        busy = true
                        scope.launch {
                            val content =
                                try {
                                    withContext(Dispatchers.IO) {
                                        viewModel.createBackup(
                                            BackupSelection(
                                                includeActive,
                                                includeArchived,
                                                includeSettings,
                                                includeWidgets,
                                            )
                                        )
                                    }
                                } catch (_: Exception) {
                                    null
                                }
                            busy = false
                            if (content != null) launchExport(content, BACKUP_FILE_NAME)
                            else snackbarHostState.showSnackbar(saveFailed)
                        }
                    },
                ) { Text(stringResource(R.string.backup_create)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) {
                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }

    if (showRestore) {
        AlertDialog(
            onDismissRequest = { showRestore = false },
            icon = { Icon(Icons.Outlined.SaveAlt, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.backup_restore_file)) },
            text = {
                Column {
                    Text(stringResource(R.string.backup_restore_message))
                    if (hasNotes) {
                        Column(Modifier.selectableGroup().padding(top = 8.dp)) {
                            listOf(false to R.string.backup_merge, true to R.string.backup_replace).forEach { (replace, label) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .selectable(
                                            selected = replaceNotes == replace,
                                            role = Role.RadioButton,
                                            onClick = { replaceNotes = replace },
                                        ),
                                ) {
                                    RadioButton(selected = replaceNotes == replace, onClick = null)
                                    Column(Modifier.padding(start = 8.dp)) {
                                        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            stringResource(if (replace) R.string.backup_replace_desc else R.string.backup_merge_desc),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.backup_restore_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showRestore = false
                    importMode = "full"
                    importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }) { Text(stringResource(R.string.backup_restore_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showRestore = false }) {
                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }

    if (showNotesExport) {
        AlertDialog(
            onDismissRequest = { showNotesExport = false },
            icon = { Icon(Icons.Outlined.SaveAlt, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.backup_notes_export)) },
            text = {
                Column {
                    Text(stringResource(R.string.archive_export_description))
                    Column(Modifier.selectableGroup()) {
                        listOf(true to R.string.archive_export_json, false to R.string.archive_export_text).forEach { (json, label) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .selectable(
                                        selected = exportJson == json,
                                        role = Role.RadioButton,
                                        onClick = { exportJson = json },
                                    ),
                            ) {
                                RadioButton(selected = exportJson == json, onClick = null)
                                Text(stringResource(label), modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        showNotesExport = false
                        busy = true
                        scope.launch {
                            val content =
                                try {
                                    withContext(Dispatchers.IO) { viewModel.createNotesExport(exportJson) }
                                } catch (_: Exception) {
                                    null
                                }
                            busy = false
                            if (content != null) {
                                launchExport(content, if (exportJson) BACKUP_NOTES_FILE_NAME else "singlenote-archive.txt")
                            } else {
                                snackbarHostState.showSnackbar(saveFailed)
                            }
                        }
                    },
                ) { Text(stringResource(R.string.export_archive)) }
            },
            dismissButton = {
                TextButton(onClick = { showNotesExport = false }) {
                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }

    if (showNotesImport) {
        AlertDialog(
            onDismissRequest = { showNotesImport = false },
            icon = { Icon(Icons.Outlined.SaveAlt, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.backup_notes_import)) },
            text = { Text(stringResource(R.string.archive_import_description)) },
            confirmButton = {
                TextButton(onClick = {
                    showNotesImport = false
                    importMode = "notes"
                    importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }) { Text(stringResource(R.string.archive_import)) }
            },
            dismissButton = {
                TextButton(onClick = { showNotesImport = false }) {
                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }
}

/** One live count in the header card: number first, what it counts underneath. */
@Composable
private fun BackupStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Reads at most [BACKUP_MAX_BYTES] characters, so a huge file is cheap to reject. */
private fun readDocument(context: android.content.Context, uri: android.net.Uri): String {
    val input = context.contentResolver.openInputStream(uri) ?: error("empty document")
    return input.bufferedReader(Charsets.UTF_8).use { reader ->
        val buffer = CharArray(8_192)
        val result = StringBuilder()
        var read: Int
        while (reader.read(buffer).also { read = it } != -1) {
            if (result.length + read > BACKUP_MAX_BYTES) throw IOException("file too large")
            result.append(buffer, 0, read)
        }
        result.toString()
    }
}
