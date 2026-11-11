package com.yungsamd17.singlenote.notify

import androidx.core.app.NotificationCompat
import com.yungsamd17.singlenote.data.Note
import com.yungsamd17.singlenote.util.notificationBody
import com.yungsamd17.singlenote.util.notificationTitle

/**
 * The parts of the pinned notification that can be decided without touching
 * Android: what the title and body say and how visible the notification is on
 * the lock screen.
 *
 * `PinNotification.refresh` reads the preferences and the database; the rules
 * live here so they can be tested on the JVM.
 */
data class PinnedNotificationContent(
    val title: String,
    val body: String,
    val visibility: Int,
)

/**
 * Builds the pinned notification content.
 *
 * Returns null when there is nothing to show: no note, a blank note, or the
 * user turned notifications or the pin off. The caller cancels the
 * notification in that case.
 */
fun pinnedNotificationContent(
    note: Note?,
    notificationsEnabled: Boolean,
    pinned: Boolean,
    titleFirstLine: Boolean,
    lockscreenVisible: Boolean,
    defaultTitle: String,
): PinnedNotificationContent? {
    if (!notificationsEnabled || !pinned) return null
    if (note == null || note.content.isBlank()) return null

    // The title is the note's first line when the user asked for it, and the
    // app's own label otherwise.
    val title = if (titleFirstLine) {
        notificationTitle(note.content).ifEmpty { defaultTitle }
    } else {
        defaultTitle
    }

    // The title already carries the first line, so the body starts at the
    // second one instead of repeating it.
    val body = if (titleFirstLine) notificationBody(note.content) else note.content

    return PinnedNotificationContent(
        title = title,
        body = body,
        // Private by default: full content only on the lock screen when the
        // user opts in via Settings.
        visibility = if (lockscreenVisible) {
            NotificationCompat.VISIBILITY_PUBLIC
        } else {
            NotificationCompat.VISIBILITY_PRIVATE
        },
    )
}
