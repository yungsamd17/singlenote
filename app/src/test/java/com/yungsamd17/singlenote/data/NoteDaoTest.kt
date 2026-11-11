package com.yungsamd17.singlenote.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the real Room DAO on an in-memory database, so the unique
 * activeSlot guard and the transactional state flips are covered instead of
 * only faked at the ViewModel boundary.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: NoteDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = database.noteDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun uniqueIndexRejectsSecondActiveRow() {
        runBlocking { dao.insert(note("first")) }

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { dao.insert(note("second")) }
        }

        assertTrue(runBlocking { dao.observeArchived().first() }.isEmpty())
        assertEquals("first", runBlocking { dao.getActive() }?.content)
    }

    @Test
    fun saveActiveContentCreatesUpdatesAndSkipsNoOps() = runBlocking {
        assertFalse(dao.saveActiveContent("   ", now = 5))

        assertTrue(dao.saveActiveContent("hello", now = 10))
        val created = dao.getActive()!!
        assertEquals("hello", created.content)
        assertEquals(10L, created.updatedAt)
        assertEquals(10L, created.createdAt)
        assertNull(created.editedAt)
        assertNull(created.archivedAt)

        // Identical content is a no-op so the widget is not refreshed.
        assertFalse(dao.saveActiveContent("hello", now = 20))
        assertEquals(10L, dao.getActive()!!.updatedAt)

        assertTrue(dao.saveActiveContent("changed", now = 30))
        assertEquals(30L, dao.getActive()!!.updatedAt)
    }

    @Test
    fun archiveActiveNoteClearsTheSlotAndAllowsANewActiveNote() = runBlocking {
        dao.saveActiveContent("archived soon", now = 10)

        assertTrue(dao.archiveActiveNote())
        assertNull(dao.getActive())

        val archived = dao.observeArchived().first()
        assertEquals(1, archived.size)
        assertNull(archived.single().activeSlot)
        assertEquals(Note.STATE_ARCHIVED, archived.single().state)
        assertNotNull(archived.single().archivedAt)

        // The single ACTIVE slot is free again.
        assertTrue(dao.saveActiveContent("next", now = 20))
        assertEquals("next", dao.getActive()?.content)
    }

    @Test
    fun restoreIfNoActiveReplacesOnlyABlankActiveNote() = runBlocking {
        dao.saveActiveContent("keep me", now = 10)
        assertTrue(dao.archiveActiveNote())
        val archivedId = dao.observeArchived().first().single().id

        // A non-blank active note wins; the archived one stays archived.
        dao.saveActiveContent("current", now = 20)
        assertFalse(dao.restoreIfNoActive(archivedId))
        assertEquals("current", dao.getActive()?.content)
        assertEquals(Note.STATE_ARCHIVED, dao.getById(archivedId)?.state)

        // A blank active note is discarded in favour of the archived one.
        dao.deleteById(dao.getActive()!!.id)
        dao.insert(note("", state = Note.STATE_ACTIVE))
        assertTrue(dao.restoreIfNoActive(archivedId))
        assertEquals("keep me", dao.getActive()?.content)
        assertEquals(Note.STATE_ACTIVE, dao.getById(archivedId)?.state)
    }

    @Test
    fun restoreIfNoActiveWorksWithNoActiveRow() = runBlocking {
        dao.saveActiveContent("parked", now = 10)
        dao.archiveActiveNote()
        val archivedId = dao.observeArchived().first().single().id

        assertTrue(dao.restoreIfNoActive(archivedId))
        assertEquals("parked", dao.getActive()?.content)
        assertTrue(dao.observeArchived().first().isEmpty())
    }

    @Test
    fun swapActiveWithArchivesCurrentAndRestoresTarget() = runBlocking {
        dao.saveActiveContent("current", now = 10)
        dao.archiveActiveNote()
        val archivedId = dao.observeArchived().first().single().id
        dao.saveActiveContent("next", now = 20)

        assertTrue(dao.swapActiveWith(archivedId))

        assertEquals("current", dao.getActive()?.content)
        assertEquals(listOf("next"), dao.observeArchived().first().map { it.content })
    }

    @Test
    fun replaceActiveWithDeletesCurrentInsteadOfArchivingIt() = runBlocking {
        dao.saveActiveContent("doomed", now = 10)
        dao.archiveActiveNote()
        val archivedId = dao.observeArchived().first().single().id
        dao.saveActiveContent("discarded", now = 20)

        assertTrue(dao.replaceActiveWith(archivedId))

        assertEquals("doomed", dao.getActive()?.content)
        assertTrue(dao.observeArchived().first().isEmpty())
    }

    @Test
    fun restoreWithUndoReversesSwapAndReplace() = runBlocking {
        dao.saveActiveContent("target", now = 10)
        dao.archiveActiveNote()
        val archivedId = dao.observeArchived().first().single().id
        dao.saveActiveContent("previous", now = 20)

        val undo = dao.restoreWithUndo(archivedId, replace = false)!!
        assertEquals("target", dao.getActive()?.content)
        assertTrue(dao.undoRestore(undo))
        assertEquals("previous", dao.getActive()?.content)
        assertEquals(Note.STATE_ARCHIVED, dao.getById(archivedId)?.state)

        val replaceUndo = dao.restoreWithUndo(archivedId, replace = true)!!
        assertEquals("target", dao.getActive()?.content)
        assertTrue(dao.undoRestore(replaceUndo))
        assertEquals("previous", dao.getActive()?.content)
    }

    @Test
    fun undoRestoreRefusesWhenTheRestoredNoteIsNoLongerActive() = runBlocking {
        dao.saveActiveContent("target", now = 10)
        dao.archiveActiveNote()
        val archivedId = dao.observeArchived().first().single().id
        dao.saveActiveContent("previous", now = 20)

        val undo = dao.restoreWithUndo(archivedId, replace = false)!!
        // The user edited/switched again before Undo; the snapshot is stale.
        dao.archiveActiveNote()
        assertFalse(dao.undoRestore(undo))
    }

    @Test
    fun importArchivedAlwaysForcesArchiveStateAndClearsSlot() = runBlocking {
        dao.importArchived(
            listOf(
                Note(content = "imported", createdAt = 1, updatedAt = 2, state = Note.STATE_ACTIVE),
                Note(content = "already archived", createdAt = 3, updatedAt = 4, state = Note.STATE_ARCHIVED),
            )
        )

        assertNull(dao.getActive())
        val archived = dao.observeArchived().first()
        assertEquals(listOf("already archived", "imported"), archived.map { it.content })
        assertTrue(archived.all { it.activeSlot == null })
    }

    @Test
    fun deleteArchivedNoteIgnoresARowRestoredByUndo() = runBlocking {
        dao.saveActiveContent("target", now = 10)
        dao.archiveActiveNote()
        val archivedId = dao.observeArchived().first().single().id
        assertTrue(dao.restoreIfNoActive(archivedId))

        dao.deleteArchivedNote(archivedId)

        assertNotNull(dao.getById(archivedId))
        assertEquals(Note.STATE_ACTIVE, dao.getById(archivedId)?.state)
    }

    @Test
    fun deleteArchivedClearsOnlyArchivedRows() = runBlocking {
        dao.saveActiveContent("archived later", now = 10)
        dao.archiveActiveNote()
        dao.saveActiveContent("new active", now = 20)
        dao.insert(
            Note(
                content = "archived",
                createdAt = 1,
                updatedAt = 2,
                state = Note.STATE_ARCHIVED,
                activeSlot = null,
            )
        )

        dao.deleteArchived()

        assertEquals("new active", dao.getActive()?.content)
        assertTrue(dao.observeArchived().first().isEmpty())
    }

    private fun note(content: String, state: Int = Note.STATE_ACTIVE) = Note(
        content = content,
        createdAt = 1,
        updatedAt = 2,
        state = state,
        activeSlot = if (state == Note.STATE_ACTIVE) Note.ACTIVE_SLOT else null,
    )
}
