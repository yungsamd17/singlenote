package com.yungsamd17.singlenote

import com.yungsamd17.singlenote.data.Note
import com.yungsamd17.singlenote.data.NotePreferences.Companion.FONT_DEFAULT
import com.yungsamd17.singlenote.data.NotePreferences.Companion.SIZE_MEDIUM
import com.yungsamd17.singlenote.data.NotePreferences.Companion.THEME_SYSTEM
import com.yungsamd17.singlenote.data.NoteStore
import com.yungsamd17.singlenote.ui.NoteViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NoteViewModelTest {

    private class FakeNoteStore : NoteStore {
        override val activeNote = MutableStateFlow<Note?>(null)
        override val pinned = MutableStateFlow(false)
        override val themeMode = MutableStateFlow(THEME_SYSTEM)
        override val fontFamily = MutableStateFlow(FONT_DEFAULT)
        override val textSize = MutableStateFlow(SIZE_MEDIUM)

        var savedContent: String? = null
        var archiveRequested = false

        override suspend fun getActive(): Note? = activeNote.value

        override suspend fun saveActive(content: String) {
            savedContent = content
            if (activeNote.value == null && content.isNotBlank()) {
                activeNote.value = Note(id = 1, content = content, createdAt = 0, updatedAt = 0)
            }
        }

        override suspend fun archiveActive() {
            archiveRequested = true
            activeNote.value = null
        }

        override suspend fun setPinned(value: Boolean) {
            pinned.value = value
        }

        override suspend fun setThemeMode(value: String) {}
        override suspend fun setFontFamily(value: String) {}
        override suspend fun setTextSize(value: String) {}
    }

    @Before
    fun setUp() {
        // Each test installs Main backed by its own runTest scheduler.
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.installMain() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    @Test
    fun typing_savesAfterDebounce() = runTest {
        installMain()
        val store = FakeNoteStore()
        val vm = NoteViewModel(store)
        advanceUntilIdle()

        vm.onTextChange("hello")
        advanceTimeBy(100)
        assertEquals(null, store.savedContent)

        advanceTimeBy(600)
        advanceUntilIdle()
        assertEquals("hello", store.savedContent)
    }

    @Test
    fun rapidTyping_debouncesToSingleSaveOfLatestText() = runTest {
        installMain()
        val store = FakeNoteStore()
        val vm = NoteViewModel(store)
        advanceUntilIdle()

        vm.onTextChange("a")
        advanceTimeBy(200)
        vm.onTextChange("ab")
        advanceTimeBy(200)
        vm.onTextChange("abc")
        advanceUntilIdle()

        assertEquals("abc", store.savedContent)
    }

    @Test
    fun archive_persistsThenClearsEditor() = runTest {
        installMain()
        val store = FakeNoteStore()
        val vm = NoteViewModel(store)
        advanceUntilIdle()

        vm.onTextChange("to do")
        advanceUntilIdle()
        vm.archiveCurrent()
        advanceUntilIdle()

        assertTrue(store.archiveRequested)
        assertEquals("", vm.text.value)
    }

    @Test
    fun init_adoptsExistingActiveNote() = runTest {
        installMain()
        val store = FakeNoteStore()
        store.activeNote.value = Note(id = 7, content = "existing", createdAt = 0, updatedAt = 0)
        val vm = NoteViewModel(store)
        advanceUntilIdle()

        assertEquals("existing", vm.text.value)
    }
}
