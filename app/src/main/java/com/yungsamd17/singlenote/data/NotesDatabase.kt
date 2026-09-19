package com.yungsamd17.singlenote.data

import android.content.Context
import android.content.Intent
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    val accentColor: Flow<String>

    suspend fun getActive(): Note?
    suspend fun saveActive(content: String)
    suspend fun archiveActive()
    suspend fun deleteActive()
    suspend fun setPinned(value: Boolean)
    suspend fun setNotificationsEnabled(value: Boolean)
    suspend fun setThemeMode(value: String)
    suspend fun setFontFamily(value: String)
    suspend fun setTextSize(value: String)
    suspend fun setAccentColor(value: String)
}

interface ArchiveStore {
    val archivedNotes: Flow<List<Note>>
    suspend fun restore(noteId: Long): Boolean
    suspend fun deleteArchived(noteId: Long)
    suspend fun hasActiveNote(): Boolean
    suspend fun swapWithActive(noteId: Long)
    suspend fun replaceActive(noteId: Long)
    suspend fun clearArchived()
}

/**
 * Single source of truth for the active note, the archive and the display
 * prefs. The app wires it via the secondary `NoteRepository(dao, context)`
 * constructor; unit tests use the primary constructor with a fake [NoteDao], a fake
 * [PreferencesStore] and a recording broadcast — no Android framework.
 *
 * Writes serialize on [writeMutex] so a debounced flush racing an archive,
 * swap or replace can't interleave read-modify-write sequences into a
 * duplicated ACTIVE row. Content saves broadcast throttled (leading +
 * trailing, [BROADCAST_THROTTLE_MS]) so every keystroke-save doesn't force
 * a full Glance rebuild; explicit user actions (pin, archive, restore,
 * swap, replace, clear) broadcast immediately.
 */
