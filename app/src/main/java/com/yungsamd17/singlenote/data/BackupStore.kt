package com.yungsamd17.singlenote.data

/**
 * Everything a full backup touches. Kept as an interface so the backup logic
 * can be tested without Android: the app wires [BackupRepository], tests use a
 * recording fake. Per-install secrets (the action token, diagnostics logs,
 * launcher alias state) are deliberately not part of this surface.
 */
interface BackupStore {
    suspend fun activeNote(): Note?

    suspend fun archivedNotes(): List<Note>

    /** Raw DataStore values, keyed by preference key name. */
    suspend fun settings(): Map<String, Any>

    /** Raw widget SharedPreferences values, keyed by preference key name. */
    suspend fun widgetStyles(): Map<String, Any>

    suspend fun languageTag(): String

    /** Adds notes to the archive; duplicates are filtered before this call. */
    suspend fun importArchived(notes: List<Note>)

    /** Puts a backed-up note in the main view when it is empty. */
    suspend fun restoreActive(note: Note)

    /** Atomic replacement of both note sets (used by "replace" restores). */
    suspend fun replaceNotes(active: Note?, archived: List<Note>)

    suspend fun applySettings(values: Map<String, Any>)

    suspend fun applyWidgetStyles(values: Map<String, Any>)

    suspend fun applyLanguage(tag: String)

    /** Flushes a pending debounced editor save before notes are replaced. */
    suspend fun flushPendingNote()

    /** Repaints widgets, the tile and the notification after a restore. */
    suspend fun refreshSurfaces()
}
