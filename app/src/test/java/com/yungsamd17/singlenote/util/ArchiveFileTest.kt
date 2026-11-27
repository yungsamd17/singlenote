package com.yungsamd17.singlenote.util

import com.yungsamd17.singlenote.data.Note
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the versioned archive JSON: round trips, malformed files and files
 * from another app or a newer archive version.
 */
class ArchiveFileTest {

    private val notes = listOf(
        Note(
            content = "first note\nwith a second line",
            createdAt = 1_000L,
            updatedAt = 2_000L,
            editedAt = 2_500L,
            archivedAt = 3_000L,
            state = Note.STATE_ARCHIVED,
            activeSlot = null,
        ),
        Note(
            content = "second note",
            createdAt = 4_000L,
            updatedAt = 5_000L,
            state = Note.STATE_ARCHIVED,
            activeSlot = null,
        ),
    )

    @Test
    fun exportCarriesFormatVersionAndNotes() {
        val json = JSONObject(buildArchiveJson(notes))

        assertEquals(ARCHIVE_FORMAT, json.getString("format"))
        assertEquals(ARCHIVE_VERSION, json.getInt("version"))
        assertEquals(2, json.getJSONArray("notes").length())
        assertEquals(
            "first note",
            json.getJSONArray("notes").getJSONObject(0).getString("title"),
        )
    }

    @Test
    fun roundTripKeepsContentAndTimestamps() {
        val parsed = parseArchiveJson(buildArchiveJson(notes))

        assertEquals(2, parsed.size)
        assertEquals("first note\nwith a second line", parsed[0].content)
        assertEquals(1_000L, parsed[0].createdAt)
        assertEquals(2_000L, parsed[0].updatedAt)
        assertEquals(2_500L, parsed[0].editedAt)
        assertEquals(3_000L, parsed[0].archivedAt)
        // Imported notes are always archived, never active.
        assertEquals(Note.STATE_ARCHIVED, parsed[0].state)
        assertNull(parsed[0].activeSlot)

        assertEquals("second note", parsed[1].content)
        assertNull(parsed[1].editedAt)
        assertNull(parsed[1].archivedAt)
    }

    @Test
    fun emptyArchiveRoundTripsToNoNotes() {
        assertTrue(parseArchiveJson(buildArchiveJson(emptyList())).isEmpty())
    }

    @Test
    fun rejectsMalformedJson() {
        assertThrows(ArchiveFormatException::class.java) { parseArchiveJson("{not json") }
    }

    @Test
    fun rejectsForeignFormat() {
        val json = JSONObject()
            .put("format", "some-other-app")
            .put("version", ARCHIVE_VERSION)
            .put("notes", org.json.JSONArray())

        assertThrows(ArchiveFormatException::class.java) { parseArchiveJson(json.toString()) }
    }

    @Test
    fun rejectsUnsupportedVersion() {
        val json = JSONObject()
            .put("format", ARCHIVE_FORMAT)
            .put("version", ARCHIVE_VERSION + 1)
            .put("notes", org.json.JSONArray())

        assertThrows(ArchiveFormatException::class.java) { parseArchiveJson(json.toString()) }
    }

    @Test
    fun rejectsMissingNotesArray() {
        val json = JSONObject()
            .put("format", ARCHIVE_FORMAT)
            .put("version", ARCHIVE_VERSION)

        assertThrows(ArchiveFormatException::class.java) { parseArchiveJson(json.toString()) }
    }

    @Test
    fun rejectsNoteWithoutContent() {
        val json = JSONObject()
            .put("format", ARCHIVE_FORMAT)
            .put("version", ARCHIVE_VERSION)
            .put(
                "notes",
                org.json.JSONArray().put(
                    JSONObject().put("createdAt", 1L).put("updatedAt", 2L)
                )
            )

        assertThrows(ArchiveFormatException::class.java) { parseArchiveJson(json.toString()) }
    }