class NoteRepository(
    private val dao: NoteDao,
    private val preferences: PreferencesStore,
    private val broadcast: suspend () -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
    private val notifyScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val broadcastThrottleMs: Long = BROADCAST_THROTTLE_MS,
) : NoteStore, ArchiveStore {

    constructor(dao: NoteDao, context: Context) : this(
        dao = dao,
        preferences = NotePreferences.get(context),
        broadcast = {
            val app = context.applicationContext
            app.sendBroadcast(Intent(ACTION_NOTE_UPDATED).setPackage(app.packageName))
        }
    )

    private val writeMutex = Mutex()
    private val notifyLock = Any()

    // Null until the first broadcast: the first save always notifies at
    // once instead of waiting out a throttle window anchored at zero.
    private var lastBroadcastMs: Long? = null
    private var trailingBroadcast: Job? = null

    override val activeNote: Flow<Note?> = dao.observeActive()
    override val pinned: Flow<Boolean> = preferences.pinned
    override val notificationsEnabled: Flow<Boolean> = preferences.notificationsEnabled
    override val themeMode: Flow<String> = preferences.themeMode
    override val fontFamily: Flow<String> = preferences.fontFamily
    override val textSize: Flow<String> = preferences.textSize
    override val accentColor: Flow<String> = preferences.accentColor
    override val archivedNotes: Flow<List<Note>> = dao.observeArchived()

    override suspend fun getActive(): Note? = dao.getActive()

    override suspend fun saveActive(content: String) {
        val changed = writeMutex.withLock {
            val now = clock()
            val existing = dao.getActive()
            when {
                existing == null && content.isBlank() -> false
                existing == null -> {
                    dao.insert(Note(content = content, createdAt = now, updatedAt = now))
                    true
                }
                existing.content != content -> {
                    dao.update(existing.copy(content = content, updatedAt = now))
                    true
                }
                // Same content re-saved (flush after an already-persisted
                // edit): nothing changed, so no widget/notification churn.
                else -> false
            }
        }
        if (changed) notifyThrottled()
    }

    override suspend fun archiveActive() {
        val archived = writeMutex.withLock {
            val active = dao.getActive() ?: return@withLock false
            dao.archive(active.id)
            true
        }
        if (archived) {
            // Same as delete: archiving empties the main view, so the next
            // note starts unpinned instead of inheriting the pinned state.
            // setPinned already broadcasts the change.
            setPinned(false)
        }
    }

    override suspend fun deleteActive() {
        val deleted = writeMutex.withLock {
            val active = dao.getActive() ?: return@withLock false
            dao.deleteById(active.id)
            true
        }
        if (deleted) {
            // Unpin with the note: the next note starts unpinned instead of
            // inheriting the deleted note's pinned state. setPinned already
            // broadcasts the change.
            setPinned(false)
        }
    }

    override suspend fun restore(noteId: Long): Boolean {
        val restored = writeMutex.withLock {
            if (dao.getActive() != null) return@withLock false
            dao.restore(noteId)
            true
        }
        if (restored) notifyNow()
        return restored
    }

    override suspend fun deleteArchived(noteId: Long) = dao.deleteById(noteId)

    override suspend fun hasActiveNote(): Boolean = dao.getActive() != null

    override suspend fun swapWithActive(noteId: Long) {
        val swapped = writeMutex.withLock {
            val active = dao.getActive()
            val archived = dao.getById(noteId)
            if (active != null && archived != null && archived.state == Note.STATE_ARCHIVED) {
                dao.setState(active.id, Note.STATE_ARCHIVED)
                dao.setState(archived.id, Note.STATE_ACTIVE)
                true
            } else {
                false
            }
        }
        if (swapped) notifyNow()
    }

    override suspend fun replaceActive(noteId: Long) {
        val replaced = writeMutex.withLock {
            val archived = dao.getById(noteId) ?: return@withLock false
            if (archived.state != Note.STATE_ARCHIVED) return@withLock false
            dao.getActive()?.let { dao.deleteById(it.id) }
            dao.restore(noteId)
            true
        }
        if (replaced) notifyNow()
    }

    override suspend fun clearArchived() {
        writeMutex.withLock { dao.deleteArchived() }
        notifyNow()
    }

    override suspend fun setPinned(value: Boolean) {
        preferences.setPinned(value)
        notifyNow()
    }

    override suspend fun setNotificationsEnabled(value: Boolean) {
        preferences.setNotificationsEnabled(value)
        if (!value) preferences.setPinned(false)
        notifyNow()
    }

    override suspend fun setThemeMode(value: String) = preferences.setThemeMode(value)

    override suspend fun setFontFamily(value: String) = preferences.setFontFamily(value)

    override suspend fun setTextSize(value: String) = preferences.setTextSize(value)

    override suspend fun setAccentColor(value: String) = preferences.setAccentColor(value)

    /**
     * Immediate broadcast: cancels any coalesced trailing save broadcast,
     * which it supersedes (pin/archive/restore describe the newest state).
     */
    private fun notifyNow() {
        synchronized(notifyLock) {
            lastBroadcastMs = clock()
            trailingBroadcast?.cancel()
            trailingBroadcast = notifyScope.launch { broadcast() }
        }
    }

    /**
     * Coalesced broadcast for content saves: first save in a quiet window
     * notifies at once (leading), saves inside the window collapse into one
     * trailing broadcast [BROADCAST_THROTTLE_MS] after the leading one.
     */
    private fun notifyThrottled() {
        synchronized(notifyLock) {
            val now = clock()
            val last = lastBroadcastMs
            if (last == null || now - last >= broadcastThrottleMs) {
                lastBroadcastMs = now
                trailingBroadcast?.cancel()
                trailingBroadcast = notifyScope.launch { broadcast() }
                return
            }
            trailingBroadcast?.cancel()
            val remaining = broadcastThrottleMs - (now - last)
            trailingBroadcast = notifyScope.launch {
                delay(remaining)
                synchronized(notifyLock) { lastBroadcastMs = clock() }
                broadcast()
            }
        }
    }

    companion object {
        /**
         * Widget/notification coalescing window: debounced keystroke-saves
         * arrive ~500ms apart, so 3s collapses a burst into leading +
         * trailing broadcasts instead of a rebuild per keystroke.
         */
        const val BROADCAST_THROTTLE_MS = 3000L
    }
}
