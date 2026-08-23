package com.yungsamd17.singlenote.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class NotePreferences(private val context: Context) {

    val pinned: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_PINNED] ?: false }
    val notificationsEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_NOTIFICATIONS] ?: true }
    val showPinButton: Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_SHOW_PIN_BUTTON] ?: true }
    val themeMode: Flow<String> = context.settingsDataStore.data.map { it[KEY_THEME] ?: THEME_SYSTEM }
    val fontFamily: Flow<String> =
        context.settingsDataStore.data.map { it[KEY_FONT_FAMILY] ?: FONT_DEFAULT }
    val textSize: Flow<String> =
        context.settingsDataStore.data.map { it[KEY_TEXT_SIZE] ?: SIZE_MEDIUM }

    suspend fun setPinned(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_PINNED] = value }
    }

    suspend fun setNotificationsEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_NOTIFICATIONS] = value }
    }

    suspend fun setShowPinButton(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_SHOW_PIN_BUTTON] = value }
    }

    suspend fun setThemeMode(value: String) {
        context.settingsDataStore.edit { it[KEY_THEME] = value }
    }

    suspend fun setFontFamily(value: String) {
        context.settingsDataStore.edit { it[KEY_FONT_FAMILY] = value }
    }

    suspend fun setTextSize(value: String) {
        context.settingsDataStore.edit { it[KEY_TEXT_SIZE] = value }
    }

    companion object {
        const val KEY_PINNED_NAME = "pin_current_note"

        val KEY_PINNED = booleanPreferencesKey(KEY_PINNED_NAME)
        val KEY_NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val KEY_SHOW_PIN_BUTTON = booleanPreferencesKey("show_pin_button")
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_FONT_FAMILY = stringPreferencesKey("font_family")
        val KEY_TEXT_SIZE = stringPreferencesKey("text_size")

        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        const val FONT_DEFAULT = "default"
        const val FONT_MONO = "mono"
        const val FONT_SERIF = "serif"

        const val SIZE_SMALL = "small"
        const val SIZE_MEDIUM = "medium"
        const val SIZE_LARGE = "large"

        val THEMES = listOf(THEME_SYSTEM, THEME_LIGHT, THEME_DARK)
        val FONTS = listOf(FONT_DEFAULT, FONT_MONO, FONT_SERIF)
        val SIZES = listOf(SIZE_SMALL, SIZE_MEDIUM, SIZE_LARGE)
    }
}
