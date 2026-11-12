package com.yungsamd17.singlenote

import com.yungsamd17.singlenote.data.ArchiveRestoreUndo
import com.yungsamd17.singlenote.data.ArchiveStore
import com.yungsamd17.singlenote.data.Note
import com.yungsamd17.singlenote.ui.ArchiveEvent
import com.yungsamd17.singlenote.ui.ArchiveViewModel
import com.yungsamd17.singlenote.ui.archiveSearchAvailable
import com.yungsamd17.singlenote.ui.filterArchivedNotes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers ArchiveViewModel restore conflict, swap/replace and clear paths
 * against a fake [ArchiveStore].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveViewModelTest {

    private class FakeArchiveStore : ArchiveStore {
        override val archivedNotes = MutableStateFlow<List<Note>>(emptyList())
        var active: Note? = null
        var swappedWith: Long? = null
        var replacedWith: Long? = null
        var cleared = false

        override suspend fun restore(noteId: Long): Boolean {
            if (active != null) return false
            val note = archivedNotes.value.find { it.id == noteId } ?: return false
            active = note.copy(state = Note.STATE_ACTIVE, activeSlot = Note.ACTIVE_SLOT)
            archivedNotes.value = archivedNotes.value.filterNot { it.id == noteId }
            return true
        }

        override suspend fun deleteArchived(noteId: Long) {
            archivedNotes.value = archivedNotes.value.filterNot { it.id == noteId }
        }

        override suspend fun hasActiveNote(): Boolean = active != null

        override suspend fun swapWithActive(noteId: Long) {
            swappedWith = noteId
            val current = active ?: return
            val archived = archivedNotes.value.find { it.id == noteId } ?: return
            active = archived.copy(state = Note.STATE_ACTIVE, activeSlot = Note.ACTIVE_SLOT)
            archivedNotes.value = archivedNotes.value.map {
                if (it.id == noteId) current.copy(state = Note.STATE_ARCHIVED, activeSlot = null) else it
            }
        }

        override suspend fun replaceActive(noteId: Long) {
            replacedWith = noteId
            val archived = archivedNotes.value.find { it.id == noteId } ?: return
            active = archived.copy(state = Note.STATE_ACTIVE, activeSlot = Note.ACTIVE_SLOT)
            archivedNotes.value = archivedNotes.value.filterNot { it.id == noteId }
        }

        override suspend fun restoreWithUndo(noteId: Long, replace: Boolean): ArchiveRestoreUndo? {
            val previous = active ?: return null
            if (archivedNotes.value.none { it.id == noteId }) return null
            if (replace) replaceActive(noteId) else swapWithActive(noteId)
            return ArchiveRestoreUndo(noteId, previous, replace)
        }

        override suspend fun undoRestore(undo: ArchiveRestoreUndo): Boolean {
            val incoming = active?.takeIf { it.id == undo.restoredNoteId } ?: return false
            archivedNotes.value = archivedNotes.value.filterNot { it.id == undo.previousActive.id } +
                incoming.copy(state = Note.STATE_ARCHIVED, activeSlot = null)
            active = undo.previousActive
            return true
        }

        override suspend fun importArchived(notes: List<Note>) { notes.forEach { archivedNotes.value = archivedNotes.value + it } }

        override suspend fun clearArchived() {
            cleared = true
            archivedNotes.value = emptyList()
        }
    }

    private fun archived(id: Long, content: String) =
        Note(id = id, content = content, createdAt = 0, updatedAt = 0, state = Note.STATE_ARCHIVED)

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.installMain() {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    }

    private fun TestScope.collectEvents(vm: ArchiveViewModel): MutableList<ArchiveEvent> {
        val received = mutableListOf<ArchiveEvent>()
        backgroundScope.launch { vm.events.collect { received += it } }
        return received
    }

    @Test
    fun deletingTwoNotesQuicklyKeepsBothHiddenAndDeletesBoth() = runTest {
        installMain()
        val first = archived(1, "first")
        val second = archived(2, "second")
        val store = FakeArchiveStore().apply { archivedNotes.value = listOf(first, second) }
        val vm = ArchiveViewModel(store)
        vm.deleteWithUndo(first)
        runCurrent()
        vm.deleteWithUndo(second)
        runCurrent()
        assertEquals(setOf(1L, 2L), vm.pendingDeleteIds.value)
        advanceTimeBy(ArchiveViewModel.UNDO_WINDOW_MS + 1)
        runCurrent()
        assertTrue(store.archivedNotes.value.isEmpty())
        assertTrue(vm.pendingDeleteIds.value.isEmpty())
    }

    @Test
    fun undoLatestDeletionDoesNotCancelEarlierDeletion() = runTest {
        installMain()
        val first = archived(1, "first")
        val second = archived(2, "second")
        val store = FakeArchiveStore().apply { archivedNotes.value = listOf(first, second) }
        val vm = ArchiveViewModel(store)
        vm.deleteWithUndo(first)
        runCurrent()
        vm.deleteWithUndo(second)
        runCurrent()
        vm.undoDelete()
        assertEquals(setOf(1L), vm.pendingDeleteIds.value)
        advanceTimeBy(ArchiveViewModel.UNDO_WINDOW_MS + 1)
        runCurrent()
        assertEquals(listOf(second), store.archivedNotes.value)
    }

    @Test
    fun restore_withoutActiveNote_emitsRestored() = runTest {
        installMain()
        val store = FakeArchiveStore().apply {
            archivedNotes.value = listOf(archived(1, "old"))
        }
        val vm = ArchiveViewModel(store)
        val received = collectEvents(vm)

        vm.restore(archived(1, "old"))
        advanceUntilIdle()

        assertEquals(listOf(ArchiveEvent.Restored), received)
        assertEquals("old", store.active?.content)
        assertNull(vm.restoreConflict)
    }

    @Test
    fun restore_withActiveNote_setsConflictWithoutEvent() = runTest {
        installMain()
        val note = archived(1, "old")
        val store = FakeArchiveStore().apply {
            active = Note(id = 2, content = "current", createdAt = 0, updatedAt = 0)
            archivedNotes.value = listOf(note)
        }
        val vm = ArchiveViewModel(store)
        val received = collectEvents(vm)

        vm.restore(note)
        advanceUntilIdle()

        assertEquals(note, vm.restoreConflict)
        assertTrue(received.isEmpty())
        assertEquals("current", store.active?.content)
    }

    @Test
    fun swap_clearsConflictSwapsStatesAndEmitsRestored() = runTest(UnconfinedTestDispatcher()) {
        // Unconfined Main + background: two sequential ViewModel actions
        // (restore-conflict, then swap) lose the second Channel event under
        // StandardTestDispatcher even with early subscription — the second
        // advance never resumes the collector. Unconfined runs sends and
        // collects eagerly, so no advance juggling is needed (advances kept
        // as harmless no-ops for symmetry with the other tests).
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val note = archived(1, "old")
        val store = FakeArchiveStore().apply {
            active = Note(id = 2, content = "current", createdAt = 0, updatedAt = 0)
            archivedNotes.value = listOf(note)
        }
        val vm = ArchiveViewModel(store)
        // Subscribe before any action: subscribing late (after restore)
        // races the Channel event and flakes under StandardTestDispatcher.
        val received = collectEvents(vm)

        vm.restore(note)
        advanceUntilIdle()
        // Conflict path emits nothing.
        assertTrue(received.isEmpty())

        vm.swap(note)
        advanceUntilIdle()

        val undo = vm.takeRestoreUndo()
        assertEquals(2L, undo?.previousActive?.id)
        assertEquals(false, undo?.replaced)
        assertNull(vm.takeRestoreUndo())
        assertEquals(1L, store.swappedWith)
        assertEquals("old", store.active?.content)
        assertEquals(listOf(2L), store.archivedNotes.value.map { it.id })
        assertNull(vm.restoreConflict)
        assertEquals(listOf(ArchiveEvent.Restored), received)
    }

    @Test
    fun replace_clearsConflictReplacesActiveAndEmitsRestored() = runTest(UnconfinedTestDispatcher()) {
        // Same two-action Channel race as swap above: Unconfined dispatches
        // eagerly so the replace event can't be lost between advances.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val note = archived(1, "old")
        val store = FakeArchiveStore().apply {
            active = Note(id = 2, content = "current", createdAt = 0, updatedAt = 0)
            archivedNotes.value = listOf(note)
        }
        val vm = ArchiveViewModel(store)
        // Subscribe before any action: subscribing late (after restore)
        // races the Channel event and flakes under StandardTestDispatcher.
        val received = collectEvents(vm)

        vm.restore(note)
        advanceUntilIdle()
        // Conflict path emits nothing.
        assertTrue(received.isEmpty())

        vm.replace(note)
        advanceUntilIdle()

        assertEquals(true, vm.takeRestoreUndo()?.replaced)
        assertNull(vm.takeRestoreUndo())
        assertEquals(1L, store.replacedWith)
        assertEquals("old", store.active?.content)
        assertTrue(store.archivedNotes.value.isEmpty())
        assertNull(vm.restoreConflict)
        assertEquals(listOf(ArchiveEvent.Restored), received)
    }

    @Test
    fun dismissRestoreConflict_clearsWithoutStoreCall() = runTest {
        installMain()
        val note = archived(1, "old")
        val store = FakeArchiveStore().apply {
            active = Note(id = 2, content = "current", createdAt = 0, updatedAt = 0)
            archivedNotes.value = listOf(note)
        }
        val vm = ArchiveViewModel(store)
        val received = collectEvents(vm)

        vm.restore(note)
        advanceUntilIdle()
        vm.dismissRestoreConflict()
        advanceUntilIdle()

        assertNull(vm.restoreConflict)
        assertNull(store.swappedWith)
        assertNull(store.replacedWith)
        assertTrue(received.isEmpty())
    }

    @Test
    fun delete_removesNoteWithoutEvent() = runTest {
        installMain()
        val note = archived(1, "old")
        val store = FakeArchiveStore().apply {
            archivedNotes.value = listOf(note)
        }
        val vm = ArchiveViewModel(store)
        val received = collectEvents(vm)

        vm.delete(note)
        advanceUntilIdle()

        assertTrue(store.archivedNotes.value.isEmpty())
        assertTrue(received.isEmpty())
    }

    @Test
    fun clearArchive_clearsAndEmitsCleared() = runTest {
        installMain()
        val store = FakeArchiveStore().apply {
            archivedNotes.value = listOf(archived(1, "a"), archived(2, "b"))
        }
        val vm = ArchiveViewModel(store)
        val received = collectEvents(vm)

        vm.clearArchive()
        advanceUntilIdle()

        assertTrue(store.cleared)
        assertTrue(store.archivedNotes.value.isEmpty())
        assertEquals(listOf(ArchiveEvent.Cleared), received)
    }

    // ------------------------------------------------------------- search ---

    @Test
    fun setQuery_updatesTheRawQueryImmediately() = runTest {
        installMain()
        val vm = ArchiveViewModel(FakeArchiveStore())

        vm.setQuery("rent")

        assertEquals("rent", vm.query.value)
        vm.setQuery("")
        assertEquals("", vm.query.value)
    }

    @Test
    fun searchQuery_publishesOnlyAfterTheDebounceWindow() = runTest {
        installMain()
        val vm = ArchiveViewModel(FakeArchiveStore())
        val seen = mutableListOf<String>()
        backgroundScope.launch { vm.searchQuery.collect { seen += it } }
        runCurrent()

        vm.setQuery("a")
        advanceTimeBy(ArchiveViewModel.SEARCH_DEBOUNCE_MS - 10)
        runCurrent()
        assertTrue("still debouncing, saw $seen", seen.none { it == "a" })

        advanceTimeBy(20)
        runCurrent()
        assertEquals("a", seen.last())
    }

    @Test
    fun searchQuery_keepsOnlyTheLastValueOfAFastBurst() = runTest {
        installMain()
        val vm = ArchiveViewModel(FakeArchiveStore())
        val seen = mutableListOf<String>()
        backgroundScope.launch { vm.searchQuery.collect { seen += it } }
        runCurrent()

        vm.setQuery("a")
        advanceTimeBy(50)
        runCurrent()
        vm.setQuery("ab")
        advanceTimeBy(50)
        runCurrent()
        vm.setQuery("abc")
        advanceTimeBy(ArchiveViewModel.SEARCH_DEBOUNCE_MS + 1)
        runCurrent()

        // The initial value, then one publish for the whole burst.
        assertEquals(listOf("", "abc"), seen)
    }

    @Test
    fun filterArchivedNotes_matchesCaseInsensitivelyAndTrimsTheQuery() {
        val notes = listOf(
            archived(1, "Buy Milk"),
            archived(2, "call the plumber"),
            archived(3, "milk and eggs"),
        )

        assertEquals(
            listOf(1L, 3L),
            filterArchivedNotes(notes, "  MILK ").map { it.id },
        )
    }

    @Test
    fun filterArchivedNotes_blankQueryReturnsTheListUnchanged() {
        val notes = listOf(archived(1, "one"), archived(2, "two"))

        assertEquals(notes, filterArchivedNotes(notes, ""))
        assertEquals(notes, filterArchivedNotes(notes, "   "))
    }

    @Test
    fun filterArchivedNotes_keepsTheInputOrderWhenNothingMatches() {
        val notes = listOf(archived(1, "alpha"), archived(2, "beta"))

        assertEquals(emptyList<Note>(), filterArchivedNotes(notes, "gamma"))
    }

    @Test
    fun theSearchButtonOnlyAppearsWithMoreThanOneNote() {
        // With no note or a single note there is nothing to filter, so the app
        // bar leaves the search action out.
        assertFalse(archiveSearchAvailable(0))
        assertFalse(archiveSearchAvailable(1))
        assertTrue(archiveSearchAvailable(2))
        assertTrue(archiveSearchAvailable(25))
    }
}
