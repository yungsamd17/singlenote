package com.yungsamd17.singlenote.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yungsamd17.singlenote.data.ArchiveRestoreUndo
import com.yungsamd17.singlenote.data.ArchiveStore
import com.yungsamd17.singlenote.data.Note
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
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

    /**
     * Raw text of the archive search field. Kept here (not in the screen) so
     * it survives rotation and process recreation of the composable.
     */
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /**
     * Debounced copy of [query] used to filter the list. The archive already
     * lives in memory, so filtering is cheap — the debounce only avoids
     * re-sorting and recomposing the list on every keystroke of a long word.
     */
    val searchQuery: StateFlow<String> = _query
        .debounce(SEARCH_DEBOUNCE_MS)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun setQuery(value: String) {
        _query.value = value
    }

    // Retain the selected snapshot while the system save picker is open.
    var pendingExport: String? = null

    var restoreConflict by mutableStateOf<Note?>(null)
        private set

    private var restoredUndo: ArchiveRestoreUndo? = null

    fun takeRestoreUndo(): ArchiveRestoreUndo? = restoredUndo.also { restoredUndo = null }

    fun restore(note: Note) {
        viewModelScope.launch {
            if (archiveStore.restore(note.id)) {
                restoredUndo = null
                _events.send(ArchiveEvent.Restored)
            } else if (archiveStore.hasActiveNote()) {
                restoreConflict = note
            }
        }
    }

    fun swap(note: Note) {
        viewModelScope.launch {
            restoredUndo = archiveStore.restoreWithUndo(note.id, replace = false) ?: return@launch
            restoreConflict = null
            _events.send(ArchiveEvent.Restored)
        }
    }

    fun replace(note: Note) {
        viewModelScope.launch {
            restoredUndo = archiveStore.restoreWithUndo(note.id, replace = true) ?: return@launch
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

    suspend fun importArchived(notes: List<Note>) = archiveStore.importArchived(notes)

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
    private val _pendingDeleteIds = MutableStateFlow<Set<Long>>(emptySet())
    val pendingDeleteIds: StateFlow<Set<Long>> = _pendingDeleteIds.asStateFlow()
    private val deleteJobs = mutableMapOf<Long, Job>()

    private val _pendingClear = MutableStateFlow<List<Note>?>(null)
    val pendingClear: StateFlow<List<Note>?> = _pendingClear.asStateFlow()
    private var clearJob: Job? = null

    fun deleteWithUndo(note: Note) {
        if (note.id in _pendingDeleteIds.value) return
        _pendingDeleteIds.value = _pendingDeleteIds.value + note.id
        _pendingDelete.value = note
        deleteJobs[note.id] = viewModelScope.launch {
            delay(UNDO_WINDOW_MS)
            try {
                archiveStore.deleteArchived(note.id)
                // Keep the row hidden until the database flow reflects the deletion.
                archiveStore.archivedNotes.first { notes -> notes.none { it.id == note.id } }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) { }
            _pendingDeleteIds.value = _pendingDeleteIds.value - note.id
            if (_pendingDelete.value?.id == note.id) _pendingDelete.value = null
            deleteJobs.remove(note.id)
        }
    }

    fun undoDelete() {
        val note = _pendingDelete.value ?: return
        deleteJobs.remove(note.id)?.cancel()
        _pendingDeleteIds.value = _pendingDeleteIds.value - note.id
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

        // Long enough to swallow a burst of keystrokes, short enough that the
        // filtered list still feels live.
        const val SEARCH_DEBOUNCE_MS = 150L

        fun factory(archiveStore: ArchiveStore) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ArchiveViewModel(archiveStore) as T
        }
    }
}

/**
 * Case-insensitive substring match over the note text.
 *
 * Search runs over the archive the screen already holds instead of querying
 * Room: the list is small, it is already loaded for sorting, and this keeps
 * the schema and the DAO untouched. A blank query returns the list unchanged.
 */
internal fun filterArchivedNotes(notes: List<Note>, query: String): List<Note> {
    val needle = query.trim()
    if (needle.isEmpty()) return notes
    return notes.filter { it.content.contains(needle, ignoreCase = true) }
}
