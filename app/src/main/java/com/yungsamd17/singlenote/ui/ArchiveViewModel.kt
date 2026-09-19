package com.yungsamd17.singlenote.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yungsamd17.singlenote.data.ArchiveStore
import com.yungsamd17.singlenote.data.Note
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ArchiveEvent {
    data object Restored : ArchiveEvent
    data object Cleared : ArchiveEvent
}

class ArchiveViewModel(private val archiveStore: ArchiveStore) : ViewModel() {

    val notes: StateFlow<List<Note>> = archiveStore.archivedNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _events = Channel<ArchiveEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    var restoreConflict by mutableStateOf<Note?>(null)
        private set

    fun restore(note: Note) {
        viewModelScope.launch {
            if (archiveStore.hasActiveNote()) {
                restoreConflict = note
            } else if (archiveStore.restore(note.id)) {
                _events.send(ArchiveEvent.Restored)
            }
        }
    }

    fun swap(note: Note) {
        viewModelScope.launch {
            archiveStore.swapWithActive(note.id)
            restoreConflict = null
            _events.send(ArchiveEvent.Restored)
        }
    }

    fun replace(note: Note) {
        viewModelScope.launch {
            archiveStore.replaceActive(note.id)
            restoreConflict = null
            _events.send(ArchiveEvent.Restored)
        }
    }

    fun dismissRestoreConflict() {
        restoreConflict = null
    }

    fun delete(note: Note) {
        viewModelScope.launch { archiveStore.deleteArchived(note.id) }
    }

    fun clearArchive() {
        viewModelScope.launch {
            archiveStore.clearArchived()
            _events.send(ArchiveEvent.Cleared)
        }
    }

    // Undo-aware variants: the destructive write is delayed by the Undo
    // window (10s, exceeding the 5s minimum to give TalkBack users extra
    // time). State lives here so rotation re-shows the bar instead of
    // losing the chance. Deleting only the captured ids avoids removing
    // notes archived during the window.
    private val _pendingDelete = MutableStateFlow<Note?>(null)
    val pendingDelete: StateFlow<Note?> = _pendingDelete.asStateFlow()
    private var deleteJob: Job? = null

    private val _pendingClear = MutableStateFlow<List<Note>?>(null)
    val pendingClear: StateFlow<List<Note>?> = _pendingClear.asStateFlow()
    private var clearJob: Job? = null

    fun deleteWithUndo(note: Note) {
        deleteJob?.cancel()
        _pendingDelete.value = note
        deleteJob = viewModelScope.launch {
            delay(UNDO_WINDOW_MS)
            try { archiveStore.deleteArchived(note.id) } catch (_: Exception) { }
            _pendingDelete.value = null
        }
    }

    fun undoDelete() {
        deleteJob?.cancel()
        deleteJob = null
        _pendingDelete.value = null
    }

    fun clearWithUndo(snapshot: List<Note>) {
        if (snapshot.isEmpty()) return
        clearJob?.cancel()
        _pendingClear.value = snapshot.toList()
        clearJob = viewModelScope.launch {
            delay(UNDO_WINDOW_MS)
            snapshot.forEach { note ->
                try { archiveStore.deleteArchived(note.id) } catch (_: Exception) { }
            }
            _pendingClear.value = null
        }
    }

    fun undoClear() {
        clearJob?.cancel()
        clearJob = null
        _pendingClear.value = null
    }

    companion object {
        // Matches SnackbarDuration.Short so the bar and the commit stay aligned.
        const val UNDO_WINDOW_MS = 4_000L
        fun factory(archiveStore: ArchiveStore) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ArchiveViewModel(archiveStore) as T
        }
    }
}
