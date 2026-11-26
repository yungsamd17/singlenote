package com.yungsamd17.singlenote.util

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.yungsamd17.singlenote.MainActivity
import com.yungsamd17.singlenote.R

/**
 * Launcher long-press shortcuts: New note, Archive and Settings.
 *
 * They are published at runtime instead of declared in `shortcuts.xml` because
 * the activity they start is exported: like the widget, tile and notification,
 * every shortcut intent carries [ActionSecurity]'s token, and that token is
 * generated per install and cannot be baked into a static resource.
 */
internal object AppShortcuts {
    /** Extra carrying the shortcut's destination. */
    const val EXTRA_TARGET = "shortcut_target"

    const val TARGET_NEW_NOTE = "new_note"
    const val TARGET_ARCHIVE = "archive"
    const val TARGET_SETTINGS = "settings"

    private data class Entry(
        val target: String,
        val label: Int,
        val icon: Int,
    )

    // Reuses the already translated notification/archive/settings labels, so
    // adding a shortcut never means 13 new strings.
    private val entries = listOf(
        Entry(TARGET_NEW_NOTE, R.string.notification_action_new_note, R.mipmap.ic_shortcut_new_note),
        Entry(TARGET_ARCHIVE, R.string.archive_title, R.mipmap.ic_shortcut_archive),
        Entry(TARGET_SETTINGS, R.string.settings_title, R.mipmap.ic_shortcut_settings),
    )

    /**
     * (Re)publishes the shortcuts with the current labels. Called on every
     * launch and after a language change, because a shortcut label is a
     * snapshot the launcher keeps instead of a resource it re-resolves.
     */
    fun publish(context: Context) {
        val shortcuts = entries.mapIndexed { index, entry ->
            ShortcutInfoCompat.Builder(context, entry.target)
                .setShortLabel(context.getString(entry.label))
                .setLongLabel(context.getString(entry.label))
                .setIcon(IconCompat.createWithResource(context, entry.icon))
                .setRank(index)
                .setIntent(intent(context, entry.target))
                .build()
        }
        try {
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        } catch (e: Exception) {
            // A launcher without shortcut support must never break a launch.
            Diagnostics.failure("shortcuts", e)
        }
    }

    /** Intent for one shortcut, authenticated like the widget's actions. */
    fun intent(context: Context, target: String): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(ActionSecurity.EXTRA_TOKEN, ActionSecurity.token(context))
            putExtra(EXTRA_TARGET, target)
        }

    /** The shortcut carried by [intent], or null for anything else. */
    fun target(intent: Intent?): String? =
        intent?.getStringExtra(EXTRA_TARGET)?.takeIf { value -> entries.any { it.target == value } }
}
