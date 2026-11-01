package com.yungsamd17.singlenote.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class NoteDao {

    @Query("SELECT * FROM notes WHERE state = ${Note.STATE_ACTIVE} ORDER BY updatedAt DESC LIMIT 1")
    abstract fun observeActive(): Flow<Note?>

    @Query("SELECT * FROM notes WHERE state = ${Note.STATE_ACTIVE} ORDER BY updatedAt DESC LIMIT 1")
    abstract suspend fun getActive(): Note?

    @Query("SELECT * FROM notes WHERE state = ${Note.STATE_ARCHIVED} ORDER BY updatedAt DESC")
    abstract fun observeArchived(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id") abstract suspend fun getById(id: Long): Note?

    @Insert abstract suspend fun insert(note: Note): Long

    @Update abstract suspend fun update(note: Note)

    // Every state flip maintains activeSlot alongside state so the unique
    // index keeps enforcing a single ACTIVE row.
    @Query("UPDATE notes SET state = ${Note.STATE_ARCHIVED}, activeSlot = NULL, editedAt = CASE WHEN archivedAt IS NULL THEN NULL ELSE editedAt END, archivedAt = CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER) WHERE id = :id")
    abstract suspend fun archive(id: Long)

    @Query(
        "UPDATE notes SET state = ${Note.STATE_ACTIVE}, activeSlot = ${Note.ACTIVE_SLOT} WHERE id = :id"
    )
    abstract suspend fun restore(id: Long)

    @Query("DELETE FROM notes WHERE id = :id") abstract suspend fun deleteById(id: Long)

    @Query("DELETE FROM notes WHERE state = ${Note.STATE_ARCHIVED}")
    abstract suspend fun deleteArchived()

    // Atomic read-modify-write: the SELECT and the write run in one
    // transaction, so a concurrent flush can never interleave and duplicate
    // the ACTIVE row. Returns false when there is nothing to persist (blank
    // input with no active note, or content identical to the stored note so
    // a post-debounce flush doesn't rewrite the row or annoy the widget).
    @Transaction
    open suspend fun saveActiveContent(content: String, now: Long): Boolean {
        val existing = getActive()
        when {
            existing == null && content.isBlank() -> return false
            existing == null -> insert(Note(content = content, createdAt = now, updatedAt = now))
            existing.content != content -> update(existing.copy(content = content, updatedAt = now, editedAt = if (existing.archivedAt != null) now else null))
            else -> return false
        }
        return true
    }

    // Archives the current active note in the same transaction as the read,
    // so a concurrent save cannot resurrect a duplicate ACTIVE row.
    @Transaction
    open suspend fun archiveActiveNote(): Boolean {
        val active = getActive() ?: return false
        archive(active.id)
        return true
    }

    @Transaction
    open suspend fun deleteActiveNote(): Boolean {
        val active = getActive() ?: return false
        deleteById(active.id)
        return true
    }

    @Transaction
    open suspend fun restoreIfNoActive(noteId: Long): Boolean {
        val archived = getById(noteId) ?: return false
        if (archived.state != Note.STATE_ARCHIVED) return false
        val active = getActive()
        if (active != null && active.content.isNotBlank()) return false
        if (active != null) deleteById(active.id)
        restore(noteId)
        return true
    }

    @Transaction
    open suspend fun swapActiveWith(noteId: Long): Boolean {
        val active = getActive()
        val archived = getById(noteId)
        if (active != null && archived != null && archived.state == Note.STATE_ARCHIVED) {
            archive(active.id)
            restore(archived.id)
            return true
        }
        return false
    }

    @Transaction
    open suspend fun replaceActiveWith(noteId: Long): Boolean {
        val archived = getById(noteId) ?: return false
        if (archived.state != Note.STATE_ARCHIVED) return false
        getActive()?.let { deleteById(it.id) }
        restore(noteId)
        return true
    }

    @Transaction
    open suspend fun importArchived(notes: List<Note>) {
        notes.forEach { insert(it.copy(id = 0, state = Note.STATE_ARCHIVED, activeSlot = null)) }
    }

    @Transaction
    open suspend fun deleteArchivedNote(noteId: Long) {
        // A captured archive deletion may commit after an Undo has restored this row.
        if (getById(noteId)?.state == Note.STATE_ARCHIVED) deleteById(noteId)
    }

    @Transaction
    open suspend fun restoreWithUndo(noteId: Long, replace: Boolean): ArchiveRestoreUndo? {
        val incoming = getById(noteId)?.takeIf { it.state == Note.STATE_ARCHIVED } ?: return null
        val previous = getActive() ?: return null
        if (replace) deleteById(previous.id) else archive(previous.id)
        restore(incoming.id)
        return ArchiveRestoreUndo(incoming.id, previous, replace)
    }

    @Transaction
    open suspend fun undoRestore(undo: ArchiveRestoreUndo): Boolean {
        if (getActive()?.id != undo.restoredNoteId) return false
        val previous = getById(undo.previousActive.id)
        if (undo.replaced) {
            if (previous != null) return false
        } else if (previous?.state != Note.STATE_ARCHIVED) return false
        // Archive first to free the unique active slot. Keep any edits to the incoming note.
        archive(undo.restoredNoteId)
        if (undo.replaced) {
            insert(
                undo.previousActive.copy(state = Note.STATE_ACTIVE, activeSlot = Note.ACTIVE_SLOT)
            )
        } else {
            restore(undo.previousActive.id)
        }
        return true
    }
}
