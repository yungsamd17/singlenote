package com.yungsamd17.singlenote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yungsamd17.singlenote.data.BackupStore
import com.yungsamd17.singlenote.data.Note
import com.yungsamd17.singlenote.util.BackupPayload
import com.yungsamd17.singlenote.util.BackupSelection
import com.yungsamd17.singlenote.util.BackupSnapshot
import com.yungsamd17.singlenote.util.RestoreResult
import com.yungsamd17.singlenote.util.RestoreSelection
import com.yungsamd17.singlenote.util.buildArchiveJson
import com.yungsamd17.singlenote.util.buildArchiveText
import com.yungsamd17.singlenote.util.ImportFilterResult
import com.yungsamd17.singlenote.util.buildBackupJson
import com.yungsamd17.singlenote.util.filterDuplicateNotes
import com.yungsamd17.singlenote.util.parseBackupFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Counts shown on the Backup screen so the toggles say what is there. */
data class BackupPreview(
    val activeNote: Boolean = false,
    val archivedCount: Int = 0,
    val settingsCount: Int = 0,
    val widgetCount: Int = 0,
    // False until the real counts arrive: the restore dialog must not hide
    // its note choices because of the initial all-zero placeholder.
    val loaded: Boolean = false,
)

class BackupViewModel(private val store: BackupStore) : ViewModel() {

    /** Serialized backup kept across the SAF picker without a Bundle. */
    var pendingExport: String? = null

    private val _preview = MutableStateFlow(BackupPreview())
    val preview: StateFlow<BackupPreview> = _preview.asStateFlow()

    /** Refresh the counts when the screen opens (notes can change elsewhere). */
    fun refresh() {
        viewModelScope.launch {
            val snapshot = collect()
            _preview.value = BackupPreview(
                activeNote = snapshot.active?.content?.isNotBlank() == true,
                archivedCount = snapshot.archived.size,
                settingsCount = snapshot.settings.size,
                widgetCount = snapshot.widgetStyles.keys.count { it == "remembered" || it.toIntOrNull() != null },
                loaded = true,
            )
        }
    }

    private suspend fun collect(): BackupSnapshot =
        BackupSnapshot(
            active = store.activeNote(),
            archived = store.archivedNotes(),
            settings = store.settings(),
            widgetStyles = store.widgetStyles(),
            languageTag = store.languageTag(),
            hasActiveNote = true,
            hasArchivedNotes = true,
            hasSettings = true,
            hasWidgets = true,
        )

    /** Serializes the selected categories while the system save picker is open. */
    suspend fun createBackup(selection: BackupSelection): String =
        buildBackupJson(collect(), selection)

    /** Notes-only export, kept for compatibility with older Singlenote files. */
    suspend fun createNotesExport(json: Boolean): String {
        val notes = store.archivedNotes()
        return if (json) buildArchiveJson(notes) else buildArchiveText(notes)
    }

    /**
     * Imports a full backup or a legacy archive. Notes are deduplicated before
     * they are written; a legacy archive can only add notes — it never deletes
     * the main note, even when "Replace" was selected.
     */
    suspend fun restore(raw: String, selection: RestoreSelection): RestoreResult {
        // A pending editor save would otherwise land on top of the restored note.
        store.flushPendingNote()
        return when (val payload = parseBackupFile(raw)) {
            is BackupPayload.Full -> restoreFull(payload.snapshot, selection)
            is BackupPayload.Notes -> importNotes(payload.notes, payload.plainText, selection.notes)
        }
    }

    private suspend fun restoreFull(snapshot: BackupSnapshot, selection: RestoreSelection): RestoreResult {
        var result = RestoreResult()
        if (selection.notes && (snapshot.hasActiveNote || snapshot.hasArchivedNotes)) {
            result = if (selection.replaceNotes) {
                // Replace only what the file actually contains: a backup with
                // just the active note must not wipe the archive, and vice versa.
                val active = if (snapshot.hasActiveNote) snapshot.active else store.activeNote()
                val archived = if (snapshot.hasArchivedNotes) snapshot.archived else store.archivedNotes()
                store.replaceNotes(active, archived)
                RestoreResult(
                    activeRestored = snapshot.hasActiveNote && snapshot.active != null,
                    archivedAdded = if (snapshot.hasArchivedNotes) snapshot.archived.size else 0,
                )
            } else {
                val existing = store.archivedNotes()
                val filtered =
                    if (snapshot.hasArchivedNotes) filterDuplicateNotes(snapshot.archived, existing)
                    else ImportFilterResult(emptyList(), 0)
                val active =
                    snapshot.active?.takeIf {
                        snapshot.hasActiveNote && store.activeNote()?.content.isNullOrBlank()
                    }
                active?.let { store.restoreActive(it) }
                if (filtered.accepted.isNotEmpty()) store.importArchived(filtered.accepted)
                RestoreResult(
                    activeRestored = active != null,
                    archivedAdded = filtered.accepted.size,
                    archivedSkipped = filtered.skipped,
                )
            }
        }
        if (selection.appSettings && snapshot.hasSettings) {
            store.applySettings(snapshot.settings)
            store.applyLanguage(snapshot.languageTag)
            result = result.copy(settingsRestored = true)
        }
        if (selection.widgetSettings && snapshot.hasWidgets) {
            store.applyWidgetStyles(snapshot.widgetStyles)
            result = result.copy(widgetStylesRestored = true)
        }
        store.refreshSurfaces()
        refresh()
        return result
    }

    private suspend fun importNotes(
        notes: List<Note>,
        plainText: Boolean,
        enabled: Boolean,
    ): RestoreResult {
        if (!enabled) return RestoreResult()
        val filtered = filterDuplicateNotes(notes, store.archivedNotes(), matchContentOnly = plainText)
        store.importArchived(filtered.accepted)
        store.refreshSurfaces()
        refresh()
        return RestoreResult(archivedAdded = filtered.accepted.size, archivedSkipped = filtered.skipped)
    }

    companion object {
        fun factory(store: BackupStore) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                BackupViewModel(store) as T
        }
    }
}
