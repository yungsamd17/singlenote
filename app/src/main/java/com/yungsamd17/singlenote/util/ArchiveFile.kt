package com.yungsamd17.singlenote.util

import com.yungsamd17.singlenote.data.Note
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Format marker written into every archive export. */
const val ARCHIVE_FORMAT = "singlenote-archive"

/** Current archive export version. Bump it when the JSON shape changes. */
const val ARCHIVE_VERSION = 1

/** Thrown when an archive file cannot be read as a Singlenote archive. */
class ArchiveFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Serializes notes into the versioned archive JSON.
 *
 * The shape is stable: `format`, `version` and a `notes` array. Each note keeps
 * its content and timestamps; `title` is the first line and exists for
 * readability, the importer ignores it.
 */
fun buildArchiveJson(notes: List<Note>): String {
    val items = JSONArray()
    notes.forEach { note ->
        items.put(
            JSONObject()
                .put("title", note.content.lineSequence().firstOrNull().orEmpty())
                .put("content", note.content)
                .put("createdAt", note.createdAt)
                .put("updatedAt", note.updatedAt)
                .put("editedAt", note.editedAt ?: JSONObject.NULL)
                .put("archivedAt", note.archivedAt ?: JSONObject.NULL)
        )
    }
    return JSONObject()
        .put("format", ARCHIVE_FORMAT)
        .put("version", ARCHIVE_VERSION)
        .put("notes", items)
        .toString(2)
}

/**
 * Parses an archive export into notes.
 *
 * Imported notes always come back archived: the file only ever holds archived
 * notes, and the active note is never part of an export. Throws
 * [ArchiveFormatException] for malformed JSON, a foreign format, an
 * unsupported version or a note without content.
 */
fun parseArchiveJson(raw: String): List<Note> {
    val root = try {
        JSONObject(raw)
    } catch (e: JSONException) {
        throw ArchiveFormatException("the file is not valid JSON", e)
    }

    val format = root.optString("format", "")
    if (format != ARCHIVE_FORMAT) {
        throw ArchiveFormatException("unexpected format: $format")
    }
    val version = root.optInt("version", -1)
    if (version != ARCHIVE_VERSION) {
        throw ArchiveFormatException("unsupported archive version: $version")
    }

    val array = root.optJSONArray("notes")
        ?: throw ArchiveFormatException("the archive has no notes array")
    val notes = ArrayList<Note>(array.length())
    for (index in 0 until array.length()) {
        val obj = array.optJSONObject(index)
            ?: throw ArchiveFormatException("note $index is not an object")
        if (!obj.has("content")) {
            throw ArchiveFormatException("note $index has no content")
        }
        if (!obj.has("createdAt") || !obj.has("updatedAt")) {
            throw ArchiveFormatException("note $index has no timestamps")
        }
        notes += try {
            Note(
                content = obj.getString("content"),
                createdAt = obj.getLong("createdAt"),
                updatedAt = obj.getLong("updatedAt"),
                editedAt = if (obj.isNull("editedAt")) null else obj.getLong("editedAt"),
                archivedAt = if (obj.isNull("archivedAt")) null else obj.getLong("archivedAt"),
                state = Note.STATE_ARCHIVED,
            )
        } catch (e: JSONException) {
            throw ArchiveFormatException("note $index is malformed", e)
        }
    }
    return notes
}
