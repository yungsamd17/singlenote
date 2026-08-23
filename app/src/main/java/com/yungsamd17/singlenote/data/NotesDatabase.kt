package com.yungsamd17.singlenote.data

import android.content.Context
import android.content.Intent
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

const val ACTION_NOTE_UPDATED = "com.yungsamd17.singlenote.NOTE_UPDATED"

@Database(entities = [Note::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "singlenote.db"
                ).build().also { instance = it }
            }
    }
}

class NoteRepository(private val dao: NoteDao, private val context: Context) {

    val activeNote: Flow<Note?> = dao.observeActive()
    val archivedNotes: Flow<List<Note>> = dao.observeArchived()

    suspend fun getActive(): Note? = dao.getActive()

    suspend fun saveActive(content: String) {
        val now = System.currentTimeMillis()
        val existing = dao.getActive()
        when {
            existing == null && content.isBlank() -> return
            existing == null -> dao.insert(Note(content = content, createdAt = now, updatedAt = now))
            existing.content != content ->
                dao.update(existing.copy(content = content, updatedAt = now))
        }
        notifyNoteChanged()
    }

    suspend fun archiveActive() {
        dao.getActive()?.let {
            dao.archive(it.id)
            notifyNoteChanged()
        }
    }

    suspend fun restore(noteId: Long) {
        val hasActive = dao.getActive() != null
        if (!hasActive) dao.restore(noteId)
        notifyNoteChanged()
    }

    suspend fun deleteArchived(noteId: Long) = dao.deleteById(noteId)

    private suspend fun notifyNoteChanged() {
        context.sendBroadcast(Intent(ACTION_NOTE_UPDATED).setPackage(context.packageName))
    }
}
