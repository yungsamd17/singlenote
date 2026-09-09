package com.yungsamd17.singlenote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    val snackbarHostState = remember { SnackbarHostState() }
    var noteToDelete by remember { mutableStateOf<Note?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val messageRes = when (event) {
                ArchiveEvent.Restored -> R.string.restored
                ArchiveEvent.Cleared -> R.string.archive_cleared
            }
            snackbarHostState.showSnackbar(message = context.getString(messageRes))
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
                    if (notes.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.clear_archive)
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (notes.isEmpty()) {
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
                items(notes, key = { it.id }) { note ->
                    ArchivedNoteItem(
                        note = note,
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
                    TextButton(onClick = { viewModel.dismissRestoreConflict() }) {
                        Text(stringResource(R.string.cancel))
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
                        viewModel.delete(note)
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
                        viewModel.clearArchive()
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
                text = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(note.updatedAt)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExtendedFloatingActionButton(
                    onClick = onRestore,
                    shape = CircleShape,
                    elevation = noShadowElevation(),
                    icon = {
                        Icon(
                            Icons.Outlined.Unarchive,
                            contentDescription = null
                        )
                    },
                    text = { Text(stringResource(R.string.action_restore)) }
                )
                ExtendedFloatingActionButton(
                    onClick = onDelete,
                    shape = CircleShape,
                    elevation = noShadowElevation(),
                    icon = {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = null
                        )
                    },
                    text = { Text(stringResource(R.string.action_delete)) }
                )
            }
        }
    }
}
