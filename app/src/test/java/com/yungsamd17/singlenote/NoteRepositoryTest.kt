package com.yungsamd17.singlenote

import com.yungsamd17.singlenote.data.Note
import com.yungsamd17.singlenote.data.NoteDao
import com.yungsamd17.singlenote.data.NotePreferences.Companion.ACCENT_DEFAULT
import com.yungsamd17.singlenote.data.NotePreferences.Companion.FONT_DEFAULT
import com.yungsamd17.singlenote.data.NotePreferences.Companion.SIZE_MEDIUM
import com.yungsamd17.singlenote.data.NotePreferences.Companion.THEME_SYSTEM
import com.yungsamd17.singlenote.data.NoteRepository
import com.yungsamd17.singlenote.data.PreferencesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers NoteRepository write paths, swap/replace semantics and broadcast
 * throttling with an in-memory fake DAO, a fake prefs store and a recording
 * broadcast — no Room, DataStore or framework needed.
 */
class NoteRepositoryTest {

    private class FakeDao : NoteDao {
        private val notes = mutableMapOf<Long, Note>()
        private var nextId = 1L
        private val all = MutableStateFlow<List<Note>>(emptyList())

        private fun emit() {
            all.value = notes.values.sortedByDescending { it.updatedAt }
        }

        fun activeCount(): Int = notes.values.count { it.state == Note.STATE_ACTIVE }

        suspend fun insertArchived(content: String): Long =
            insert(Note(content = content, createdAt = 0, updatedAt = 0, state = Note.STATE_ARCHIVED))

        override fun observeActive(): Flow<Note?> =
            all.map { list -> list.firstOrNull { it.state == Note.STATE_ACTIVE } }

        override suspend fun getActive(): Note? =
            notes.values.filter { it.state == Note.STATE_ACTIVE }.maxByOrNull { it.updatedAt }

        override fun observeArchived(): Flow<List<Note>> =
            all.map { list -> list.filter { it.state == Note.STATE_ARCHIVED } }

        override suspend fun getById(id: Long): Note? = notes[id]

        override suspend fun insert(note: Note): Long {
            val id = nextId++
            notes[id] = note.copy(id = id)
            emit()
            return id
        }

        override suspend fun update(note: Note) {
            notes[note.id] = note
            emit()
        }

        override suspend fun archive(id: Long) {
            notes[id]?.let { notes[id] = it.copy(state = Note.STATE_ARCHIVED); emit() }
        }

        override suspend fun restore(id: Long) {
            notes[id]?.let { notes[id] = it.copy(state = Note.STATE_ACTIVE); emit() }
        }

        override suspend fun setState(id: Long, state: Int) {
            notes[id]?.let { notes[id] = it.copy(state = state); emit() }
        }

        override suspend fun deleteById(id: Long) {
            notes.remove(id)
            emit()
        }

        override suspend fun deleteArchived() {
            notes.entries.removeAll { it.value.state == Note.STATE_ARCHIVED }
            emit()
        }
    }

    private class FakePrefs : PreferencesStore {
        override val pinned = MutableStateFlow(false)
        override val notificationsEnabled = MutableStateFlow(true)
        override val themeMode = MutableStateFlow(THEME_SYSTEM)
        override val fontFamily = MutableStateFlow(FONT_DEFAULT)
        override val textSize = MutableStateFlow(SIZE_MEDIUM)
        override val accentColor = MutableStateFlow(ACCENT_DEFAULT)

        override suspend fun setPinned(value: Boolean) {
            pinned.value = value
        }

        override suspend fun setNotificationsEnabled(value: Boolean) {
            notificationsEnabled.value = value
        }

        override suspend fun setThemeMode(value: String) {
            themeMode.value = value
        }

        override suspend fun setFontFamily(value: String) {
            fontFamily.value = value
        }

        override suspend fun setTextSize(value: String) {
            textSize.value = value
        }

        override suspend fun setAccentColor(value: String) {
            accentColor.value = value
        }
    }

    @Test
    fun saveActive_insertsNewNoteAndBroadcasts() = runTest {
        val dao = FakeDao()
        var broadcasts = 0
        val repo = NoteRepository(dao, FakePrefs(), { broadcasts++ }, { 10_000L }, backgroundScope, 0L)

        repo.saveActive("hello")
        advanceUntilIdle()

        assertEquals("hello", dao.getActive()?.content)
        assertEquals(1, broadcasts)
    }

