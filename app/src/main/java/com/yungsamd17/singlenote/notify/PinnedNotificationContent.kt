package com.yungsamd17.singlenote.notify

import androidx.core.app.NotificationCompat
import com.yungsamd17.singlenote.data.Note
import com.yungsamd17.singlenote.util.notificationBody
import com.yungsamd17.singlenote.util.notificationTitle

/**
 * The parts of the pinned notification that can be decided without touching
 * Android: what the title and body say, how visible the notification is on
 * the lock screen, and what the concealable public version says.
 *
 * `PinNotification.refresh` reads the preferences, the database and the
 * keyguard state; the rules live here so they can be tested on the JVM.
 */
data class PinnedNotificationContent(
    val title: String,
    val body: String,
    val visibility: Int,
    val publicTitle: String,
    val publicBody: String,
)

/**
 * Builds the pinned notification content.
 *
 * Returns null when there is nothing to show: no note, a blank note, or the
 * user turned notifications or the pin off. The caller cancels the
 * notification in that case.
 *
 * When the user opts out of lock-screen visibility, the note is concealed
 * unless [noteRevealed] is true — a flag set only on a positive unlock signal
 * (USER_PRESENT or an unlocked activity resume), never inferred from the
 * keyguard API, which lies on some OEM ROMs while the lock screen is up.
 * [forceConcealed] overrides even a revealed flag for screen events, so a
 * wake can never briefly show the note.
 */
fun pinnedNotificationContent(
    note: Note?,
    notificationsEnabled: Boolean,
    pinned: Boolean,
    titleFirstLine: Boolean,
    lockscreenVisible: Boolean,
    noteRevealed: Boolean,
    forceConcealed: Boolean,
    defaultTitle: String,
    hiddenBody: String,
): PinnedNotificationContent? {
    if (!notificationsEnabled || !pinned) return null
    if (note == null || note.content.isBlank()) return null

    // The title is the note's first line when the user asked for it, and the
    // app's own label otherwise.
    val fullTitle = if (titleFirstLine) {
        notificationTitle(note.content).ifEmpty { defaultTitle }
    } else {
        defaultTitle
    }

    // The title already carries the first line, so the body starts at the
    // second one instead of repeating it.
    val fullBody = if (titleFirstLine) notificationBody(note.content) else note.content

    // Conceal by default. Only an explicit reveal (USER_PRESENT / foreground
    // resume while unlocked) can show the note, and a screen event always
    // forces the placeholder back — no reliance on keyguard readings.
    val concealed = !lockscreenVisible && (!noteRevealed || forceConcealed)

    // Opted in: fully public. Opted out and concealed: private with a generic
    // public version, so the placeholder is visible on the lock screen.
    // Opted out but revealed (full note): SECRET — the platform hides the
    // whole notification on a secure lock screen even in the gap between the
    // lock screen appearing and our SCREEN_OFF receiver concealing the post.
    // SECRET notifications still appear normally in the unlocked shade.
    val visibility =
        when {
            lockscreenVisible -> NotificationCompat.VISIBILITY_PUBLIC
            concealed -> NotificationCompat.VISIBILITY_PRIVATE
            else -> NotificationCompat.VISIBILITY_SECRET
        }

    return PinnedNotificationContent(
        title = if (concealed) defaultTitle else fullTitle,
        body = if (concealed) hiddenBody else fullBody,
        visibility = visibility,
        // Always give Android a generic public version. Without one the
        // system builds it from the full notification, which can carry the
        // note text (or its first line as the title) onto the lock screen in
        // the window before the keyguard receiver re-posts the concealed one.
        publicTitle = defaultTitle,
        publicBody = hiddenBody,
    )
}
