package com.yungsamd17.singlenote.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollBehaviorTest {
    @Test
    fun fittingSettingsDoNotScroll() {
        assertFalse(settingsContentOverflows(400, 800))
        assertFalse(settingsContentOverflows(800, 800))
        assertFalse(settingsContentOverflows(0, 0))
    }

    @Test
    fun overflowingSettingsScroll() {
        assertTrue(settingsContentOverflows(810, 800))
    }

    @Test
    fun largerViewportCanDisableScrolling() {
        assertFalse(settingsContentOverflows(810, 1000))
    }

    @Test
    fun visibleCaretDoesNotMoveViewport() {
        assertEquals(200, caretScrollOffset(240, 270, 300, 200, 900, 16))
    }

    @Test
    fun typingBelowViewportRevealsCaretWithMargin() {
        assertEquals(246, caretScrollOffset(500, 530, 300, 200, 900, 16))
    }

    @Test
    fun movingCaretAboveViewportScrollsUp() {
        assertEquals(84, caretScrollOffset(100, 130, 300, 200, 900, 16))
    }

    @Test
    fun keyboardResizeKeepsActiveLineVisible() {
        assertEquals(186, caretScrollOffset(320, 350, 180, 0, 900, 16))
    }

    @Test
    fun scrollTargetsRespectContentBounds() {
        assertEquals(0, caretScrollOffset(0, 30, 300, 100, 900, 16))
        assertEquals(900, caretScrollOffset(1200, 1230, 300, 200, 900, 16))
        assertEquals(100, caretScrollOffset(1200, 1230, 0, 100, 900, 16))
    }
}
