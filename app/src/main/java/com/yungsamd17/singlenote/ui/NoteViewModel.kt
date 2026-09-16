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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NoteViewModel(private val store: NoteStore) : ViewModel() {

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    // False until the stored note plus the display prefs have all emitted
    // their disk truth. The editor waits for it so its first frame already
    // uses the real text size, font and content — the card never resizes
    // or restyles past the launch background.
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

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
            // Continuous truth: notification actions and the archive screen
            // change the database without this ViewModel involved. Adopting
            // every emission keeps a resumed editor in sync (an archived or
            // deleted note clears it at once). Own saves re-emit the same
            // note id, which adopt() ignores, so typing is never disturbed.
            launch { store.activeNote.collect { adopt(it) } }
            // DataStore/Room first emissions are the stored truth (the
            // StateFlow defaults above are only a pre-load stand-in).
            combine(store.textSize, store.fontFamily) { _, _ -> Unit }.first()
            _ready.value = true
        }
    }

    fun onTextChange(value: String) {
        // Absolute character backstop only. Per-size limits are enforced by
        // the editor for new keystrokes; capping here to the current size
        // would truncate loaded notes while the text-size preference is
        // still resolving (default medium vs stored small) and destroy data.
        // Loaded content is always adopted whole — see adopt().
        val capped = value.take(MAX_LENGTH_SMALL)
        if (capped == _text.value) return
        _text.value = capped
        if (capped.isBlank()) unpinIfPinned()
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
        // Never truncate here: a note saved under a smaller font holds more
        // characters than a larger font allows, and must survive relaunch
        // unchanged regardless of the current text-size setting.
        _text.value = note?.content.orEmpty()
        if (note?.content.isNullOrBlank()) unpinIfPinned()
    }

    // Clearing the main note manually unpins it, same as archiving or
    // deleting: a blank note shows no notification, and the next note must
    // not inherit the pinned state. Nothing ever re-pins automatically —
    // only the pin button sets pinned to true (see togglePinned).
    private fun unpinIfPinned() {
        viewModelScope.launch {
            if (store.pinned.first()) store.setPinned(false)
        }
    }

    companion object {
        private const val SAVE_DEBOUNCE_MS = 500L

        // Note capacity per text size, enforced in the editor for new
        // input: a character cap (maxLength) plus an exact visual-line
        // guard — so the note holds as many characters as visibly fit its
        // fixed card. Smaller font fits more. The ViewModel backstop above
        // stays at the absolute max so stored notes are never truncated.
        const val MAX_LENGTH_SMALL = 1000
        const val MAX_LENGTH_MEDIUM = 600
        const val MAX_LENGTH_LARGE = 400

        const val MAX_LINES_SMALL = 10
        const val MAX_LINES_MEDIUM = 7
        const val MAX_LINES_LARGE = 5

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
