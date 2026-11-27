package com.yungsamd17.singlenote.data

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.yungsamd17.singlenote.util.AppLanguage
import com.yungsamd17.singlenote.widget.SinglenoteWidgetReceiver
import com.yungsamd17.singlenote.widget.WidgetStyleStore
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android-facing implementation of [BackupStore]. All writes go through the
 * same stores the app uses at runtime, so the ViewModels and the widgets see a
 * restore immediately instead of reading a second copy of the truth.
 */
class BackupRepository(
    private val context: Context,
    private val repository: NoteRepository,
    private val preferences: NotePreferences,
    private val flushNotes: suspend () -> Unit = {},
) : BackupStore {

    override suspend fun activeNote(): Note? = repository.getActive()

    override suspend fun archivedNotes(): List<Note> = repository.archivedNotes.firstOrNull().orEmpty()

    override suspend fun settings(): Map<String, Any> = preferences.snapshot()

    override suspend fun widgetStyles(): Map<String, Any> = WidgetStyleStore.snapshot(context)

    override suspend fun languageTag(): String = AppLanguage.getLanguageTag(context)

    override suspend fun importArchived(notes: List<Note>) = repository.importArchived(notes)

    override suspend fun restoreActive(note: Note) = repository.restoreActiveFromBackup(note)

    override suspend fun replaceNotes(active: Note?, archived: List<Note>) =
        repository.replaceAllNotes(active, archived)

    override suspend fun applySettings(values: Map<String, Any>) = preferences.restore(values)

    override suspend fun applyWidgetStyles(values: Map<String, Any>): Unit = withContext(Dispatchers.Default) {
        WidgetStyleStore.restore(context, values)
        // Widget ids belong to the current launcher session. Restoring the
        // remembered settings onto the widgets that exist now is what makes a
        // backup useful after a reinstall (new ids, same look and behaviour).
        WidgetStyleStore.remembered(context)?.let { remembered ->
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, SinglenoteWidgetReceiver::class.java))
            ids.forEach { WidgetStyleStore.set(context, it, remembered) }
        }
        Unit
    }

    override suspend fun applyLanguage(tag: String) = withContext(Dispatchers.Default) {
        try {
            AppLanguage.restore(context, tag)
        } catch (_: Exception) {
            // A hand-edited file must not abort the rest of the restore; the
            // stored tag is still written before the locale is applied.
        }
    }

    override suspend fun flushPendingNote() = flushNotes.invoke()

    override suspend fun refreshSurfaces() = repository.notifyChanged()
}
