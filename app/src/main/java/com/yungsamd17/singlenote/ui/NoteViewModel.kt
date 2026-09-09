package com.yungsamd17.singlenote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yungsamd17.singlenote.data.Note
import com.yungsamd17.singlenote.data.NotePreferences.Companion.FONT_DEFAULT
import com.yungsamd17.singlenote.data.NotePreferences.Companion.SIZE_LARGE
import com.yungsamd17.singlenote.data.NotePreferences.Companion.SIZE_MEDIUM
import com.yungsamd17.singlenote.data.NotePreferences.Companion.SIZE_SMALL
import com.yungsamd17.singlenote.data.NoteStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NoteViewModel(private val store: NoteStore) : ViewModel() {

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    val pinned: StateFlow<Boolean> = store.pinned
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val notificationsEnabled: StateFlow<Boolean> = store.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val fontFamily: StateFlow<String> = store.fontFamily
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FONT_DEFAULT)

    val textSize: StateFlow<String> = store.textSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SIZE_MEDIUM)

    fun togglePinned() {
        viewModelScope.launch {
            if (!pinned.value && !notificationsEnabled.value) {
                store.setNotificationsEnabled(true)
            }
            store.setPinned(!pinned.value)
        }
    }

    private var currentNoteId: Long? = null
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            adopt(store.activeNote.first())
        }
    }

    fun onTextChange(value: String) {
        // The editor card has a fixed visible size with no scrolling, so input
        // is capped at what fits. Smaller fonts fit more text, larger less.
        val capped = value.take(maxLengthForTextSize(textSize.value))
        if (capped == _text.value) return
        _text.value = capped
        scheduleSave()
    }

    fun refreshFromDatabase() {
        viewModelScope.launch {
            adopt(store.getActive())
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
            store.archiveActive()
            currentNoteId = null
            _text.value = ""
        }
    }

    fun deleteCurrent() {
        saveJob?.cancel()
        viewModelScope.launch {
            store.deleteActive()
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

    private suspend fun persist() = store.saveActive(_text.value)

    private fun adopt(note: Note?) {
        if (note?.id == currentNoteId) return
        saveJob?.cancel()
        currentNoteId = note?.id
        _text.value = note?.content.orEmpty()
    }

    companion object {
        private const val SAVE_DEBOUNCE_MS = 500L

        // Fixed editor capacity per text size: the card height is sized to
        // exactly these lines, so the longest allowed note fills it with
        // nothing left to scroll to. Smaller font fits more text.
        const val MAX_LENGTH_SMALL = 360
        const val MAX_LENGTH_MEDIUM = 230
        const val MAX_LENGTH_LARGE = 150

        const val MAX_LINES_SMALL = 12
        const val MAX_LINES_MEDIUM = 9
        const val MAX_LINES_LARGE = 7

        fun maxLengthForTextSize(key: String): Int = when (key) {
            SIZE_SMALL -> MAX_LENGTH_SMALL
            SIZE_LARGE -> MAX_LENGTH_LARGE
            else -> MAX_LENGTH_MEDIUM
        }

        fun maxLinesForTextSize(key: String): Int = when (key) {
            SIZE_SMALL -> MAX_LINES_SMALL
            SIZE_LARGE -> MAX_LINES_LARGE
            else -> MAX_LINES_MEDIUM
        }

        fun factory(store: NoteStore) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                NoteViewModel(store) as T
        }
    }
}
