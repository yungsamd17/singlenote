package com.yungsamd17.singlenote.data

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the DataStore-backed preferences: every value survives a write and
 * comes back on the next read.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotePreferencesTest {

    @Before
    fun setUp() {
        // Every test gets its own data directory: start from a fresh
        // preferences instance instead of one a previous test left behind.
        NotePreferences.resetForTests()
    }

    private val preferences: NotePreferences
        get() = NotePreferences.get(ApplicationProvider.getApplicationContext())

    @Test
    fun themeAndFontAndSizeRoundTrip() = runBlocking {
        val prefs = preferences

        prefs.setThemeMode(NotePreferences.THEME_DARK)
        prefs.setFontFamily(NotePreferences.FONT_SERIF)
        prefs.setTextSize(NotePreferences.SIZE_LARGE)

        assertEquals(NotePreferences.THEME_DARK, prefs.themeMode.first())
        assertEquals(NotePreferences.FONT_SERIF, prefs.fontFamily.first())
        assertEquals(NotePreferences.SIZE_LARGE, prefs.textSize.first())
    }

    @Test
    fun accentAndSwitchesRoundTrip() = runBlocking {
        val prefs = preferences

        prefs.setAccentColor(NotePreferences.ACCENT_TEAL)
        prefs.setDynamicColors(true)
        prefs.setAmoled(true)
        prefs.setMonochrome(true)

        assertEquals(NotePreferences.ACCENT_TEAL, prefs.accentColor.first())
        assertEquals(true, prefs.dynamicColors.first())
        assertEquals(true, prefs.amoled.first())
        assertEquals(true, prefs.monochrome.first())
    }

    @Test
    fun appFontAndIconRoundTrip() = runBlocking {
        val prefs = preferences

        prefs.setAppFont("mono")
        prefs.setAppIcon("dynamic")

        assertEquals("mono", prefs.appFont.first())
        assertEquals("dynamic", prefs.appIcon.first())
    }

    @Test
    fun notificationPreferencesRoundTrip() = runBlocking {
        val prefs = preferences

        prefs.setPinned(true)
        prefs.setNotificationsEnabled(false)
        prefs.setLockscreenVisible(false)
        prefs.setNotificationTitleFirstLine(true)

        assertEquals(true, prefs.pinned.first())
        assertEquals(false, prefs.notificationsEnabled.first())
        assertEquals(false, prefs.lockscreenVisible.first())
        assertEquals(true, prefs.notificationTitleFirstLine.first())
    }

    @Test
    fun archiveSortAndOrderRoundTrip() = runBlocking {
        val prefs = preferences

        prefs.setArchiveSort("title")
        prefs.setArchiveOrder("ascending")

        assertEquals("title", prefs.archiveSort.first())
        assertEquals("ascending", prefs.archiveOrder.first())
    }

    @Test
    fun systemAccentResolvesToTheDefaultAccent() {
        assertEquals(
            NotePreferences.ACCENT_DEFAULT,
            NotePreferences.resolveAccentColor(NotePreferences.ACCENT_SYSTEM),
        )
        assertEquals(
            NotePreferences.ACCENT_DEFAULT,
            NotePreferences.resolveAccentColor(null),
        )
        assertEquals(
            NotePreferences.ACCENT_BLUE,
            NotePreferences.resolveAccentColor(NotePreferences.ACCENT_BLUE),
        )
    }

    @Test
    fun dynamicColorsFollowTheLegacySystemAccent() {
        assertEquals(true, NotePreferences.resolveDynamicColors(null, NotePreferences.ACCENT_SYSTEM))
        assertEquals(false, NotePreferences.resolveDynamicColors(null, NotePreferences.ACCENT_DEFAULT))
        assertEquals(true, NotePreferences.resolveDynamicColors(true, NotePreferences.ACCENT_DEFAULT))
        // Android below 12 has no wallpaper colors to pick from.
        assertEquals(
            false,
            NotePreferences.resolveDynamicColors(true, NotePreferences.ACCENT_SYSTEM, supported = false),
        )
    }
}