    @Test
    fun saveActive_blankWithNoNote_isNoOp() = runTest {
        val dao = FakeDao()
        var broadcasts = 0
        val repo = NoteRepository(dao, FakePrefs(), { broadcasts++ }, { 10_000L }, backgroundScope, 0L)

        repo.saveActive("   ")
        advanceUntilIdle()

        assertNull(dao.getActive())
        assertEquals(0, broadcasts)
    }

    @Test
    fun saveActive_sameContent_skipsWriteAndBroadcast() = runTest {
        val dao = FakeDao()
        var broadcasts = 0
        var now = 10_000L
        val repo = NoteRepository(dao, FakePrefs(), { broadcasts++ }, { now }, backgroundScope, 0L)

        repo.saveActive("hi")
        advanceUntilIdle()
        assertEquals(1, broadcasts)
        val firstUpdatedAt = dao.getActive()?.updatedAt

        // A flush arriving after the text already persisted (e.g. ON_STOP
        // right after the debounce fired) must not touch the row nor annoy
        // the widget.
        now = 11_000L
        repo.saveActive("hi")
        advanceUntilIdle()

        assertEquals(1, broadcasts)
        assertEquals(firstUpdatedAt, dao.getActive()?.updatedAt)
    }

    @Test
    fun saveActive_updatesExistingNoteWithoutDuplicating() = runTest {
        val dao = FakeDao()
        val repo = NoteRepository(dao, FakePrefs(), {}, { 10_000L }, backgroundScope, 0L)

        repo.saveActive("a")
        repo.saveActive("b")
        advanceUntilIdle()

        assertEquals("b", dao.getActive()?.content)
        assertEquals(1, dao.activeCount())
    }

    @Test
    fun archiveActive_archivesAndUnpins() = runTest {
        val dao = FakeDao()
        val prefs = FakePrefs().apply { pinned.value = true }
        var broadcasts = 0
        val repo = NoteRepository(dao, prefs, { broadcasts++ }, { 10_000L }, backgroundScope, 0L)

        repo.saveActive("to archive")
        advanceUntilIdle()
        repo.archiveActive()
        advanceUntilIdle()

        assertNull(dao.getActive())
        assertFalse(prefs.pinned.value)
        assertTrue(broadcasts >= 1)
    }

    @Test
    fun archiveActive_noActiveNote_isNoOp() = runTest {
        val dao = FakeDao()
        var broadcasts = 0
        val repo = NoteRepository(dao, FakePrefs(), { broadcasts++ }, { 10_000L }, backgroundScope, 0L)

        repo.archiveActive()
        advanceUntilIdle()

        assertEquals(0, broadcasts)
    }

    @Test
    fun deleteActive_deletesAndUnpins() = runTest {
        val dao = FakeDao()
        val prefs = FakePrefs().apply { pinned.value = true }
        var broadcasts = 0
        val repo = NoteRepository(dao, prefs, { broadcasts++ }, { 10_000L }, backgroundScope, 0L)

        repo.saveActive("to delete")
        advanceUntilIdle()
        repo.deleteActive()
        advanceUntilIdle()

        assertNull(dao.getActive())
        assertFalse(prefs.pinned.value)
        assertTrue(broadcasts >= 1)
    }

    @Test
    fun restore_succeedsWhenNoActiveNote() = runTest {
        val dao = FakeDao()
        val archivedId = dao.insertArchived("old")
        var broadcasts = 0
        val repo = NoteRepository(dao, FakePrefs(), { broadcasts++ }, { 10_000L }, backgroundScope, 0L)

        assertTrue(repo.restore(archivedId))
        advanceUntilIdle()

        assertEquals("old", dao.getActive()?.content)
        assertEquals(1, broadcasts)
    }

    @Test
    fun restore_failsWhenActiveNoteExists() = runTest {
        val dao = FakeDao()
        val archivedId = dao.insertArchived("old")
        var broadcasts = 0
        val repo = NoteRepository(dao, FakePrefs(), { broadcasts++ }, { 10_000L }, backgroundScope, 0L)

        repo.saveActive("current")
        advanceUntilIdle()
        broadcasts = 0

        assertFalse(repo.restore(archivedId))
        advanceUntilIdle()

        assertEquals("current", dao.getActive()?.content)
        assertEquals(0, broadcasts)
    }

