package com.yungsamd17.singlenote.widget

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetContrastTest {

    private val inkOnLight = Color(0xFF1D1B20)
    private val inkOnDark = Color(0xFFE6E0E9)
    private val lightCard = Color(0xFFECE6F0)
    private val darkCard = Color(0xFF2B2930)

    @Test
    fun aDarkBackdropPicksTheLightInk() {
        val effective = WidgetContrast.composite(lightCard, Color(0xFF080808), 0.35f)
        assertTrue(effective.red < 0.4f)
        assertFalse(WidgetContrast.prefersDarkInk(inkOnLight, inkOnDark, effective))
    }

    @Test
    fun aLightBackdropPicksTheDarkInk() {
        val effective = WidgetContrast.composite(lightCard, Color(0xFFFFFFFF), 0.35f)
        assertTrue(WidgetContrast.prefersDarkInk(inkOnLight, inkOnDark, effective))
    }

    @Test
    fun aDarkCardStillUsesLightInkWhenOpaque() {
        assertFalse(WidgetContrast.prefersDarkInk(inkOnLight, inkOnDark, darkCard))
    }

    @Test
    fun opacityOneIgnoresTheBackdrop() {
        val effective = WidgetContrast.composite(lightCard, Color.Black, 1f)
        assertTrue(WidgetContrast.prefersDarkInk(inkOnLight, inkOnDark, effective))
    }
}
