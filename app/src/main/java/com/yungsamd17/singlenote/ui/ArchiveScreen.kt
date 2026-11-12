package com.yungsamd17.singlenote.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import com.yungsamd17.singlenote.data.NotePreferences
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yungsamd17.singlenote.R
import com.yungsamd17.singlenote.data.Note
import com.yungsamd17.singlenote.util.buildArchiveJson
import com.yungsamd17.singlenote.util.parseArchiveJson
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ArchiveScreen(
    viewModel: ArchiveViewModel,
    onBack: () -> Unit,
    onRestored: () -> Unit,
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    var searchActive by rememberSaveable { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val preferences = remember { NotePreferences.get(context.applicationContext) }
    // Start as null, not with a made-up default: rendering the list before the
    // stored sort/order arrive would show it in the wrong order for a frame and
    // then animate every card (including overlaps) into place. Paint nothing
    // until the real values are known instead.
    val sort by preferences.archiveSort.collectAsStateWithLifecycle(initialValue = null)
    val order by preferences.archiveOrder.collectAsStateWithLifecycle(initialValue = null)
    val listState = rememberLazyListState()
    var showSort by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var exportJson by remember { mutableStateOf(true) }
    var menuOpen by remember { mutableStateOf(false) }
    var now by remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) now = System.currentTimeMillis() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    // The archive always opens at the top: the NavHost restores the last scroll
    // offset, so coming back to a screen that was left half-scrolled looked
    // like the list had opened in the middle.
    LaunchedEffect(Unit) { listState.scrollToItem(0) }
    // Changing the sort or the search builds a different list, so the previous
    // offset belongs to rows that may no longer exist. Starting from the first
    // row is the only predictable outcome — opening and clearing the search
    // included.
    LaunchedEffect(sort, order, searchActive, searchQuery) { listState.scrollToItem(0) }
    // The field is composed in the same frame that flips [searchActive], so
    // the effect runs after it exists and the focus request lands.
    LaunchedEffect(searchActive) {
        if (searchActive) searchFocus.requestFocus()
    }
    // Back closes the search before it leaves the archive: users expect the
    // field to dismiss first, not the whole screen.
    BackHandler(enabled = searchActive) {
        searchActive = false
        viewModel.setQuery("")
        keyboard?.hide()
    }
    val pendingDelete by viewModel.pendingDelete.collectAsStateWithLifecycle()
    val pendingDeleteIds by viewModel.pendingDeleteIds.collectAsStateWithLifecycle()
    val pendingClear by viewModel.pendingClear.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // Hoisted: creating formatters during scroll composition is needlessly heavy.
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    var noteToDelete by remember { mutableStateOf<Note?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    val undoLabel = stringResource(R.string.action_undo)
    val deletedLabel = stringResource(R.string.archived_note_deleted)
    val clearedLabel = stringResource(R.string.archive_cleared)
    val exportSuccess = stringResource(R.string.archive_export_success)
    val exportFailure = stringResource(R.string.archive_export_failed)
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val snapshot = viewModel.pendingExport
        viewModel.pendingExport = null
        if (uri != null) {
            scope.launch {
                val saved = snapshot != null && withContext(Dispatchers.IO) {
                    try {
                        val stream = context.contentResolver.openOutputStream(uri)
                        if (stream == null) false else {
                            stream.bufferedWriter(Charsets.UTF_8).use { it.write(snapshot) }
                            true
                        }
                    } catch (_: java.io.IOException) { false }
                      catch (_: SecurityException) { false }
                }
                snackbarHostState.showSnackbar(if (saved) exportSuccess else exportFailure)
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val imported = withContext(Dispatchers.IO) {
                try {
                    val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("empty")
                    val notes = parseArchiveJson(raw)
                    viewModel.importArchived(notes)
                    true
                } catch (_: Exception) { false }
            }
            snackbarHostState.showSnackbar(context.getString(if (imported) R.string.archive_import_success else R.string.archive_import_failed))
        }
    }

    // Optimistic hiding: pending rows vanish at once while the DB write
    // waits out the Undo window. Survives rotation via the ViewModel.
    val pendingClearIds = remember(pendingClear) {
        pendingClear?.map { it.id }?.toSet() ?: emptySet()
    }
    // Everything that export and clear act on. Search is deliberately not
    // applied here: exporting only the current matches would silently back up
    // a fragment of the archive.
    val sortedNotes = remember(notes, pendingDeleteIds, pendingClearIds, sort, order) {
        val sortKey = sort
        val orderKey = order
        if (sortKey == null || orderKey == null) {
            emptyList()
        } else {
            val filtered = notes.filter { !pendingDeleteIds.contains(it.id) && !pendingClearIds.contains(it.id) }
            val sorted = when (sortKey) {
                "title" -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.content.trim() })
                "archived" -> filtered.sortedBy { it.archivedAt ?: it.updatedAt }
                else -> filtered.sortedBy { it.editedAt ?: it.archivedAt ?: it.updatedAt }
            }
            if (orderKey == "descending" && sortKey != "title" || orderKey == "ascending" && sortKey == "title") sorted.reversed() else sorted
        }
    }
    // Sorting is the expensive part and only changes with the sort keys, so
    // the search filters the already-sorted list instead of re-sorting it.
    val visibleNotes = remember(sortedNotes, searchQuery) {
        filterArchivedNotes(sortedNotes, searchQuery)
    }

    LaunchedEffect(pendingDelete) {
        val pending = pendingDelete ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = deletedLabel,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoDelete()
        }
        // On timeout/dismiss the ViewModel job commits the delete itself.
    }

    LaunchedEffect(pendingClear) {
        val pending = pendingClear ?: return@LaunchedEffect
        if (pending.isEmpty()) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = clearedLabel,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoClear()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                // Back on the main note: the restored/swapped/replaced note
                // is already visible there, so the archive closes itself.
                ArchiveEvent.Restored -> onRestored()
                ArchiveEvent.Cleared ->
                    snackbarHostState.showSnackbar(message = context.getString(R.string.archive_cleared))
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.archive_title)) },
                navigationIcon = {
                    TooltipIconButton(
                        onClick = onBack,
                        tooltip = stringResource(R.string.cd_back),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    if (searchActive) {
                        TooltipIconButton(
                            onClick = {
                                searchActive = false
                                viewModel.setQuery("")
                                keyboard?.hide()
                            },
                            tooltip = stringResource(R.string.cd_close_search),
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.cd_close_search)
                            )
                        }
                    } else {
                        if (archiveSearchAvailable(sortedNotes.size)) {
                            TooltipIconButton(
                                onClick = { searchActive = true },
                                tooltip = stringResource(R.string.cd_search_archive),
                            ) {
                                Icon(
                                    Icons.Outlined.Search,
                                    contentDescription = stringResource(R.string.cd_search_archive)
                                )
                            }
                        }
                    }
                    Box {
                        TooltipIconButton(
                            onClick = { menuOpen = true },
                            tooltip = stringResource(R.string.archive_more),
                        ) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.archive_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false },
                            shape = RoundedCornerShape(28.dp), containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            shadowElevation = 8.dp, tonalElevation = 0.dp, modifier = Modifier.widthIn(min = 220.dp)) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.archive_sort)) }, trailingIcon = { Icon(painterResource(R.drawable.ic_symbol_sort), null) }, onClick = { menuOpen = false; showSort = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.export_archive)) }, enabled = sortedNotes.isNotEmpty(), trailingIcon = { Icon(painterResource(R.drawable.ic_symbol_upload), null) }, onClick = {
                                menuOpen = false
                                showExport = true
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.archive_import)) }, trailingIcon = { Icon(Icons.Outlined.SaveAlt, null) }, onClick = { menuOpen = false; showImport = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.clear_archive)) }, enabled = sortedNotes.isNotEmpty(), trailingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menuOpen = false; showClearDialog = true })
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        // Lifted above the gesture bar like the note screen's host so the
        // Undo bar never sits under it.
        snackbarHost = {
            SwipeableSnackbarHost(
                snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(bottom = 16.dp)
            )
        }
    ) { innerPadding ->
        // Keep the content area empty until the stored sort/order are known so
        // the first painted list is already in the right order.
        val loadedSort = sort
        val loadedOrder = order
        if (loadedSort == null || loadedOrder == null) {
            // Loading preferences; the screen's slide transition covers it.
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    // Keep the list above the keyboard while the field is open.
                    .imePadding()
            ) {
                if (searchActive) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = NoteCardHorizontalPadding, vertical = NoteCardTopPadding)
                            .focusRequester(searchFocus),
                        placeholder = { Text(stringResource(R.string.archive_search)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                TooltipIconButton(
                                    onClick = {
                                        viewModel.setQuery("")
                                        // Clearing the field ends the search, so
                                        // the keyboard goes with it.
                                        keyboard?.hide()
                                    },
                                    tooltip = stringResource(R.string.cd_clear_search),
                                ) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = stringResource(R.string.cd_clear_search)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = AppCardShape,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    )
                }
                if (visibleNotes.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            // Distinguish "the archive is empty" from "nothing
                            // matches the current search".
                            text = stringResource(
                                if (sortedNotes.isEmpty()) R.string.archive_empty
                                else R.string.archive_search_empty
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = NoteCardHorizontalPadding, top = NoteCardTopPadding, end = NoteCardHorizontalPadding, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(visibleNotes, key = { it.id }, contentType = { "archivedNote" }) { note ->
                            androidx.compose.animation.AnimatedContent(
                                targetState = archiveHeader(context, note, loadedSort, now),
                                modifier = Modifier.animateItem(),
                                // Fade only: a size transform would make every row
                                // jump when its timestamp changes.
                                transitionSpec = {
                                    fadeIn(motionSpec(AppMotion.FAST_MS)) togetherWith
                                        fadeOut(motionSpec(AppMotion.FAST_MS))
                                },
                                label = "archive timestamp",
                            ) { header ->
                            ArchivedNoteItem(
                                note = note,
                                date = header,
                                onRestore = { viewModel.restore(note) },
                                onDelete = { noteToDelete = note }
                            )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showExport) {
        AlertDialog(onDismissRequest = { showExport = false }, icon = { Icon(Icons.Outlined.SaveAlt, null, tint = MaterialTheme.colorScheme.primary) }, title = { Text(stringResource(R.string.export_archive)) }, text = {
            Column {
                Text(stringResource(R.string.archive_export_description))
                // Same radio pattern as Settings: the row owns the selection
                // so TalkBack sees one focus stop with a checked state, not
                // a clickable row plus a second radio button.
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
        }, confirmButton = { TextButton(onClick = {
            showExport = false
            viewModel.pendingExport = if (exportJson) buildArchiveJson(sortedNotes) else sortedNotes.joinToString("\n\n---\n\n") { it.content }
            exportLauncher.launch(if (exportJson) "singlenote-archive.json" else "singlenote-archive.txt")
        }) { Text(stringResource(R.string.export_archive)) } }, dismissButton = { TextButton(onClick = { showExport = false }) { Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant) } })
    }
    if (showImport) {
        AlertDialog(onDismissRequest = { showImport = false }, icon = { Icon(Icons.Outlined.SaveAlt, null, tint = MaterialTheme.colorScheme.primary) }, title = { Text(stringResource(R.string.archive_import)) },
            text = { Text(stringResource(R.string.archive_import_description)) },
            confirmButton = { TextButton(onClick = { showImport = false; importLauncher.launch(arrayOf("application/json", "application/octet-stream")) }) { Text(stringResource(R.string.archive_import)) } },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant) } })
    }
    if (showSort) {
        AlertDialog(onDismissRequest = { showSort = false }, icon = { Icon(painterResource(R.drawable.ic_symbol_sort), null, tint = MaterialTheme.colorScheme.primary) }, title = { Text(stringResource(R.string.archive_sort_title)) }, text = {
            Column {
                Column(Modifier.selectableGroup()) {
                    listOf("changed" to R.string.archive_sort_changed, "archived" to R.string.archive_sort_archived, "title" to R.string.archive_sort_name).forEach { (key, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = sort == key,
                                    role = Role.RadioButton,
                                    onClick = { scope.launch { preferences.setArchiveSort(key) } },
                                ),
                        ) {
                            RadioButton(selected = sort == key, onClick = null)
                            Text(stringResource(label), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
                Text(stringResource(R.string.archive_order), modifier = Modifier.padding(top = 12.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("descending", "ascending").forEachIndexed { index, key ->
                        val label = if (sort == "title") { if (index == 0) R.string.archive_order_az else R.string.archive_order_za } else { if (index == 0) R.string.archive_order_newest else R.string.archive_order_oldest }
                        SegmentedButton(selected = order == key, onClick = { scope.launch { preferences.setArchiveOrder(key) } }, shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(stringResource(label)) }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { showSort = false }) { Text(stringResource(R.string.action_done)) } })
    }
    viewModel.restoreConflict?.let { note ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissRestoreConflict() },
            icon = {
                Icon(
                    Icons.Outlined.Unarchive,
                    contentDescription = null
                )
            },
            title = { Text(stringResource(R.string.restore_conflict_title)) },
            text = { Text(stringResource(R.string.restore_conflict_message)) },
            confirmButton = {
                // Keep the requested reading order, wrapping rather than clipping at large text sizes.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = { viewModel.dismissRestoreConflict() }) {
                        Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { viewModel.swap(note) }) {
                        Text(stringResource(R.string.action_swap))
                    }
                    TextButton(
                        onClick = { viewModel.replace(note) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text(stringResource(R.string.action_replace)) }
                }
            }
        )
    }

    noteToDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            icon = {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.delete_dialog_title)) },
            text = { Text(stringResource(R.string.delete_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteWithUndo(note)
                        noteToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.delete_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) {
                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.clear_archive_title)) },
            text = { Text(stringResource(R.string.clear_archive_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearWithUndo(notes)
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.delete_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun ArchivedNoteItem(
    note: Note,
    date: String,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = AppCardShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = date,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                // Each note starts with its timestamp: heading navigation
                // jumps between archived notes instead of through the body.
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .semantics { heading() },
            )
            Text(text = note.content.ifBlank { stringResource(R.string.empty_note_placeholder) }, style = MaterialTheme.typography.bodyLarge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Restore is the primary action: filled button for the
                // strongest contrast on light theme. Delete is destructive:
                // filled button in error colors so it matches restore's
                // weight instead of washing out like the tonal error
                // container does on light theme.
                Button(
                    onClick = onRestore,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Outlined.Unarchive,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.action_restore))
                }
                Button(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.action_delete))
                }
            }
        }
    }
}