    @Test
    fun swapWithActive_swapsStates() = runTest {
        val dao = FakeDao()
        var broadcasts = 0
        val repo = NoteRepository(dao, FakePrefs(), { broadcasts++ }, { 10_000L }, backgroundScope, 0L)

        repo.saveActive("active")
        val archivedId = dao.insertArchived("archived")
        advanceUntilIdle()
        broadcasts = 0

        repo.swapWithActive(archivedId)
        advanceUntilIdle()

        assertEquals("archived", dao.getActive()?.content)
        assertEquals(Note.STATE_ARCHIVED, dao.getById(1L)?.state)
        assertEquals(1, broadcasts)
    }

    @Test
    fun swapWithActive_noOpForUnknownOrActiveId() = runTest {
        val dao = FakeDao()
        var broadcasts = 0
        val repo = NoteRepository(dao, FakePrefs(), { broadcasts++ }, { 10_000L }, backgroundScope, 0L)

        repo.saveActive("active")
        advanceUntilIdle()
        broadcasts = 0

        repo.swapWithActive(999L)
        repo.swapWithActive(1L)
        advanceUntilIdle()

        assertEquals("active", dao.getActive()?.content)
        assertEquals(0, broadcasts)
    }

    @Test
    fun replaceActive_replacesActiveWithArchived() = runTest {
        val dao = FakeDao()
        var broadcasts = 0
        val repo = NoteRepository(dao, FakePrefs(), { broadcasts++ }, { 10_000L }, backgroundScope, 0L)

        repo.saveActive("active")
        val archivedId = dao.insertArchived("archived")
        val activeId = dao.getActive()?.id
        advanceUntilIdle()
        broadcasts = 0

        repo.replaceActive(archivedId)
        advanceUntilIdle()

        assertEquals("archived", dao.getActive()?.content)
        assertNull(dao.getById(activeId!!))
        assertEquals(1, broadcasts)
    }

    @Test
    fun replaceActive_noOpForNonArchived() = runTest {
        val dao = FakeDao()
        var broadcasts = 0
        val repo = NoteRepository(dao, FakePrefs(), { broadcasts++ }, { 10_000L }, backgroundScope, 0L)

        repo.saveActive("active")
        advanceUntilIdle()
        broadcasts = 0

        repo.replaceActive(1L)
        repo.replaceActive(999L)
        advanceUntilIdle()

        assertEquals("active", dao.getActive()?.content)
        assertEquals(0, broadcasts)
    }

    @Test
    fun clearArchived_removesArchivedAndBroadcasts() = runTest {
        val dao = FakeDao()
        var broadcasts = 0
        val repo = NoteRepository(dao, FakePrefs(), { broadcasts++ }, { 10_000L }, backgroundScope, 0L)

        dao.insertArchived("a")
        dao.insertArchived("b")
        repo.clearArchived()
        advanceUntilIdle()

        assertEquals(1, broadcasts)
    }

    @Test
    fun saves_coalesceToLeadingPlusTrailing() = runTest {
        val dao = FakeDao()
        val seen = mutableListOf<String>()
        var now = 10_000L
        val repo = NoteRepository(
            dao, FakePrefs(),
            { seen += dao.getActive()?.content.orEmpty() },
            { now },
            backgroundScope,
            3000L
        )

        // Leading: first save in a quiet window notifies at once.
        repo.saveActive("a")
        advanceUntilIdle()
        assertEquals(listOf("a"), seen)

        // Two more saves inside the window collapse into one trailing
        // broadcast instead of two full Glance rebuilds.
        now = 10_100L
        repo.saveActive("b")
        now = 10_200L
        repo.saveActive("c")
        advanceUntilIdle()
        assertEquals(listOf("a"), seen)

        advanceTimeBy(3000L)
        advanceUntilIdle()
        assertEquals(listOf("a", "c"), seen)
        assertEquals("c", dao.getActive()?.content)
    }

    @Test
    fun concurrentSaves_keepSingleActiveRow() = runTest {
        val dao = FakeDao()
        val repo = NoteRepository(dao, FakePrefs(), {}, { 10_000L }, backgroundScope, 0L)

        launch { repo.saveActive("a") }
        launch { repo.saveActive("b") }
        advanceUntilIdle()

        assertEquals(1, dao.activeCount())
    }
}
