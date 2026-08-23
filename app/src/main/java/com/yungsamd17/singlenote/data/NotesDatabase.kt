package com.yungsamd17.singlenote.data

import android.content.Context
import android.content.Intent
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

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

interface NoteStore {
    val activeNote: Flow<Note?>
    val pinned: Flow<Boolean>
    val notificationsEnabled: Flow<Boolean>
    val themeMode: Flow<String>
    val fontFamily: Flow<String>
    val textSize: Flow<String>

    suspend fun getActive(): Note?
    suspend fun saveActive(content: String)
    suspend fun archiveActive()
    suspend fun setPinned(value: Boolean)
    suspend fun setNotificationsEnabled(value: Boolean)
    suspend fun setThemeMode(value: String)
    suspend fun setFontFamily(value: String)
    suspend fun setTextSize(value: String)
}

interface ArchiveStore {
    val archivedNotes: Flow<List<Note>>
    suspend fun restore(noteId: Long): Boolean
    suspend fun deleteArchived(noteId: Long)
}

class NoteRepository(private val dao: NoteDao, private val context: Context) :
    NoteStore, ArchiveStore {

    private val preferences = NotePreferences(context)

    override val activeNote: Flow<Note?> = dao.observeActive()
    override val pinned: Flow<Boolean> = preferences.pinned
    override val notificationsEnabled: Flow<Boolean> = preferences.notificationsEnabled
    override val themeMode: Flow<String> = preferences.themeMode
    override val fontFamily: Flow<String> = preferences.fontFamily
    override val textSize: Flow<String> = preferences.textSize
    override val archivedNotes: Flow<List<Note>> = dao.observeArchived()

    override suspend fun getActive(): Note? = dao.getActive()

    override suspend fun saveActive(content: String) {
        val now = System.currentTimeMillis()
        val existing = dao.getActive()
        when {
            existing == null && content.isBlank() -> return
            existing == null ->
                dao.insert(Note(content = content, createdAt = now, updatedAt = now))
            existing.content != content ->
                dao.update(existing.copy(content = content, updatedAt = now))
        }
        notifyNoteChanged()
    }

    override suspend fun archiveActive() {
        dao.getActive()?.let {
            dao.archive(it.id)
            notifyNoteChanged()
        }
    }

    override suspend fun restore(noteId: Long): Boolean {
        val canRestore = dao.getActive() == null
        if (canRestore) {
            dao.restore(noteId)
            notifyNoteChanged()
        }
        return canRestore
    }

    override suspend fun deleteArchived(noteId: Long) = dao.deleteById(noteId)

    override suspend fun setPinned(value: Boolean) {
        preferences.setPinned(value)
        notifyNoteChanged()
    }

    override suspend fun setNotificationsEnabled(value: Boolean) {
        preferences.setNotificationsEnabled(value)
        if (!value) preferences.setPinned(false)
        notifyNoteChanged()
    }

    override suspend fun setThemeMode(value: String) = preferences.setThemeMode(value)

    override suspend fun setFontFamily(value: String) = preferences.setFontFamily(value)

    override suspend fun setTextSize(value: String) = preferences.setTextSize(value)

    private suspend fun notifyNoteChanged() {
        context.sendBroadcast(Intent(ACTION_NOTE_UPDATED).setPackage(context.packageName))
    }
}
