package com.yungsamd17.singlenote.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetFontSizeTest {
    @Test
    fun legacySizesPreserveTheirAppearance() {
        assertEquals(14, widgetFontSize("small"))
        assertEquals(16, widgetFontSize("medium"))
        assertEquals(20, widgetFontSize("large"))
    }

    @Test
    fun customSizesAreClampedAndInvalidValuesFallBack() {
        assertEquals(23, widgetFontSize("23"))
        assertEquals(10, widgetFontSize("2"))
        assertEquals(36, widgetFontSize("99"))
        assertEquals(16, widgetFontSize("invalid"))
    }
}
