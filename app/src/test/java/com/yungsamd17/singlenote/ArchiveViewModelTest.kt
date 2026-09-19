package com.yungsamd17.singlenote

import com.yungsamd17.singlenote.data.ArchiveStore
import com.yungsamd17.singlenote.data.Note
import com.yungsamd17.singlenote.ui.ArchiveEvent
import com.yungsamd17.singlenote.ui.ArchiveViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers ArchiveViewModel restore conflict, swap/replace and clear paths
 * against a fake [ArchiveStore].
 */
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
            active = note.copy(state = Note.STATE_ACTIVE)
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
            active = archived.copy(state = Note.STATE_ACTIVE)
            archivedNotes.value = archivedNotes.value.map {
                if (it.id == noteId) current.copy(state = Note.STATE_ARCHIVED) else it
            }
        }

        override suspend fun replaceActive(noteId: Long) {
            replacedWith = noteId
            val archived = archivedNotes.value.find { it.id == noteId } ?: return
            active = archived.copy(state = Note.STATE_ACTIVE)
            archivedNotes.value = archivedNotes.value.filterNot { it.id == noteId }
        }

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
    fun swap_clearsConflictSwapsStatesAndEmitsRestored() = runTest {
        installMain()
        val note = archived(1, "old")
        val store = FakeArchiveStore().apply {
            active = Note(id = 2, content = "current", createdAt = 0, updatedAt = 0)
            archivedNotes.value = listOf(note)
        }
        val vm = ArchiveViewModel(store)
        vm.restore(note)
        advanceUntilIdle()
        val received = collectEvents(vm)

        vm.swap(note)
        advanceUntilIdle()

        assertEquals(1L, store.swappedWith)
        assertEquals("old", store.active?.content)
        assertEquals(listOf(2L), store.archivedNotes.value.map { it.id })
        assertNull(vm.restoreConflict)
        assertEquals(listOf(ArchiveEvent.Restored), received)
    }

    @Test
    fun replace_clearsConflictReplacesActiveAndEmitsRestored() = runTest {
        installMain()
        val note = archived(1, "old")
        val store = FakeArchiveStore().apply {
            active = Note(id = 2, content = "current", createdAt = 0, updatedAt = 0)
            archivedNotes.value = listOf(note)
        }
        val vm = ArchiveViewModel(store)
        vm.restore(note)
        advanceUntilIdle()
        val received = collectEvents(vm)

        vm.replace(note)
        advanceUntilIdle()

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
}
