package com.yungsamd17.singlenote.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    // Single-ACTIVE-row guard: at most one row may hold a non-null slot.
    // SQLite treats NULLs as distinct, so archived rows (NULL) never
    // collide — only a second ACTIVE row violates the index. Every DAO
    // write keeps the slot in sync with `state`; see NotesDatabase.
    indices = [Index(value = ["activeSlot"], unique = true)]
)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val state: Int = STATE_ACTIVE,
    val activeSlot: Int? = if (state == STATE_ACTIVE) ACTIVE_SLOT else null,
) {
    companion object {
        const val STATE_ACTIVE = 0
        const val STATE_ARCHIVED = 1
        const val ACTIVE_SLOT = 1
    }
}
