package com.yungsamd17.singlenote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yungsamd17.singlenote.data.Note
import com.yungsamd17.singlenote.data.ArchiveRestoreUndo
import com.yungsamd17.singlenote.data.NotePreferences.Companion.FONT_DEFAULT
import com.yungsamd17.singlenote.data.NotePreferences.Companion.SIZE_LARGE
import com.yungsamd17.singlenote.data.NotePreferences.Companion.SIZE_MEDIUM
import com.yungsamd17.singlenote.data.NotePreferences.Companion.SIZE_SMALL
import com.yungsamd17.singlenote.data.ArchiveStore
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NoteViewModel(private val store: NoteStore) : ViewModel() {

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    // Pending destructive action awaiting its Undo window: held here (not in
    // the composable) so the snackbar can be re-shown after rotation and the
    // restore still fires. Cleared on Undo, timeout, or dismissal.
    enum class UndoKind { ARCHIVE, DELETE, SWAP, REPLACE }
    data class PendingUndo(
        val kind: UndoKind,
        val noteId: Long?,
        val content: String,
        val wasPinned: Boolean,
        val createdAtMs: Long,
        val archiveRestore: ArchiveRestoreUndo? = null,
    )
    private val _pendingUndo = MutableStateFlow<PendingUndo?>(null)
    val pendingUndo: StateFlow<PendingUndo?> = _pendingUndo.asStateFlow()

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
        .stateIn(viewModelScope, SharingStarted.Eagerly, SIZE_MEDIUM)

    fun togglePinned() {
        viewModelScope.launch {
            if (!pinned.value && !notificationsEnabled.value) {
                store.setNotificationsEnabled(true)
                // Fresh opt-in: the lockscreen part defaults on with the
                // permission grant (still one tap off in Settings).
                store.setLockscreenVisible(true)
            }
            store.setPinned(!pinned.value)
        }
    }

    private var currentNoteId: Long? = null
    private var saveJob: Job? = null
    // True while a quick action (the notification, the tile, the widget)
    // puts a new note in the place of a pinned one. The new note takes the
    // pin, so the blank editor must not unpin it the way an emptied note
    // does.
    private var keepPinnedForNextNote = false
    // Serializes persist against archive/delete: a flush racing an archive
    // must not interleave, and archive/delete join (rather than orphan) the
    // pending write by holding the same mutex.
    private val persistMutex = Mutex()

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

    /**
     * Persist the editor text now. Returns the persist job so callers that
     * must operate on the saved text (archive, shade actions) can [Job.join]
     * it — or use [flushSaveAndAwait] — instead of assuming it landed.
     * Fire-and-forget callers (ON_STOP, focus loss) just ignore the job.
     */
    fun flushSave(): Job {
        saveJob?.cancel()
        val job = viewModelScope.launch {
            persistMutex.withLock { persist() }
        }
        saveJob = job
        return job
    }

    suspend fun flushSaveAndAwait() {
        flushSave().join()
    }

    /**
     * Archives the note on screen. A quick action (the notification, the
     * quick-settings tile, the widget) passes [keepPinned]: the new note
     * takes over the pin of the note it replaces, so the pinned
     * notification never goes dark.
     */
    fun archiveCurrent(keepPinned: Boolean = false): Job {
        saveJob?.cancel()
        return viewModelScope.launch { archiveNow(keepPinned) }
    }

    /**
     * Quick capture from the notification shade: the note on screen is
     * **archived, never deleted**, so nothing the user wrote is thrown away,
     * and the editor starts empty with the cursor in it.
     *
     * An empty note has nothing worth keeping, so its empty row is dropped
     * instead of filling the archive with a blank entry.
     *
     * A pinned note stays pinned: the new note takes its place.
     */
    fun quickCapture(): Job {
        saveJob?.cancel()
        return viewModelScope.launch {
            if (_text.value.isNotBlank()) archiveNow(keepPinned = true) else clearEmptyNote()
        }
    }

    private suspend fun archiveNow(keepPinned: Boolean = false) {
        val snapshot = _text.value
        val snapshotId = currentNoteId
        val wasPinned = try { store.pinned.first() } catch (_: Exception) { false }
        // The note is replaced by a new one: a pinned note stays pinned, the
        // new note takes over the notification.
        val keepPin = keepPinned && wasPinned
        keepPinnedForNextNote = keepPin
        // Persist-then-archive under one lock: the shade-Archive path
        // and this FAB share the store, so the archive must see the
        // flushed text, never the still-debouncing keystrokes.
        persistMutex.withLock {
            persist()
            store.archiveActive()
        }
        // The archive unpins the note it moved (the next note starts fresh).
        // Put the pin back for a quick action.
        if (keepPin) {
            try { store.setPinned(true) } catch (_: Exception) { }
        }
        currentNoteId = null
        _text.value = ""
        if (snapshot.isNotBlank()) {
            _pendingUndo.value = PendingUndo(
                UndoKind.ARCHIVE, snapshotId, snapshot, wasPinned,
                System.currentTimeMillis()
            )
        }
    }

    private suspend fun clearEmptyNote() {
        val wasPinned = try { store.pinned.first() } catch (_: Exception) { false }
        keepPinnedForNextNote = wasPinned
        persistMutex.withLock { store.deleteActive() }
        if (wasPinned) {
            try { store.setPinned(true) } catch (_: Exception) { }
        }
        currentNoteId = null
        _text.value = ""
    }

    fun deleteCurrent() {
        saveJob?.cancel()
        viewModelScope.launch {
            val snapshot = _text.value
            val wasPinned = try { store.pinned.first() } catch (_: Exception) { false }
            // Hold the mutex (without re-saving) so an in-flight flush
            // can't resurrect the note after the delete lands.
            persistMutex.withLock { store.deleteActive() }
            currentNoteId = null
            _text.value = ""
            if (snapshot.isNotBlank()) {
                _pendingUndo.value = PendingUndo(
                    UndoKind.DELETE, null, snapshot, wasPinned,
                    System.currentTimeMillis()
                )
            }
        }
    }

    /** Adopt the restored row before offering Undo, cancelling stale editor writes. */
    fun onArchiveRestored(undo: ArchiveRestoreUndo?) {
        _pendingUndo.value = null
        saveJob?.cancel()
        viewModelScope.launch {
            persistMutex.withLock {
                val active = store.getActive()
                adopt(active)
                if (undo != null && active?.id == undo.restoredNoteId) {
                    _pendingUndo.value = PendingUndo(
                        if (undo.replaced) UndoKind.REPLACE else UndoKind.SWAP,
                        active.id, active.content, false, undo.createdAtMs, undo
                    )
                }
            }
        }
    }

    fun undoPending() {
        val pending = _pendingUndo.value ?: return
        _pendingUndo.value = null
        saveJob?.cancel()
        viewModelScope.launch {
            when (pending.kind) {
                UndoKind.ARCHIVE -> {
                    // Production store implements both: move the archived row
                    // back instead of duplicating it. Fakes/tests fall back
                    // to re-saving the cached text.
                    val archiveStore = store as? ArchiveStore
                    val restored = if (archiveStore != null && pending.noteId != null) {
                        try { archiveStore.restore(pending.noteId) } catch (_: Exception) { false }
                    } else false
                    if (!restored) {
                        store.saveActive(pending.content)
                        currentNoteId = store.getActive()?.id
                        _text.value = pending.content
                    } else {
                        // Optimistic: show the text at once; the DB flow
                        // re-adopts the same content a moment later.
                        currentNoteId = pending.noteId
                        _text.value = pending.content
                        refreshFromDatabase()
                    }
                    if (pending.wasPinned) {
                        try { store.setPinned(true) } catch (_: Exception) { }
                    }
                }
                UndoKind.SWAP, UndoKind.REPLACE -> {
                    val undo = pending.archiveRestore ?: return@launch
                    val archiveStore = store as? ArchiveStore ?: return@launch
                    persistMutex.withLock {
                        // A newer active note must never be replaced by a stale Undo.
                        if (store.getActive()?.id != undo.restoredNoteId) return@withLock
                        persist()
                        if (archiveStore.undoRestore(undo)) adopt(store.getActive())
                    }
                }
                UndoKind.DELETE -> {
                    store.saveActive(pending.content)
                    currentNoteId = store.getActive()?.id
                    _text.value = pending.content
                    if (pending.wasPinned) {
                        try { store.setPinned(true) } catch (_: Exception) { }
                    }
                }
            }
        }
    }

    fun consumePendingUndo() {
        _pendingUndo.value = null
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            persistMutex.withLock { persist() }
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
        if (note?.content.isNullOrBlank()) {
            if (keepPinnedForNextNote) {
                // A quick action put a new note in the place of the pinned
                // one: the new note takes the pin, so the notification comes
                // back as soon as there is something to show.
                keepPinnedForNextNote = false
            } else {
                unpinIfPinned()
            }
        } else {
            keepPinnedForNextNote = false
        }
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

        // Undo window: matches the snackbar's Short duration. A bar that
        // never resolves (e.g. cancelled by navigating away) goes stale
        // instead of resurrecting on return.
        const val UNDO_WINDOW_MS = 4_000L

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
