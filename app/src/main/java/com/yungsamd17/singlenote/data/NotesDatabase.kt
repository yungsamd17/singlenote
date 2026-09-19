package com.yungsamd17.singlenote.data

import android.content.Context
import android.content.Intent
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

// exportSchema=true: the schema JSON (app/schemas/) is the baseline the
// next migration diffs against — never flip this back to false.
@Database(entities = [Note::class], version = 2, exportSchema = true)
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
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}

// Adds the activeSlot single-ACTIVE-row guard (see Note). Pre-existing
// duplicate ACTIVE rows — the bug being fixed — collapse to the newest one,
// the rest return to the archive before the unique index is created.
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN activeSlot INTEGER")
        db.execSQL(
            "UPDATE notes SET state = ${Note.STATE_ARCHIVED} WHERE state = ${Note.STATE_ACTIVE} " +
                "AND id NOT IN (SELECT id FROM notes WHERE state = ${Note.STATE_ACTIVE} " +
                "ORDER BY updatedAt DESC, id DESC LIMIT 1)"
        )
        db.execSQL(
            "UPDATE notes SET activeSlot = ${Note.ACTIVE_SLOT} " +
                "WHERE state = ${Note.STATE_ACTIVE}"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_notes_activeSlot ON notes(activeSlot)")
    }
}

interface NoteStore {
    val activeNote: Flow<Note?>
    val pinned: Flow<Boolean>
    val notificationsEnabled: Flow<Boolean>
    val lockscreenVisible: Flow<Boolean>
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
    suspend fun setLockscreenVisible(value: Boolean)
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
 * duplicated ACTIVE row. Each compound op additionally runs in a DAO
 * @Transaction, and the unique activeSlot index is the cross-process
 * backstop against a duplicated ACTIVE row. Content saves broadcast throttled
 * (leading + trailing, [BROADCAST_THROTTLE_MS]) so every keystroke-save
 * doesn't force a full Glance rebuild; explicit user actions (pin, archive,
 * restore, swap, replace, clear) broadcast immediately.
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

    private val notifyLock = Any()

    // Null until the first broadcast: the first save always notifies at
    // once instead of waiting out a throttle window anchored at zero.
    private var lastBroadcastMs: Long? = null
    private var trailingBroadcast: Job? = null

    // Serializes every active-note mutation in this process: debounced
    // saves, ON_STOP flushes, shade actions and archive restores all funnel
    // through here. Each compound op additionally runs in a DAO
    // @Transaction, and the unique activeSlot index is the cross-process
    // backstop against a duplicated ACTIVE row.
    private val writeMutex = Mutex()

    override val activeNote: Flow<Note?> = dao.observeActive()
    override val pinned: Flow<Boolean> = preferences.pinned
    override val notificationsEnabled: Flow<Boolean> = preferences.notificationsEnabled
    override val lockscreenVisible: Flow<Boolean> = preferences.lockscreenVisible
    override val themeMode: Flow<String> = preferences.themeMode
    override val fontFamily: Flow<String> = preferences.fontFamily
    override val textSize: Flow<String> = preferences.textSize
    override val accentColor: Flow<String> = preferences.accentColor
    override val archivedNotes: Flow<List<Note>> = dao.observeArchived()

    override suspend fun getActive(): Note? = dao.getActive()

    override suspend fun saveActive(content: String) {
        val now = clock()
        val wrote = writeMutex.withLock { dao.saveActiveContent(content, now) }
        if (wrote) {
            notifyThrottled()
        }
    }

    override suspend fun archiveActive() {
        val archived = writeMutex.withLock { dao.archiveActiveNote() }
        if (archived) {
            // Same as delete: archiving empties the main view, so the next
            // note starts unpinned instead of inheriting the pinned state.
            // setPinned already broadcasts the change.
            setPinned(false)
        }
    }

    override suspend fun deleteActive() {
        val deleted = writeMutex.withLock { dao.deleteActiveNote() }
        if (deleted) {
            // Unpin with the note: the next note starts unpinned instead of
            // inheriting the deleted note's pinned state. setPinned already
            // broadcasts the change.
            setPinned(false)
        }
    }

    override suspend fun restore(noteId: Long): Boolean {
        val restored = writeMutex.withLock { dao.restoreIfNoActive(noteId) }
        if (restored) {
            notifyNow()
        }
        return restored
    }

    override suspend fun deleteArchived(noteId: Long) =
        writeMutex.withLock { dao.deleteById(noteId) }

    override suspend fun hasActiveNote(): Boolean = dao.getActive() != null

    override suspend fun swapWithActive(noteId: Long) {
        val swapped = writeMutex.withLock { dao.swapActiveWith(noteId) }
        if (swapped) {
            notifyNow()
        }
    }

    override suspend fun replaceActive(noteId: Long) {
        val replaced = writeMutex.withLock { dao.replaceActiveWith(noteId) }
        if (replaced) {
            notifyNow()
        }
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

    override suspend fun setLockscreenVisible(value: Boolean) {
        preferences.setLockscreenVisible(value)
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
