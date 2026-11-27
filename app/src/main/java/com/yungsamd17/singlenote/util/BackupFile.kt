package com.yungsamd17.singlenote.util

import com.yungsamd17.singlenote.data.Note
import com.yungsamd17.singlenote.data.NotePreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Format marker written into every full backup. */
const val BACKUP_FORMAT = "singlenote-backup"

/** Current full-backup version. Bump when the JSON shape changes. */
const val BACKUP_VERSION = 1

/**
 * Upper bound for an imported file. Notes and preferences are small, so a
 * larger file is either a mistake or an attempt to make the app allocate a
 * huge string; reject it before reading instead of parsing it.
 */
const val BACKUP_MAX_BYTES = 10L * 1024 * 1024

/** Suggested name of a full backup file. */
const val BACKUP_FILE_NAME = "singlenote-backup.json"

/** Suggested name of a notes-only (legacy archive) file. */
const val BACKUP_NOTES_FILE_NAME = "singlenote-archive.json"

/** Thrown when a file cannot be read as a Singlenote backup. */
class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** What a full backup includes. Every category is optional. */
data class BackupSelection(
    val activeNote: Boolean = true,
    val archivedNotes: Boolean = true,
    val appSettings: Boolean = true,
    val widgetSettings: Boolean = true,
)

/** Everything the app holds, collected from the real stores before writing. */
data class BackupSnapshot(
    val active: Note? = null,
    val archived: List<Note> = emptyList(),
    val settings: Map<String, Any> = emptyMap(),
    val widgetStyles: Map<String, Any> = emptyMap(),
    val languageTag: String = "",
    // Absent sections are not empty sections: restoring one must never wipe
    // the corresponding data on the device.
    val hasActiveNote: Boolean = false,
    val hasArchivedNotes: Boolean = false,
    val hasSettings: Boolean = false,
    val hasWidgets: Boolean = false,
)

/** What to put back from a full backup. Settings never overwrite notes. */
data class RestoreSelection(
    val notes: Boolean = true,
    val appSettings: Boolean = true,
    val widgetSettings: Boolean = true,
    val replaceNotes: Boolean = false,
)

/** Counts reported after a restore, so the snackbar can be honest. */
data class RestoreResult(
    val activeRestored: Boolean = false,
    val archivedAdded: Int = 0,
    val archivedSkipped: Int = 0,
    val settingsRestored: Boolean = false,
    val widgetStylesRestored: Boolean = false,
    val languageRestored: Boolean = false,
)

/** A file is either a full backup or one of the two legacy note archives. */
sealed interface BackupPayload {
    data class Full(val snapshot: BackupSnapshot) : BackupPayload

    /** Legacy archive JSON or plain text: notes only, always archived. */
    data class Notes(val notes: List<Note>, val plainText: Boolean) : BackupPayload
}

// The keys we are willing to write and read back. Unknown keys (from a newer
// app version or a hand-edited file) are dropped rather than written into
// DataStore, where they would have no effect and could never be removed.
private val BOOLEAN_SETTINGS =
    setOf(
        NotePreferences.KEY_PINNED.name,
        NotePreferences.KEY_NOTIFICATIONS.name,
        NotePreferences.KEY_LOCKSCREEN_VISIBLE.name,
        NotePreferences.KEY_NOTIFICATION_TITLE_FIRST_LINE.name,
        NotePreferences.KEY_UPDATE_CHECKS.name,
        NotePreferences.KEY_DYNAMIC_COLORS.name,
        NotePreferences.KEY_AMOLED.name,
        NotePreferences.KEY_MONOCHROME.name,
    )

private val STRING_SETTINGS =
    mapOf(
        NotePreferences.KEY_THEME.name to NotePreferences.THEMES.toSet(),
        NotePreferences.KEY_FONT_FAMILY.name to NotePreferences.FONTS.toSet(),
        NotePreferences.KEY_LINE_SPACING.name to NotePreferences.LINE_SPACINGS.toSet(),
        NotePreferences.KEY_TEXT_SIZE.name to NotePreferences.SIZES.toSet(),
        NotePreferences.KEY_ACCENT.name to
            (NotePreferences.ACCENTS + NotePreferences.ACCENT_SYSTEM).toSet(),
        NotePreferences.KEY_APP_FONT.name to NotePreferences.APP_FONTS.toSet(),
        NotePreferences.KEY_APP_ICON.name to setOf("default", "accent", "dynamic"),
        "archive_sort" to setOf("changed", "archived", "title"),
        "archive_order" to setOf("ascending", "descending"),
    )

private val WIDGET_THEMES = setOf("system", "light", "dark")
private val WIDGET_ACCENTS =
    setOf("app", NotePreferences.ACCENT_DEFAULT, NotePreferences.ACCENT_BLUE,
        NotePreferences.ACCENT_TEAL, NotePreferences.ACCENT_GREEN,
        NotePreferences.ACCENT_ORANGE, NotePreferences.ACCENT_PINK)
