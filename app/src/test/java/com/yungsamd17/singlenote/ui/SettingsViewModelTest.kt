package com.yungsamd17.singlenote.ui

import com.yungsamd17.singlenote.data.Note
import com.yungsamd17.singlenote.data.NotePreferences.Companion.ACCENT_DEFAULT
import com.yungsamd17.singlenote.data.NotePreferences.Companion.FONT_DEFAULT
import com.yungsamd17.singlenote.data.NotePreferences.Companion.SIZE_MEDIUM
import com.yungsamd17.singlenote.data.NotePreferences.Companion.THEME_SYSTEM
import com.yungsamd17.singlenote.data.NoteStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the settings ViewModel: the stored values reach the screen and every
 * setter writes back to the store.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private class FakeNoteStore : NoteStore {
        override val activeNote = MutableStateFlow<Note?>(null)
        override val pinned = MutableStateFlow(false)
        override val notificationsEnabled = MutableStateFlow(true)
        override val lockscreenVisible = MutableStateFlow(true)
        override val notificationTitleFirstLine = MutableStateFlow(false)
        override val themeMode = MutableStateFlow(THEME_SYSTEM)
        override val fontFamily = MutableStateFlow(FONT_DEFAULT)
        override val textSize = MutableStateFlow(SIZE_MEDIUM)
        override val dynamicColors = MutableStateFlow(false)
        override val amoled = MutableStateFlow(false)
        override val monochrome = MutableStateFlow(false)
        override val appFont = MutableStateFlow("system")
        override val appIcon = MutableStateFlow("default")
        override val accentColor = MutableStateFlow(ACCENT_DEFAULT)

        override suspend fun getActive(): Note? = activeNote.value
        override suspend fun saveActive(content: String) = Unit
        override suspend fun archiveActive() = Unit
        override suspend fun deleteActive() = Unit
        override suspend fun setPinned(value: Boolean) { pinned.value = value }
        override suspend fun setNotificationsEnabled(value: Boolean) { notificationsEnabled.value = value }
        override suspend fun setLockscreenVisible(value: Boolean) { lockscreenVisible.value = value }
        override suspend fun setNotificationTitleFirstLine(value: Boolean) { notificationTitleFirstLine.value = value }
        override suspend fun setThemeMode(value: String) { themeMode.value = value }
        override suspend fun setFontFamily(value: String) { fontFamily.value = value }
        override suspend fun setTextSize(value: String) { textSize.value = value }
        override suspend fun setDynamicColors(value: Boolean) { dynamicColors.value = value }
        override suspend fun setAmoled(value: Boolean) { amoled.value = value }
        override suspend fun setMonochrome(value: Boolean) { monochrome.value = value }
        override suspend fun setAppFont(value: String) { appFont.value = value }
        override suspend fun setAppIcon(value: String) { appIcon.value = value }
        override suspend fun setAccentColor(value: String) { accentColor.value = value }
    }

    private lateinit var store: FakeNoteStore

    @Before
    fun setUp() {
        store = FakeNoteStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.installMain() {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    }

    @Test
    fun becomesReadyOnceTheStoredValuesArrived() = runTest {
        installMain()
        val model = SettingsViewModel(store)

        assertFalse(model.ready.value)
        advanceUntilIdle()
        assertTrue(model.ready.value)
    }

    @Test
    fun exposesTheStoredValues() = runTest {
        installMain()
        store.themeMode.value = "dark"
        store.accentColor.value = "blue"
        store.amoled.value = true
        val model = SettingsViewModel(store)

        // The flows share their upstream only while somebody listens, and a
        // StateFlow always has a value: collect first, then read it.
        val job = launch {
            launch { model.themeMode.collect { } }
            launch { model.accentColor.collect { } }
            launch { model.amoled.collect { } }
        }
        advanceUntilIdle()

        assertEquals("dark", model.themeMode.value)
        assertEquals("blue", model.accentColor.value)
        assertTrue(model.amoled.value)
        job.cancel()
    }

    @Test
    fun settersWriteToTheStore() = runTest {
        installMain()
        val model = SettingsViewModel(store)
        advanceUntilIdle()

        model.setThemeMode("light")
        model.setFontFamily("serif")
        model.setTextSize("large")
        model.setAccentColor("teal")
        model.setNotificationsEnabled(false)
        model.setLockscreenVisible(false)
        model.setNotificationTitleFirstLine(true)
        model.setDynamicColors(true)
        model.setAmoled(true)
        model.setMonochrome(true)
        model.setAppFont("mono")
        model.setAppIcon("dynamic")
        advanceUntilIdle()

        assertEquals("light", store.themeMode.value)
        assertEquals("serif", store.fontFamily.value)
        assertEquals("large", store.textSize.value)
        assertEquals("teal", store.accentColor.value)
        assertFalse(store.notificationsEnabled.value)
        assertFalse(store.lockscreenVisible.value)
        assertTrue(store.notificationTitleFirstLine.value)
        assertTrue(store.dynamicColors.value)
        assertTrue(store.amoled.value)
        assertTrue(store.monochrome.value)
        assertEquals("mono", store.appFont.value)
        assertEquals("dynamic", store.appIcon.value)
    }

    @Test
    fun factoryBuildsTheSameModel() = runTest {
        installMain()
        val model = SettingsViewModel.factory(store).create(SettingsViewModel::class.java)
        advanceUntilIdle()

        assertTrue(model.ready.value)
    }
}