    @Test
    fun rejectsNoteWithoutTimestamps() {
        val json = JSONObject()
            .put("format", ARCHIVE_FORMAT)
            .put("version", ARCHIVE_VERSION)
            .put(
                "notes",
                org.json.JSONArray().put(JSONObject().put("content", "no timestamps"))
            )

        assertThrows(ArchiveFormatException::class.java) { parseArchiveJson(json.toString()) }
    }

    @Test
    fun textRoundTripKeepsNoteContent() {
        val parsed = parseArchiveText(buildArchiveText(notes), now = 9_000L)

        assertEquals(
            listOf("first note\nwith a second line", "second note"),
            parsed.map { it.content },
        )
        parsed.forEach {
            assertEquals(9_000L, it.createdAt)
            assertEquals(9_000L, it.updatedAt)
            assertEquals(9_000L, it.archivedAt)
            assertEquals(Note.STATE_ARCHIVED, it.state)
            assertNull(it.activeSlot)
        }
    }

    @Test
    fun textImportAcceptsWindowsLineEndings() {
        val parsed = parseArchiveText("first line\r\n\r\n---\r\n\r\nsecond line\r\n", 1L)

        assertEquals(listOf("first line", "second line"), parsed.map { it.content })
    }

    @Test
    fun textImportRejectsAnEmptyFile() {
        assertThrows(ArchiveFormatException::class.java) {
            parseArchiveText("  \n\n---\n\n ")
        }
    }

    @Test
    fun archiveSniffsBothFormatsWithoutHidingBrokenJson() {
        val json = parseArchive(buildArchiveJson(notes))
        assertEquals(2, json.size)
        assertEquals(1_000L, json[0].createdAt)

        assertEquals("single note", parseArchive("single note", now = 7L).single().content)
        assertThrows(ArchiveFormatException::class.java) { parseArchive("{not json") }
        assertTrue(isPlainTextArchive("  single note"))
        assertTrue(!isPlainTextArchive(buildArchiveJson(notes)))
    }

    @Test
    fun duplicateImportSkipsNotesAlreadyInTheArchive() {
        val duplicates = notes.map { it.copy(id = 0L) }
        val fresh = notes[0].copy(content = "new note", createdAt = 9_000L)

        val result = filterDuplicateNotes(duplicates + fresh, notes)

        assertEquals(listOf(fresh), result.accepted)
        assertEquals(2, result.skipped)
    }

    @Test
    fun duplicateImportAlsoSkipsDuplicatesInsideTheFile() {
        val result = filterDuplicateNotes(listOf(notes[0], notes[0]), emptyList())

        assertEquals(listOf(notes[0]), result.accepted)
        assertEquals(1, result.skipped)
    }

    @Test
    fun aDifferentCreationTimeIsNotADuplicate() {
        val changed = notes[0].copy(createdAt = 9_999L)

        val result = filterDuplicateNotes(listOf(changed), notes)

        assertEquals(listOf(changed), result.accepted)
        assertEquals(0, result.skipped)
    }

    @Test
    fun textImportMatchesOnContentOnly() {
        val existing = notes.map { it.copy(createdAt = 9_000L) }

        val result = filterDuplicateNotes(notes, existing, matchContentOnly = true)

        assertTrue(result.accepted.isEmpty())
        assertEquals(2, result.skipped)
    }

    // Full backups: same versioned-JSON discipline as archives, with sections
    // for the other stores the app keeps.

    private val snapshot =
        BackupSnapshot(
            active = Note(content = "current", createdAt = 10L, updatedAt = 20L),
            archived = notes,
            settings =
                mapOf(
                    "theme_mode" to "dark",
                    "pin_current_note" to true,
                    "text_size" to "huge",
                    "unknown_key" to "value",
                ),
            widgetStyles =
                mapOf(
                    "remembered" to "dark",
                    "remembered.accent" to "blue",
                    "remembered.titleSize" to 99,
                    "remembered.opacity" to 150,
                    "7" to "light",
                    "7.showTitle" to false,
                    "junk.key" to "dark",
                ),
            languageTag = "de-DE",
        )

