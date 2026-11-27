package com.yungsamd17.singlenote

import com.yungsamd17.singlenote.data.BackupStore
import com.yungsamd17.singlenote.data.Note
import com.yungsamd17.singlenote.ui.BackupViewModel
import com.yungsamd17.singlenote.util.BackupSelection
import com.yungsamd17.singlenote.util.RestoreSelection
import com.yungsamd17.singlenote.util.buildArchiveJson
import com.yungsamd17.singlenote.util.buildBackupJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers backup orchestration against a fake [BackupStore] — no Android. */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {

    private class FakeStore : BackupStore {
        var active: Note? = null
        val archived = mutableListOf<Note>()
        val settings = mutableMapOf<String, Any>()
        val widgets = mutableMapOf<String, Any>()
        var language = ""

        var flushes = 0
        var refreshes = 0
        var replaced: Pair<Note?, List<Note>>? = null
        val restoredActive = mutableListOf<Note>()
        var appliedSettings: Map<String, Any>? = null
        var appliedWidgets: Map<String, Any>? = null
        var appliedLanguage: String? = null

        override suspend fun activeNote(): Note? = active

        override suspend fun archivedNotes(): List<Note> = archived.toList()

        override suspend fun settings(): Map<String, Any> = settings.toMap()

        override suspend fun widgetStyles(): Map<String, Any> = widgets.toMap()

        override suspend fun languageTag(): String = language

        override suspend fun importArchived(notes: List<Note>) {
            archived.addAll(notes)
        }

        override suspend fun restoreActive(note: Note) {
            restoredActive += note
            active = note
        }

        override suspend fun replaceNotes(active: Note?, archived: List<Note>) {
            replaced = active to archived
        }

        override suspend fun applySettings(values: Map<String, Any>) {
            appliedSettings = values
        }

        override suspend fun applyWidgetStyles(values: Map<String, Any>) {
            appliedWidgets = values
        }

        override suspend fun applyLanguage(tag: String) {
            appliedLanguage = tag
        }

        override suspend fun flushPendingNote() {
            flushes++
        }

        override suspend fun refreshSurfaces() {
            refreshes++
        }
    }

    private fun note(content: String, createdAt: Long = 1L) =
        Note(content = content, createdAt = createdAt, updatedAt = createdAt, state = Note.STATE_ARCHIVED)

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.installMain() {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    }

    @Test
    fun createBackupIncludesOnlyTheSelectedCategories() = runTest {
        installMain()
        val store = FakeStore().apply {
            active = Note(content = "current", createdAt = 1L, updatedAt = 1L)
            archived += note("one")
            settings["theme_mode"] = "dark"
            widgets["remembered"] = "light"
            language = "fr-FR"
        }
        val vm = BackupViewModel(store)
        val onlySettings = BackupSelection(false, false, true, false)

        val json = JSONObject(vm.createBackup(onlySettings))

        assertFalse(json.has("notes"))
        assertFalse(json.has("widget"))
        assertEquals("dark", json.getJSONObject("settings").getString("theme_mode"))
        assertEquals("fr-FR", json.getString("language"))
    }

    @Test
    fun mergeRestoreKeepsExistingNotesAndDeduplicates() = runTest {
        installMain()
        val store = FakeStore().apply { archived += note("same") }
        val vm = BackupViewModel(store)
        val raw =
            buildBackupJson(
                com.yungsamd17.singlenote.util.BackupSnapshot(
                    active = Note(content = "backed up", createdAt = 2L, updatedAt = 2L),
                    archived = listOf(note("same"), note("fresh", 3L)),
                    settings = mapOf("theme_mode" to "dark"),
                    widgetStyles = mapOf("remembered" to "dark"),
                    languageTag = "de-DE",
                ),
                savedAt = 10L,
            )

        val result = vm.restore(raw, RestoreSelection())

        assertEquals(1, result.archivedAdded)
        assertEquals(1, result.archivedSkipped)
        assertTrue(result.activeRestored)
        assertEquals("backed up", store.active?.content)
        assertEquals(listOf("same", "fresh"), store.archived.map { it.content })
        assertEquals(mapOf("theme_mode" to "dark"), store.appliedSettings)
        assertEquals("de-DE", store.appliedLanguage)
        assertEquals(1, store.flushes)
        assertEquals(1, store.refreshes)
    }

    @Test
    fun aNoteInTheEditorIsNotOverwrittenByAMerge() = runTest {
        installMain()
        val store = FakeStore().apply {
            active = Note(content = "in progress", createdAt = 9L, updatedAt = 9L)
        }
        val vm = BackupViewModel(store)
        val raw =
            buildBackupJson(
                com.yungsamd17.singlenote.util.BackupSnapshot(
                    active = Note(content = "backed up", createdAt = 2L, updatedAt = 2L),
                ),
                savedAt = 10L,
            )

        val result = vm.restore(raw, RestoreSelection())

        assertFalse(result.activeRestored)
        assertTrue(store.restoredActive.isEmpty())
        assertEquals("in progress", store.active?.content)
    }

    @Test
    fun replaceRestoreSwapsBothNoteSetsInOneCall() = runTest {
        installMain()
        val store = FakeStore().apply {
            active = Note(content = "old", createdAt = 1L, updatedAt = 1L)
            archived += note("old archived")
        }
        val vm = BackupViewModel(store)
        val raw =
            buildBackupJson(
                com.yungsamd17.singlenote.util.BackupSnapshot(
                    active = Note(content = "new", createdAt = 2L, updatedAt = 2L),
                    archived = listOf(note("new archived", 3L)),
                ),
                savedAt = 10L,
            )

        val result = vm.restore(raw, RestoreSelection(replaceNotes = true))

        assertEquals("new", store.replaced?.first?.content)
        assertEquals(listOf("new archived"), store.replaced?.second?.map { it.content })
        assertEquals(0, result.archivedSkipped)
        assertTrue(store.restoredActive.isEmpty())
    }

    @Test
    fun restoringOnlySettingsLeavesNotesAlone() = runTest {
        installMain()
        val store = FakeStore().apply { archived += note("keep me") }
        val vm = BackupViewModel(store)
        val raw =
            buildBackupJson(
                com.yungsamd17.singlenote.util.BackupSnapshot(
                    active = Note(content = "backed up", createdAt = 2L, updatedAt = 2L),
                    archived = listOf(note("fresh")),
                    settings = mapOf("amoled" to true),
                    widgetStyles = mapOf("remembered" to "dark"),
                ),
                savedAt = 10L,
            )

        vm.restore(raw, RestoreSelection(notes = false, widgetSettings = false))

        assertNull(store.replaced)
        assertTrue(store.restoredActive.isEmpty())
        assertEquals(listOf("keep me"), store.archived.map { it.content })
        assertEquals(mapOf("amoled" to true), store.appliedSettings)
        assertNull(store.appliedWidgets)
    }

    @Test
    fun replacingFromAnActiveOnlyBackupKeepsTheArchive() = runTest {
        installMain()
        val store = FakeStore().apply {
            active = Note(content = "old active", createdAt = 1L, updatedAt = 1L)
            archived += note("keep archived")
        }
        val vm = BackupViewModel(store)
        val raw =
            buildBackupJson(
                com.yungsamd17.singlenote.util.BackupSnapshot(
                    active = Note(content = "new active", createdAt = 2L, updatedAt = 2L),
                ),
                BackupSelection(true, false, false, false),
                savedAt = 10L,
            )

        vm.restore(raw, RestoreSelection(replaceNotes = true))

        assertEquals("new active", store.replaced?.first?.content)
        assertEquals(listOf("keep archived"), store.replaced?.second?.map { it.content })
    }

    @Test
    fun aSettingsOnlyBackupNeverDeletesNotes() = runTest {
        installMain()
        val store = FakeStore().apply {
            active = Note(content = "keep", createdAt = 1L, updatedAt = 1L)
            archived += note("keep archived")
        }
        val vm = BackupViewModel(store)
        val raw =
            buildBackupJson(
                com.yungsamd17.singlenote.util.BackupSnapshot(settings = mapOf("theme_mode" to "dark")),
                BackupSelection(false, false, true, false),
                savedAt = 10L,
            )

        vm.restore(raw, RestoreSelection(replaceNotes = true))

        assertNull(store.replaced)
        assertEquals("keep", store.active?.content)
        assertEquals(listOf("keep archived"), store.archived.map { it.content })
        assertEquals(mapOf("theme_mode" to "dark"), store.appliedSettings)
    }

    @Test
    fun legacyArchivesOnlyAddNotes() = runTest {
        installMain()
        val store = FakeStore().apply { active = Note(content = "current", createdAt = 1L, updatedAt = 1L) }
        val vm = BackupViewModel(store)
        val raw = buildArchiveJson(listOf(note("legacy")))

        val result = vm.restore(raw, RestoreSelection(replaceNotes = true))

        assertEquals(1, result.archivedAdded)
        assertEquals(listOf("legacy"), store.archived.map { it.content })
        assertNull(store.replaced)
        assertNull(store.appliedSettings)
    }

    @Test
    fun refreshCountsNotesAndWidgetSets() = runTest {
        installMain()
        val store = FakeStore().apply {
            active = Note(content = "current", createdAt = 1L, updatedAt = 1L)
            archived += note("one")
            settings["theme_mode"] = "dark"
            widgets["remembered"] = "dark"
            widgets["7"] = "light"
            widgets["unknown.key"] = "dark"
        }
        val vm = BackupViewModel(store)

        vm.refresh()
        advanceUntilIdle()

        val preview = vm.preview.value
        assertTrue(preview.activeNote)
        assertEquals(1, preview.archivedCount)
        assertEquals(1, preview.settingsCount)
        assertEquals(2, preview.widgetCount)
    }

    @Test
    fun backupFilesAreRecognisedBeforeLegacyOnes() = runTest {
        val store = FakeStore()
        val vm = BackupViewModel(store)
        val raw =
            buildBackupJson(
                com.yungsamd17.singlenote.util.BackupSnapshot(settings = mapOf("theme_mode" to "dark")),
                savedAt = 10L,
            )

        assertTrue(com.yungsamd17.singlenote.util.isFullBackup(raw))
        assertFalse(com.yungsamd17.singlenote.util.isFullBackup(buildArchiveJson(emptyList())))

        // A malformed JSON file must not be silently treated as one text note.
        assertFalse(com.yungsamd17.singlenote.util.isFullBackup("{oops"))
        org.junit.Assert.assertThrows(com.yungsamd17.singlenote.util.ArchiveFormatException::class.java) {
            com.yungsamd17.singlenote.util.parseBackupFile("{oops")
        }
    }
}