private val WIDGET_ACTIONS = setOf("open", "edit", "archive")
private val WIDGET_SIZES = setOf("small", "medium", "large")
// Longest suffixes first: an empty suffix matches every key, so it has to be
// the last resort when splitting "remembered.opacity" into key + field.
private val WIDGET_FIELDS =
    listOf(".accent", ".size", ".action", ".titleSize", ".showTitle", ".opacity", "")

/**
 * Keeps only settings this version understands, with a value it can actually
 * use. Restoring a file from a future version must not leave a preference in
 * an unrepresentable state.
 */
fun sanitizeSettings(values: Map<String, Any>): Map<String, Any> =
    values.mapNotNull { (key, value) ->
        when {
            key in BOOLEAN_SETTINGS && value is Boolean -> key to value
            key in STRING_SETTINGS && value is String && value in STRING_SETTINGS.getValue(key) -> key to value
            else -> null
        }
    }.toMap()

/**
 * Widget preferences are shared-preference keys: a widget id (numeric) or the
 * special "remembered" key, each with suffixed fields. Drop anything else,
 * clamp the numeric fields, and leave the values in the form [WidgetStyleStore]
 * reads them.
 */
fun sanitizeWidgetStyles(values: Map<String, Any>): Map<String, Any> {
    val result = mutableMapOf<String, Any>()
    values.forEach { (rawKey, value) ->
        val (key, field) = WIDGET_FIELDS.firstOrNull { rawKey.endsWith(it) }
            ?.let { rawKey.removeSuffix(it) to it }
            ?: return@forEach
        if (key != WidgetStyleKeys.REMEMBERED && key.toIntOrNull() == null) return@forEach
        when (field) {
            "" -> if (value is String && value in WIDGET_THEMES) result[rawKey] = value
            ".accent" -> if (value is String && value in WIDGET_ACCENTS) result[rawKey] = value
            ".size" ->
                if (value is String && (value in WIDGET_SIZES || sanitizeWidgetSize(value) != null)) {
                    result[rawKey] = if (value in WIDGET_SIZES) value else sanitizeWidgetSize(value)!!
                }
            ".action" -> if (value is String && value in WIDGET_ACTIONS) result[rawKey] = value
            ".titleSize" ->
                if (value is Int) result[rawKey] = value.coerceIn(8, 24)
                else if (value is Long) result[rawKey] = value.toInt().coerceIn(8, 24)
            ".showTitle" -> if (value is Boolean) result[rawKey] = value
            ".opacity" ->
                if (value is Int) result[rawKey] = value.coerceIn(0, 100)
                else if (value is Long) result[rawKey] = value.toInt().coerceIn(0, 100)
        }
    }
    // A theme value is the marker that makes WidgetStyleStore consider a key
    // present. Never restore a suffix without its base key.
    result.keys.toList().forEach { key ->
        WIDGET_FIELDS.firstOrNull { key.endsWith(it) && it.isNotEmpty() }?.let { field ->
            if (key.removeSuffix(field) !in result) result.remove(key)
        }
    }
    return result
}

private fun sanitizeWidgetSize(value: String): String? =
    value.toIntOrNull()?.coerceIn(10, 36)?.toString()

/** Kept in one place so the sanitizer and the storage agree on the marker key. */
private object WidgetStyleKeys {
    const val REMEMBERED = "remembered"
}

private fun noteJson(note: Note): JSONObject =
    JSONObject()
        .put("content", note.content)
        .put("createdAt", note.createdAt)
        .put("updatedAt", note.updatedAt)
        .put("editedAt", note.editedAt ?: JSONObject.NULL)
        .put("archivedAt", note.archivedAt ?: JSONObject.NULL)

private fun noteFrom(json: JSONObject, label: String): Note {
    if (!json.has("content")) throw BackupFormatException("$label has no content")
    if (!json.has("createdAt") || !json.has("updatedAt")) {
        throw BackupFormatException("$label has no timestamps")
    }
    return try {
        Note(
            content = json.getString("content"),
            createdAt = json.getLong("createdAt"),
            updatedAt = json.getLong("updatedAt"),
            editedAt = if (json.isNull("editedAt")) null else json.getLong("editedAt"),
            archivedAt = if (json.isNull("archivedAt")) null else json.getLong("archivedAt"),
            state = Note.STATE_ARCHIVED,
        )
    } catch (e: JSONException) {
        throw BackupFormatException("$label is malformed", e)
    }
}

private fun mapFrom(json: JSONObject): Map<String, Any> {
    val result = mutableMapOf<String, Any>()
    json.keys().forEach { key ->
        val value = json.get(key)
        if (value is String || value is Boolean || value is Int || value is Long) result[key] = value
    }
    return result
}

