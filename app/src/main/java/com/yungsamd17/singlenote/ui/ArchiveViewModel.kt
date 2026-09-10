package com.yungsamd17.singlenote.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yungsamd17.singlenote.data.ArchiveStore
import com.yungsamd17.singlenote.data.Note
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    companion object {
        fun factory(archiveStore: ArchiveStore) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ArchiveViewModel(archiveStore) as T
        }
    }
}
