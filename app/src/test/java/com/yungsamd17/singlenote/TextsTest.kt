package com.yungsamd17.singlenote

import com.yungsamd17.singlenote.util.firstNonBlankLine
import com.yungsamd17.singlenote.util.notificationBody
import com.yungsamd17.singlenote.util.notificationTitle
import com.yungsamd17.singlenote.util.truncate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextsTest {

    @Test
    fun firstNonBlankLine_returnsFirstLine() {
        assertEquals("hello", firstNonBlankLine("hello\nworld"))
    }

    @Test
    fun firstNonBlankLine_skipsLeadingBlanks() {
        assertEquals("world", firstNonBlankLine("\n \nworld"))
    }

    @Test
    fun firstNonBlankLine_nullWhenBlank() {
        assertNull(firstNonBlankLine("\n\n  "))
        assertNull(firstNonBlankLine(""))
    }

    @Test
    fun truncate_keepsShortText() {
        assertEquals("abc", truncate("abc", 5))
        assertEquals("abcde", truncate("abcde", 5))
    }

    @Test
    fun truncate_cutsLongText() {
        assertEquals("abc", truncate("abcdef", 3))
    }

    @Test
    fun notificationTitle_usesFirstLine() {
        assertEquals("hello", notificationTitle("hello\nworld"))
        assertEquals("world", notificationTitle("\n \nworld"))
    }

    @Test
    fun notificationTitle_keepsShortLines() {
        assertEquals("hello", notificationTitle("hello"))
    }

    @Test
    fun notificationTitle_cutsLongLinesWithEllipsis() {
        val title = notificationTitle("0123456789012345678901234567890")
        assertEquals(20, title.length)
        assertTrue(title.endsWith("…"))
        assertEquals("0123456789012345678…", title)
    }

    @Test
    fun notificationTitle_blankNoteHasNoTitle() {
        assertEquals("", notificationTitle(""))
        assertEquals("", notificationTitle("\n  \n"))
    }

    @Test
    fun notificationBody_startsAtTheSecondLine() {
        assertEquals("world", notificationBody("hello\nworld"))
        assertEquals("world", notificationBody("\n \nhello\nworld"))
    }

    @Test
    fun notificationBody_keepsTheRemainingLines() {
        assertEquals("world\nagain", notificationBody("hello\nworld\nagain"))
    }

    @Test
    fun notificationBody_isEmptyForSingleLineNotes() {
        assertEquals("", notificationBody("hello"))
        assertEquals("", notificationBody(""))
    }
}