/** Writes a sanitized map as a JSON object without relying on a Map constructor. */
private fun jsonObjectFrom(values: Map<String, Any>): JSONObject {
    val json = JSONObject()
    values.forEach { (key, value) -> json.put(key, value) }
    return json
}

/** A section that is missing, null or the wrong type is treated as absent. */
private fun jsonObjectOrEmpty(root: JSONObject, key: String): Map<String, Any> {
    val value = root.opt(key) ?: return emptyMap()
    return if (value is JSONObject) mapFrom(value) else emptyMap()
}

/**
 * Serializes the selected categories into the versioned full-backup JSON.
 * Categories left out are written as absent sections, never as empty ones, so
 * a restore can tell "not included" from "included but empty".
 */
fun buildBackupJson(
    snapshot: BackupSnapshot,
    selection: BackupSelection = BackupSelection(),
    savedAt: Long = System.currentTimeMillis(),
): String {
    val root =
        JSONObject()
            .put("format", BACKUP_FORMAT)
            .put("version", BACKUP_VERSION)
            .put("savedAt", savedAt)

    if (selection.activeNote || selection.archivedNotes) {
        val notes = JSONObject()
        if (selection.activeNote) {
            notes.put("active", snapshot.active?.let { noteJson(it).put("state", "active") } ?: JSONObject.NULL)
        }
        if (selection.archivedNotes) {
            val items = JSONArray()
            snapshot.archived.forEach { items.put(noteJson(it).put("state", "archived")) }
            notes.put("archived", items)
        }
        root.put("notes", notes)
    }
    if (selection.appSettings) {
        root.put("settings", jsonObjectFrom(sanitizeSettings(snapshot.settings)))
    }
    if (selection.widgetSettings) {
        root.put("widget", jsonObjectFrom(sanitizeWidgetStyles(snapshot.widgetStyles)))
    }
    if (selection.appSettings) root.put("language", snapshot.languageTag)
    return root.toString(2)
}

/** True when the file starts with a JSON object. */
private fun isJson(raw: String): Boolean = raw.trimStart().startsWith("{")

/** True when [raw] looks like a full backup rather than a legacy archive. */
fun isFullBackup(raw: String): Boolean {
    if (!isJson(raw)) return false
    return try {
        JSONObject(raw).optString("format", "") == BACKUP_FORMAT
    } catch (_: JSONException) {
        // Broken JSON must go through the archive parser and be rejected there
        // instead of being silently imported as one giant text note.
        false
    }
}

/**
 * Parses a full backup. Unknown sections are ignored so a partial backup still
 * restores what it contains; the format marker and version are not.
 */
fun parseBackup(raw: String): BackupSnapshot {
    val root =
        try {
            JSONObject(raw)
        } catch (e: JSONException) {
            throw BackupFormatException("the file is not valid JSON", e)
        }
    val format = root.optString("format", "")
    if (format != BACKUP_FORMAT) throw BackupFormatException("unexpected format: $format")
    val version = root.optInt("version", -1)
    if (version != BACKUP_VERSION) throw BackupFormatException("unsupported backup version: $version")

    var active: Note? = null
    var archived = emptyList<Note>()
    var hasActiveNote = false
    var hasArchivedNotes = false
    try {
        root.optJSONObject("notes")?.let { notes ->
            hasActiveNote = notes.has("active")
            hasArchivedNotes = notes.has("archived")
            if (!notes.isNull("active")) {
                active = noteFrom(notes.getJSONObject("active"), "active note")
                    .copy(state = Note.STATE_ACTIVE, activeSlot = Note.ACTIVE_SLOT)
            }
            notes.optJSONArray("archived")?.let { items ->
                archived = (0 until items.length()).map { index ->
                    val obj = items.optJSONObject(index)
                        ?: throw BackupFormatException("note $index is not an object")
                    noteFrom(obj, "note $index")
                }
            }
        }
    } catch (e: JSONException) {
        throw BackupFormatException("the notes section is malformed", e)
    }
    return BackupSnapshot(
        active = active,
        archived = archived,
        settings = sanitizeSettings(jsonObjectOrEmpty(root, "settings")),
        widgetStyles = sanitizeWidgetStyles(jsonObjectOrEmpty(root, "widget")),
        languageTag = root.optString("language", ""),
        hasActiveNote = hasActiveNote,
        hasArchivedNotes = hasArchivedNotes,
        hasSettings = root.has("settings"),
        hasWidgets = root.has("widget"),
    )
}

/**
 * Parses either a full backup or a legacy note archive (JSON or plain text).
 * A malformed JSON file is rejected: the text parser must never swallow it.
 */
fun parseBackupFile(raw: String, now: Long = System.currentTimeMillis()): BackupPayload =
    if (isFullBackup(raw)) {
        BackupPayload.Full(parseBackup(raw))
    } else {
        BackupPayload.Notes(parseArchive(raw, now), isPlainTextArchive(raw))
    }
