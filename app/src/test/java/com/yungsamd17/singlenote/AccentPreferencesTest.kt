package com.yungsamd17.singlenote

import com.yungsamd17.singlenote.data.NotePreferences
import com.yungsamd17.singlenote.widget.widgetAccentSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentPreferencesTest {
    @Test fun unsupportedAndroidVersionsKeepPresetPickerAvailable() {
        assertFalse(NotePreferences.resolveDynamicColors(null, "system", supported = false))
        assertFalse(NotePreferences.resolveDynamicColors(true, "blue", supported = false))
    }

    @Test
    fun legacyWallpaperAccentMovesToDynamicColors() {
        assertEquals(NotePreferences.ACCENT_DEFAULT, NotePreferences.resolveAccentColor("system"))
        assertTrue(NotePreferences.resolveDynamicColors(null, "system"))
    }

    @Test
    fun explicitDynamicChoiceTakesPrecedenceOverLegacyAccent() {
        assertFalse(NotePreferences.resolveDynamicColors(false, "system"))
        assertTrue(NotePreferences.resolveDynamicColors(true, "blue"))
    }

    @Test
    fun freshInstallAndPresetUsersKeepTheirDefaults() {
        assertEquals(NotePreferences.ACCENT_DEFAULT, NotePreferences.resolveAccentColor(null))
        assertEquals("blue", NotePreferences.resolveAccentColor("blue"))
        assertFalse(NotePreferences.resolveDynamicColors(null, "blue"))
        assertFalse(NotePreferences.resolveDynamicColors(null, null))
    }

    @Test
    fun pickerOnlyOffersPresetAccents() {
        assertEquals(
            listOf("default", "blue", "teal", "green", "orange", "pink"),
            NotePreferences.ACCENTS,
        )
    }

    @Test
    fun legacyWidgetWallpaperChoiceFollowsApp() {
        assertEquals("app", widgetAccentSetting("system"))
        assertEquals("app", widgetAccentSetting(null))
        assertEquals("blue", widgetAccentSetting("blue"))
    }
}
