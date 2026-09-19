package com.yungsamd17.singlenote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yungsamd17.singlenote.R
import com.yungsamd17.singlenote.data.Note
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    viewModel: ArchiveViewModel,
    onBack: () -> Unit,
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val pendingDelete by viewModel.pendingDelete.collectAsStateWithLifecycle()
    val pendingClear by viewModel.pendingClear.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // Hoisted: creating formatters during scroll composition is needlessly heavy.
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    var noteToDelete by remember { mutableStateOf<Note?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val undoLabel = stringResource(R.string.action_undo)
    val deletedLabel = stringResource(R.string.archived_note_deleted)
    val clearedLabel = stringResource(R.string.archive_cleared)

    // Optimistic hiding: pending rows vanish at once while the DB write
    // waits out the Undo window. Survives rotation via the ViewModel.
    val pendingClearIds = remember(pendingClear) {
        pendingClear?.map { it.id }?.toSet() ?: emptySet()
    }
    val visibleNotes = remember(notes, pendingDelete, pendingClearIds) {
        notes.filter { note ->
            note.id != pendingDelete?.id && !pendingClearIds.contains(note.id)
        }
    }

    LaunchedEffect(pendingDelete) {
        val pending = pendingDelete ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = deletedLabel,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Long
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
            duration = SnackbarDuration.Long
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
                ArchiveEvent.Restored -> onBack()
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
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    if (visibleNotes.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.clear_archive)
                            )
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
        if (visibleNotes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.archive_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(visibleNotes, key = { it.id }) { note ->
                    ArchivedNoteItem(
                        note = note,
                        date = dateFormat.format(Date(note.updatedAt)),
                        onRestore = { viewModel.restore(note) },
                        onDelete = { noteToDelete = note }
                    )
                }
            }
        }
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
            // One action per slot: three buttons in a single row overflow at
            // large font scales and announce in the wrong order. Cancel
            // dismisses, Swap/Replace confirm — Material 3 has no neutral
            // slot, so both choices share confirmButton where they wrap
            // below Cancel instead of beside it.
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRestoreConflict() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { viewModel.swap(note) }) {
                        Text(stringResource(R.string.action_swap))
                    }
                    TextButton(
                        onClick = { viewModel.replace(note) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(R.string.action_replace))
                    }
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
                    Text(stringResource(R.string.cancel))
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
                    Text(stringResource(R.string.cancel))
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
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = note.content.ifBlank { stringResource(R.string.empty_note_placeholder) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = date,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
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
