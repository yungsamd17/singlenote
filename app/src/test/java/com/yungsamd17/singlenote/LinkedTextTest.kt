package com.yungsamd17.singlenote

import com.yungsamd17.singlenote.util.stripLeadingVersionHeading
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkedTextTest {

    private val names = listOf("v0.3.3", "0.3.3")

    @Test
    fun stripsPlainVersionFirstLine() {
        assertEquals(
            "## Fixed\n- something",
            stripLeadingVersionHeading("v0.3.3\n\n## Fixed\n- something", names)
        )
    }

    @Test
    fun stripsHashedVersionHeading() {
        assertEquals(
            "Body",
            stripLeadingVersionHeading("# v0.3.3\nBody", names)
        )
    }

    @Test
    fun matchesWithoutVPrefix() {
        assertEquals(
            "Body",
            stripLeadingVersionHeading("0.3.3\nBody", names)
        )
    }

    @Test
    fun leavesOtherFirstLinesAlone() {
        val body = "#1062 fixed this\n\n## Fixed"
        assertEquals(body, stripLeadingVersionHeading(body, names))
    }

    @Test
    fun leavesDifferentVersionAlone() {
        val body = "v0.3.2\n\nOld notes"
        assertEquals(body, stripLeadingVersionHeading(body, names))
    }

    @Test
    fun blankInputStaysBlank() {
        assertEquals("", stripLeadingVersionHeading("\n  \n", names))
    }
}
