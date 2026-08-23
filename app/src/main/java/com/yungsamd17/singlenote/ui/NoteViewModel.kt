package com.yungsamd17.singlenote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yungsamd17.singlenote.data.Note
import com.yungsamd17.singlenote.data.NoteRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NoteViewModel(private val repository: NoteRepository) : ViewModel() {

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    private var currentNoteId: Long? = null
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            adopt(repository.activeNote.first())
        }
    }

    fun onTextChange(value: String) {
        _text.value = value
        scheduleSave()
    }

    fun refreshFromDatabase() {
        viewModelScope.launch {
            adopt(repository.getActive())
        }
    }

    fun flushSave() {
        saveJob?.cancel()
        viewModelScope.launch { persist() }
    }

    fun archiveCurrent() {
        saveJob?.cancel()
        viewModelScope.launch {
            persist()
            repository.archiveActive()
            currentNoteId = null
            _text.value = ""
        }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            persist()
        }
    }

    private suspend fun persist() = repository.saveActive(_text.value)

    private fun adopt(note: Note?) {
        if (note?.id == currentNoteId) return
        saveJob?.cancel()
        currentNoteId = note?.id
        _text.value = note?.content.orEmpty()
    }

    companion object {
        private const val SAVE_DEBOUNCE_MS = 500L

        fun factory(repository: NoteRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                NoteViewModel(repository) as T
        }
    }
}
