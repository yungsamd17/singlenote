package com.yungsamd17.singlenote

import com.yungsamd17.singlenote.util.firstNonBlankLine
import com.yungsamd17.singlenote.util.truncate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
