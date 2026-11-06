package com.yungsamd17.singlenote.data

import android.os.Build
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/**
 * Preferences surface the repository depends on. The app wires the real [NotePreferences]
 * singleton; unit tests wire a fake with no DataStore.
 */
interface PreferencesStore {
    val pinned: Flow<Boolean>
    val notificationsEnabled: Flow<Boolean>
    val lockscreenVisible: Flow<Boolean>
    val notificationTitleFirstLine: Flow<Boolean>
    val themeMode: Flow<String>
    val fontFamily: Flow<String>
    val textSize: Flow<String>
    val dynamicColors: Flow<Boolean>
    val amoled: Flow<Boolean>
    val monochrome: Flow<Boolean>
    val appFont: Flow<String>
    val appIcon: Flow<String>
    val accentColor: Flow<String>

    suspend fun setPinned(value: Boolean)

    suspend fun setNotificationsEnabled(value: Boolean)

    suspend fun setLockscreenVisible(value: Boolean)

    suspend fun setNotificationTitleFirstLine(value: Boolean)

    suspend fun setThemeMode(value: String)

    suspend fun setFontFamily(value: String)

    suspend fun setTextSize(value: String)

    suspend fun setDynamicColors(value: Boolean)

    suspend fun setAmoled(value: Boolean)

    suspend fun setMonochrome(value: Boolean)

    suspend fun setAppFont(value: String)

    suspend fun setAppIcon(value: String)

    suspend fun setAccentColor(value: String)
}

/**
 * App-scoped preferences holder. Use [get] everywhere instead of constructing instances per call
 * site: N wrappers on one DataStore file contend on the same file and obscure the single source of
 * truth.
 */
class NotePreferences private constructor(private val context: Context) : PreferencesStore {

    val archiveSort: Flow<String> = context.settingsDataStore.data.map { it[stringPreferencesKey("archive_sort")] ?: "changed" }
    val archiveOrder: Flow<String> = context.settingsDataStore.data.map { it[stringPreferencesKey("archive_order")] ?: "descending" }
    suspend fun setArchiveSort(value: String) { context.settingsDataStore.edit { it[stringPreferencesKey("archive_sort")] = value } }
    suspend fun setArchiveOrder(value: String) { context.settingsDataStore.edit { it[stringPreferencesKey("archive_order")] = value } }

    override val dynamicColors: Flow<Boolean> =
        context.settingsDataStore.data.map {
            resolveDynamicColors(it[KEY_DYNAMIC_COLORS], it[KEY_ACCENT], Build.VERSION.SDK_INT >= 31).also { enabled ->
                context.getSharedPreferences("launch_theme", Context.MODE_PRIVATE).edit().putBoolean("dynamic", enabled).apply()
            }
        }

    override suspend fun setDynamicColors(value: Boolean) {
        context.settingsDataStore.edit {
            if (it[KEY_ACCENT] == ACCENT_SYSTEM) it[KEY_ACCENT] = ACCENT_DEFAULT
            it[KEY_DYNAMIC_COLORS] = value
        }
    }