private fun archiveHeader(context: android.content.Context, note: Note, sort: String, now: Long): String {
    val edited = sort != "archived" && note.editedAt != null
    val timestamp = if (edited) note.editedAt!! else note.archivedAt ?: note.updatedAt
    val elapsed = (now - timestamp).coerceAtLeast(0)
    val time = when {
        elapsed < 60_000 -> context.getString(R.string.archive_just_now)
        elapsed < 3_600_000 -> context.resources.getQuantityString(R.plurals.archive_minutes_ago, (elapsed / 60_000).toInt(), (elapsed / 60_000).toInt())
        elapsed < 86_400_000 -> context.resources.getQuantityString(R.plurals.archive_hours_ago, (elapsed / 3_600_000).toInt(), (elapsed / 3_600_000).toInt())
        elapsed < 7 * 86_400_000L -> context.resources.getQuantityString(R.plurals.archive_days_ago, (elapsed / 86_400_000).toInt(), (elapsed / 86_400_000).toInt())
        else -> {
            val current = java.util.Calendar.getInstance().apply { timeInMillis = now }
            val then = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
            val flags = android.text.format.DateUtils.FORMAT_SHOW_DATE or android.text.format.DateUtils.FORMAT_ABBREV_MONTH or if (current.get(java.util.Calendar.YEAR) != then.get(java.util.Calendar.YEAR)) android.text.format.DateUtils.FORMAT_SHOW_YEAR else android.text.format.DateUtils.FORMAT_NO_YEAR
            android.text.format.DateUtils.formatDateTime(context, timestamp, flags)
        }
    }
    return context.getString(if (edited) R.string.archive_edited_time else R.string.archive_archived_time, time)
}

/**
 * The app bar only offers the archive search once the archive holds more than
 * one note. With none or a single row there is nothing to filter, and a search
 * that can only return the same row is noise next to the overflow menu.
 */
internal fun archiveSearchAvailable(noteCount: Int): Boolean = noteCount >= 2
