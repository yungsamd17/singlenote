package com.yungsamd17.singlenote.notify

import androidx.core.app.NotificationCompat
import com.yungsamd17.singlenote.data.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the pinned-notification rules: when there is nothing to show, which
 * line becomes the title, how private the notification is, and how the note
 * is concealed while the keyguard is up.
 */
class PinnedNotificationContentTest {

    private val defaultTitle = "Singlenote"
    private val hiddenBody = "Unlock to read your note"

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
        noteRevealed: Boolean = true,
        forceConcealed: Boolean = false,
    ) = pinnedNotificationContent(
        note = note,
        notificationsEnabled = notificationsEnabled,
        pinned = pinned,
        titleFirstLine = titleFirstLine,
        lockscreenVisible = lockscreenVisible,
        noteRevealed = noteRevealed,
        forceConcealed = forceConcealed,
        defaultTitle = defaultTitle,
        hiddenBody = hiddenBody,
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
    fun theNotificationVisibilityFollowsTheLockScreenOption() {
        // Concealed: private with a generic public version (placeholder shows).
        assertEquals(
            NotificationCompat.VISIBILITY_PRIVATE,
            content(noteRevealed = false)!!.visibility,
        )
        // Revealed but opted out: secret, so no lock-screen flash window.
        assertEquals(NotificationCompat.VISIBILITY_SECRET, content()!!.visibility)
        // Opted in: fully public.
        assertEquals(
            NotificationCompat.VISIBILITY_PUBLIC,
            content(lockscreenVisible = true)!!.visibility,
        )
    }

    @Test
    fun theNoteIsConcealedUntilAnUnlockSignal() {
        val result = content(
            note = note("secret line\nmore secret"),
            titleFirstLine = true,
            noteRevealed = false,
        )!!

        assertEquals(defaultTitle, result.title)
        assertEquals(hiddenBody, result.body)
    }

    @Test
    fun aScreenEventConcealsEvenAfterAnUnlockSignal() {
        // A wake must not briefly show the note: the screen event forces the
        // placeholder even when the previous state was revealed.
        val result = content(
            note = note("secret line"),
            noteRevealed = true,
            forceConcealed = true,
        )!!

        assertEquals(defaultTitle, result.title)
        assertEquals(hiddenBody, result.body)
    }

    @Test
    fun theNoteComesBackOnceTheDeviceIsUnlocked() {
        val result = content(note = note("secret line"), noteRevealed = true)!!

        assertEquals(defaultTitle, result.title)
        assertEquals("secret line", result.body)
    }

    @Test
    fun anOptedInNoteStaysVisibleBeforeUnlock() {
        val result = content(
            note = note("secret line"),
            lockscreenVisible = true,
            noteRevealed = false,
        )!!

        assertEquals("secret line", result.body)
        assertEquals(NotificationCompat.VISIBILITY_PUBLIC, result.visibility)
    }

    @Test
    fun aConcealedContentCarriesNoNoteTextInAnyField() {
        val secret = "TOP_SECRET_NOTE_LINE"
        val result = content(
            note = note(secret),
            titleFirstLine = true,
            noteRevealed = false,
        )!!

        // Every string that can reach the notification (title, body, expanded
        // style and public version) must be free of the note text.
        listOf(result.title, result.body, result.publicTitle, result.publicBody)
            .forEach { assertFalse(it.contains(secret)) }
    }

    @Test
    fun thePublicVersionNeverCarriesNoteText() {
        val result = content(
            note = note("first line\nsecond line"),
            titleFirstLine = true,
            lockscreenVisible = true,
            noteRevealed = true,
        )!!

        assertEquals(defaultTitle, result.publicTitle)
        assertEquals(hiddenBody, result.publicBody)
    }
}