    override val amoled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_AMOLED] ?: false }

    override suspend fun setAmoled(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_AMOLED] = value }
    }

    // Off by default: the app keeps its accent colors until the user asks
    // for a grayscale interface.
    override val monochrome: Flow<Boolean> =
        context.settingsDataStore.data.map {
            (it[KEY_MONOCHROME] ?: false).also { enabled ->
                // The launch window is painted before the first frame: keep
                // the flag there so it can match the gray scheme.
                context.getSharedPreferences("launch_theme", Context.MODE_PRIVATE).edit().putBoolean("monochrome", enabled).apply()
            }
        }

    override suspend fun setMonochrome(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_MONOCHROME] = value }
    }

    override val appFont: Flow<String> =
        context.settingsDataStore.data.map { it[KEY_APP_FONT] ?: "system" }

    override suspend fun setAppFont(value: String) {
        context.settingsDataStore.edit { it[KEY_APP_FONT] = value }
    }

    override val appIcon: Flow<String> =
        context.settingsDataStore.data.map { it[KEY_APP_ICON] ?: "default" }

    override suspend fun setAppIcon(value: String) {
        context.settingsDataStore.edit { it[KEY_APP_ICON] = value }
    }

    override val pinned: Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_PINNED] ?: false }
    override val notificationsEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_NOTIFICATIONS] ?: true }
    // On by default: showing on the lock screen is the point of pinning,
    // and the toggle stays one tap away in Settings for opting out.
    override val lockscreenVisible: Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_LOCKSCREEN_VISIBLE] ?: true }
    // Off by default: the pinned notification keeps its own title until the
    // user asks for the note's first line to take its place.
    override val notificationTitleFirstLine: Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_NOTIFICATION_TITLE_FIRST_LINE] ?: false }
    override val themeMode: Flow<String> =
        context.settingsDataStore.data.map { it[KEY_THEME] ?: THEME_SYSTEM }
    override val fontFamily: Flow<String> =
        context.settingsDataStore.data.map { it[KEY_FONT_FAMILY] ?: FONT_DEFAULT }
    override val textSize: Flow<String> =
        context.settingsDataStore.data.map { it[KEY_TEXT_SIZE] ?: SIZE_MEDIUM }
    override val accentColor: Flow<String> =
        context.settingsDataStore.data.map { resolveAccentColor(it[KEY_ACCENT]) }

    override suspend fun setPinned(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_PINNED] = value }
    }

    override suspend fun setNotificationsEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_NOTIFICATIONS] = value }
    }

    override suspend fun setLockscreenVisible(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_LOCKSCREEN_VISIBLE] = value }
    }

    override suspend fun setNotificationTitleFirstLine(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_NOTIFICATION_TITLE_FIRST_LINE] = value }
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
        @Volatile private var instance: NotePreferences? = null

        /**
         * Single app-scoped instance (holds the application context, never an activity). Mirrors
         * [AppDatabase.get].
         */
        fun get(context: Context): NotePreferences =
            instance
                ?: synchronized(this) {
                    instance ?: NotePreferences(context.applicationContext).also { instance = it }
                }

        const val KEY_PINNED_NAME = "pin_current_note"

        val KEY_DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
        val KEY_AMOLED = booleanPreferencesKey("amoled")
        val KEY_MONOCHROME = booleanPreferencesKey("monochrome")
        val KEY_APP_FONT = stringPreferencesKey("app_font")
        val KEY_APP_ICON = stringPreferencesKey("app_icon")
        val KEY_PINNED = booleanPreferencesKey(KEY_PINNED_NAME)
        val KEY_NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val KEY_LOCKSCREEN_VISIBLE = booleanPreferencesKey("lockscreen_visible")
        val KEY_NOTIFICATION_TITLE_FIRST_LINE = booleanPreferencesKey("notification_title_first_line")
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
        const val ACCENT_SYSTEM = "system"

        // Preserve legacy wallpaper colors through the dedicated toggle, without offering
        // a second, hidden way to enable them through the accent picker.
        fun resolveAccentColor(stored: String?): String =
            if (stored == null || stored == ACCENT_SYSTEM) ACCENT_DEFAULT else stored

        fun resolveDynamicColors(stored: Boolean?, legacyAccent: String?, supported: Boolean = true): Boolean =
            supported && (stored ?: (legacyAccent == ACCENT_SYSTEM))

        val THEMES = listOf(THEME_SYSTEM, THEME_LIGHT, THEME_DARK)
        val FONTS = listOf(FONT_DEFAULT, FONT_MONO, FONT_SERIF)
        val SIZES = listOf(SIZE_SMALL, SIZE_MEDIUM, SIZE_LARGE)
        val ACCENTS =
            listOf(
                ACCENT_DEFAULT,
                ACCENT_BLUE,
                ACCENT_TEAL,
                ACCENT_GREEN,
                ACCENT_ORANGE,
                ACCENT_PINK,
            )
    }
}
