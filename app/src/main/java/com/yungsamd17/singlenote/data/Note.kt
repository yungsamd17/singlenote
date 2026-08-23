package com.yungsamd17.singlenote.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val state: Int = STATE_ACTIVE,
) {
    companion object {
        const val STATE_ACTIVE = 0
        const val STATE_ARCHIVED = 1
    }
}
