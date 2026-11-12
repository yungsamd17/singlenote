package com.yungsamd17.singlenote.notify

import androidx.core.app.NotificationCompat
import com.yungsamd17.singlenote.data.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the pinned-notification rules: when there is nothing to show, which
 * line becomes the title, and how private the notification is.
 */
class PinnedNotificationContentTest {

    private val defaultTitle = "Singlenote"

    private fun note(content: String) = Note(
        content = content,
        createdAt = 1L,
        updatedAt = 2L,
    )

    private fun content(
        note: Note? = note("hello"),
        notificationsEnabled: Boolean = true,
        pinned: Boolean = true,
        titleFirstLine: Boolean = false,
        lockscreenVisible: Boolean = false,
    ) = pinnedNotificationContent(
        note = note,
        notificationsEnabled = notificationsEnabled,
        pinned = pinned,
        titleFirstLine = titleFirstLine,
        lockscreenVisible = lockscreenVisible,
        defaultTitle = defaultTitle,
    )

    @Test
    fun nothingIsShownWithoutANote() {
        assertNull(content(note = null))
    }

    @Test
    fun nothingIsShownForABlankNote() {
        assertNull(content(note = note("   \n  ")))
    }

    @Test
    fun nothingIsShownWhenNotificationsAreDisabled() {
        assertNull(content(notificationsEnabled = false))
    }

    @Test
    fun nothingIsShownWhenTheNoteIsNotPinned() {
        assertNull(content(pinned = false))
    }

    @Test
    fun theAppLabelIsTheTitleByDefault() {
        val result = content(note = note("first line\nsecond line"))!!

        assertEquals(defaultTitle, result.title)
        assertEquals("first line\nsecond line", result.body)
    }

    @Test
    fun theFirstLineBecomesTheTitleWhenAsked() {
        val result = content(
            note = note("first line\nsecond line"),
            titleFirstLine = true,
        )!!

        assertEquals("first line", result.title)
        // The body starts at the second line instead of repeating the title.
        assertEquals("second line", result.body)
    }

    @Test
    fun theTitleUsesTheFirstNonBlankLine() {
        val result = content(note = note("\n\nreal title\nbody"), titleFirstLine = true)!!

        assertEquals("real title", result.title)
        assertEquals("body", result.body)
    }

    @Test
    fun theNotificationIsPrivateUnlessTheUserOptsIn() {
        assertEquals(NotificationCompat.VISIBILITY_PRIVATE, content()!!.visibility)
        assertEquals(
            NotificationCompat.VISIBILITY_PUBLIC,
            content(lockscreenVisible = true)!!.visibility,
        )
    }
}
