package com.yungsamd17.singlenote.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetStyleStoreTest {
    private val storage = FakeWidgetStyleStorage()

    @Test
    fun aWidgetWithoutSettingsStartsWithTheDefaults() {
        assertEquals(WidgetOptions(), WidgetStyleStore.get(storage, 1))
        assertFalse(WidgetStyleStore.exists(storage, 1))
        assertNull(WidgetStyleStore.remembered(storage))
    }

    @Test
    fun theSettingsOfAWidgetAreStoredForThatWidget() {
        val options = WidgetOptions(theme = "dark", accent = "blue", size = "20")
        WidgetStyleStore.set(storage, 7, options)
        assertTrue(WidgetStyleStore.exists(storage, 7))
        assertEquals(options, WidgetStyleStore.get(storage, 7))
    }

    @Test
    fun theSettingsOfTheLastWidgetAreRemembered() {
        val options = WidgetOptions(theme = "dark", accent = "blue", size = "20")
        WidgetStyleStore.set(storage, 7, options)
        assertEquals(options, WidgetStyleStore.remembered(storage))
    }

    @Test
    fun aWidgetAddedLaterStartsWithTheRememberedSettings() {
        WidgetStyleStore.set(storage, 7, WidgetOptions(theme = "dark", accent = "blue"))
        // Removing the widget clears the settings of that widget only.
        WidgetStyleStore.remove(storage, 7)
        assertFalse(WidgetStyleStore.exists(storage, 7))
        // A widget that is added again gets another id and the old settings.
        assertEquals(
            WidgetOptions(theme = "dark", accent = "blue"),
            WidgetStyleStore.get(storage, 8),
        )
    }

    @Test
    fun theSettingsOfTheWidgetWinOverTheRememberedOnes() {
        WidgetStyleStore.remember(storage, WidgetOptions(theme = "dark"))
        WidgetStyleStore.set(storage, 3, WidgetOptions(theme = "light"))
        assertEquals(WidgetOptions(theme = "light"), WidgetStyleStore.get(storage, 3))
    }

    @Test
    fun removingAWidgetKeepsTheRememberedSettings() {
        WidgetStyleStore.remember(storage, WidgetOptions(accent = "green"))
        WidgetStyleStore.remove(storage, 4)
        assertEquals(WidgetOptions(accent = "green"), WidgetStyleStore.remembered(storage))
    }

    @Test
    fun valuesOutOfRangeAreClamped() {
        WidgetStyleStore.set(storage, 1, WidgetOptions(titleSize = 99, opacity = 150))
        val options = WidgetStyleStore.get(storage, 1)
        assertEquals(24, options.titleSize)
        assertEquals(100, options.opacity)
    }

    @Test
    fun rememberedSettingsAreClampedAsWell() {
        WidgetStyleStore.remember(storage, WidgetOptions(titleSize = 2, opacity = -10))
        val options = WidgetStyleStore.get(storage, 9)
        assertEquals(8, options.titleSize)
        assertEquals(0, options.opacity)
    }
}

/** The widget settings in memory, the way the shared preferences hold them. */
private class FakeWidgetStyleStorage : WidgetStyleStorage {
    val values = mutableMapOf<String, Any>()

    override fun contains(key: String): Boolean = values.containsKey(key)

    override fun getString(key: String, fallback: String): String =
        values[key] as? String ?: fallback

    override fun getInt(key: String, fallback: Int): Int = values[key] as? Int ?: fallback

    override fun getBoolean(key: String, fallback: Boolean): Boolean =
        values[key] as? Boolean ?: fallback

    override fun edit(): WidgetStyleStorageEditor = FakeWidgetStyleStorageEditor(values)
}

/** Writes the widget settings in memory. */
private class FakeWidgetStyleStorageEditor(
    private val values: MutableMap<String, Any>,
) : WidgetStyleStorageEditor {
    override fun putString(key: String, value: String) {
        values[key] = value
    }

    override fun putInt(key: String, value: Int) {
        values[key] = value
    }

    override fun putBoolean(key: String, value: Boolean) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }

    override fun apply() = Unit
}
