package com.yungsamd17.singlenote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yungsamd17.singlenote.data.Note
import com.yungsamd17.singlenote.data.NoteRepository
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

class ArchiveViewModel(private val repository: NoteRepository) : ViewModel() {

    val notes: StateFlow<List<Note>> = repository.archivedNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _events = Channel<ArchiveEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun restore(note: Note) {
        viewModelScope.launch {
            val event = if (repository.restore(note.id)) {
                ArchiveEvent.Restored
            } else {
                ArchiveEvent.BlockedByActiveNote
            }
            _events.send(event)
        }
    }

    fun delete(note: Note) {
        viewModelScope.launch { repository.deleteArchived(note.id) }
    }

    companion object {
        fun factory(repository: NoteRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ArchiveViewModel(repository) as T
        }
    }
}
