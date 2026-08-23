package com.yungsamd17.singlenote.ui

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
    data object BlockedByActiveNote : ArchiveEvent
}

class ArchiveViewModel(private val archiveStore: ArchiveStore) : ViewModel() {

    val notes: StateFlow<List<Note>> = archiveStore.archivedNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _events = Channel<ArchiveEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun restore(note: Note) {
        viewModelScope.launch {
            val event = if (archiveStore.restore(note.id)) {
                ArchiveEvent.Restored
            } else {
                ArchiveEvent.BlockedByActiveNote
            }
            _events.send(event)
        }
    }

    fun delete(note: Note) {
        viewModelScope.launch { archiveStore.deleteArchived(note.id) }
    }

    companion object {
        fun factory(archiveStore: ArchiveStore) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ArchiveViewModel(archiveStore) as T
        }
    }
}