    @Test
    fun fullBackupRoundTripsNotesSettingsWidgetsAndLanguage() {
        val parsed = parseBackup(buildBackupJson(snapshot, savedAt = 99L))

        assertEquals("current", parsed.active?.content)
        assertEquals(Note.STATE_ACTIVE, parsed.active?.state)
        assertEquals(Note.ACTIVE_SLOT, parsed.active?.activeSlot)
        assertEquals(notes.map { it.content }, parsed.archived.map { it.content })
        assertEquals(2, parsed.archived.size)
        assertEquals("dark", parsed.settings["theme_mode"])
        assertEquals(true, parsed.settings["pin_current_note"])
        assertFalse(parsed.settings.containsKey("text_size"))
        assertFalse(parsed.settings.containsKey("unknown_key"))
        assertEquals("blue", parsed.widgetStyles["remembered.accent"])
        assertEquals(24, parsed.widgetStyles["remembered.titleSize"])
        assertEquals(100, parsed.widgetStyles["remembered.opacity"])
        assertEquals("light", parsed.widgetStyles["7"])
        assertEquals(false, parsed.widgetStyles["7.showTitle"])
        assertFalse(parsed.widgetStyles.containsKey("junk.key"))
        assertEquals("de-DE", parsed.languageTag)
    }

    @Test
    fun aPartialBackupOmitsTheSectionsThatWereNotSelected() {
        val selection = BackupSelection(activeNote = true, archivedNotes = false, appSettings = false, widgetSettings = false)
        val root = JSONObject(buildBackupJson(snapshot, selection, savedAt = 99L))

        assertEquals(BACKUP_FORMAT, root.getString("format"))
        assertEquals(BACKUP_VERSION, root.getInt("version"))
        assertEquals(99L, root.getLong("savedAt"))
        assertTrue(root.has("notes"))
        assertFalse(root.has("settings"))
        assertFalse(root.has("widget"))
        assertFalse(root.has("language"))
        assertFalse(root.getJSONObject("notes").isNull("active"))
    }

    @Test
    fun fullBackupRejectsForeignFormatsVersionsAndBrokenJson() {
        val root = JSONObject(buildBackupJson(snapshot, savedAt = 99L))
        root.put("format", "singlenote-archive")
        assertThrows(BackupFormatException::class.java) { parseBackup(root.toString()) }

        root.put("format", BACKUP_FORMAT)
        root.put("version", BACKUP_VERSION + 1)
        assertThrows(BackupFormatException::class.java) { parseBackup(root.toString()) }

        assertThrows(BackupFormatException::class.java) { parseBackup("not json") }
    }

    @Test
    fun backupFileAcceptsLegacyArchivesAndText() {
        val legacy = parseBackupFile(buildArchiveJson(notes), now = 42L)
        assertTrue(legacy is BackupPayload.Notes)
        assertEquals(2, (legacy as BackupPayload.Notes).notes.size)
        assertFalse(legacy.plainText)

        val text = parseBackupFile(buildArchiveText(notes), now = 42L) as BackupPayload.Notes
        assertTrue(text.plainText)
        assertEquals(42L, text.notes.first().createdAt)
    }

    @Test
    fun backupsKeepBundledFontsAndNoteStylePreferences() {
        val values =
            mapOf(
                "font_family" to "app",
                "app_font" to "inter",
                "line_spacing" to "comfortable",
                "font_family_bogus" to "app",
                "line_spacing_bogus" to "huge",
            )

        val clean = sanitizeSettings(values)

        assertEquals("app", clean["font_family"])
        assertEquals("inter", clean["app_font"])
        assertEquals("comfortable", clean["line_spacing"])
        assertFalse(clean.containsKey("font_family_bogus"))
        assertFalse(clean.containsKey("line_spacing_bogus"))
    }

    @Test
    fun brokenJsonIsRejectedInsteadOfBeingImportedAsOneTextNote() {
        assertThrows(Exception::class.java) { parseBackupFile("{broken json", now = 42L) }
    }
}
