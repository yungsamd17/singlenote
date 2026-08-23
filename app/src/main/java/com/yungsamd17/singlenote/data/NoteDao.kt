package com.yungsamd17.singlenote.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes WHERE state = ${Note.STATE_ACTIVE} ORDER BY updatedAt DESC LIMIT 1")
    fun observeActive(): Flow<Note?>

    @Query("SELECT * FROM notes WHERE state = ${Note.STATE_ACTIVE} ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getActive(): Note?

    @Query("SELECT * FROM notes WHERE state = ${Note.STATE_ARCHIVED} ORDER BY updatedAt DESC")
    fun observeArchived(): Flow<List<Note>>

    @Insert
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Query("UPDATE notes SET state = ${Note.STATE_ARCHIVED} WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("UPDATE notes SET state = ${Note.STATE_ACTIVE} WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)
}
