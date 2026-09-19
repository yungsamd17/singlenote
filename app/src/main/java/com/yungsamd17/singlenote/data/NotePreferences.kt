package com.yungsamd17.singlenote.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/**
 * Preferences surface the repository depends on. The app wires the real
 * [NotePreferences] singleton; unit tests wire a fake with no DataStore.
 */
interface PreferencesStore {
    val pinned: Flow<Boolean>
    val notificationsEnabled: Flow<Boolean>
    val lockscreenVisible: Flow<Boolean>
    val themeMode: Flow<String>
    val fontFamily: Flow<String>
    val textSize: Flow<String>
    val accentColor: Flow<String>

    suspend fun setPinned(value: Boolean)
    suspend fun setNotificationsEnabled(value: Boolean)
    suspend fun setLockscreenVisible(value: Boolean)
    suspend fun setThemeMode(value: String)
    suspend fun setFontFamily(value: String)
    suspend fun setTextSize(value: String)
    suspend fun setAccentColor(value: String)
}

/**
 * App-scoped preferences holder. Use [get] everywhere instead of
 * constructing instances per call site: N wrappers on one DataStore file
 * contend on the same file and obscure the single source of truth.
 */
class NotePreferences private constructor(private val context: Context) : PreferencesStore {

    override val pinned: Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_PINNED] ?: false }
    override val notificationsEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_NOTIFICATIONS] ?: true }
<    override val lockscreenVisible: Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_LOCKSCREEN_VISIBLE] ?: false }
    override val themeMode: Flow<String> =
        context.settingsDataStore.data.map { it[KEY_THEME] ?: THEME_SYSTEM }
    override val fontFamily: Flow<String> =
        context.settingsDataStore.data.map { it[KEY_FONT_FAMILY] ?: FONT_DEFAULT }
    override val textSize: Flow<String> =
        context.settingsDataStore.data.map { it[KEY_TEXT_SIZE] ?: SIZE_MEDIUM }
    override val accentColor: Flow<String> =
        context.settingsDataStore.data.map { it[KEY_ACCENT] ?: ACCENT_DEFAULT }

    override suspend fun setPinned(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_PINNED] = value }
    }

    override suspend fun setNotificationsEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_NOTIFICATIONS] = value }
    }

<    override suspend fun setLockscreenVisible(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_LOCKSCREEN_VISIBLE] = value }
    }

    override suspend fun setThemeMode(value: String) {
        context.settingsDataStore.edit { it[KEY_THEME] = value }
    }

    override suspend fun setFontFamily(value: String) {
        context.settingsDataStore.edit { it[KEY_FONT_FAMILY] = value }
    }

    override suspend fun setTextSize(value: String) {
        context.settingsDataStore.edit { it[KEY_TEXT_SIZE] = value }
    }

    override suspend fun setAccentColor(value: String) {
        context.settingsDataStore.edit { it[KEY_ACCENT] = value }
    }

    companion object {
        @Volatile
        private var instance: NotePreferences? = null

        /**
         * Single app-scoped instance (holds the application context, never
         * an activity). Mirrors [AppDatabase.get].
         */
        fun get(context: Context): NotePreferences =
            instance ?: synchronized(this) {
                instance ?: NotePreferences(context.applicationContext).also { instance = it }
            }

        const val KEY_PINNED_NAME = "pin_current_note"

        val KEY_PINNED = booleanPreferencesKey(KEY_PINNED_NAME)
        val KEY_NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val KEY_LOCKSCREEN_VISIBLE = booleanPreferencesKey("lockscreen_visible")
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_FONT_FAMILY = stringPreferencesKey("font_family")
        val KEY_TEXT_SIZE = stringPreferencesKey("text_size")
        val KEY_ACCENT = stringPreferencesKey("accent_color")

        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        const val FONT_DEFAULT = "default"
        const val FONT_MONO = "mono"
        const val FONT_SERIF = "serif"

        const val SIZE_SMALL = "small"
        const val SIZE_MEDIUM = "medium"
        const val SIZE_LARGE = "large"

        const val ACCENT_DEFAULT = "default"
        const val ACCENT_BLUE = "blue"
        const val ACCENT_TEAL = "teal"
        const val ACCENT_GREEN = "green"
        const val ACCENT_ORANGE = "orange"
        const val ACCENT_PINK = "pink"

        val THEMES = listOf(THEME_SYSTEM, THEME_LIGHT, THEME_DARK)
        val FONTS = listOf(FONT_DEFAULT, FONT_MONO, FONT_SERIF)
        val SIZES = listOf(SIZE_SMALL, SIZE_MEDIUM, SIZE_LARGE)
        val ACCENTS =
            listOf(ACCENT_DEFAULT, ACCENT_BLUE, ACCENT_TEAL, ACCENT_GREEN, ACCENT_ORANGE, ACCENT_PINK)
    }
}
