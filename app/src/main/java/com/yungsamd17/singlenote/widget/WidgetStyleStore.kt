package com.yungsamd17.singlenote.widget

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** The appearance and the behavior of a single widget. */
internal data class WidgetOptions(
    val theme: String = "system",
    val accent: String = "app",
    val size: String = "medium",
    val action: String = "open",
    val titleSize: Int = 12,
    val showTitle: Boolean = true,
    val opacity: Int = 100,
)

/**
 * The settings of the widgets and the settings the user changed last.
 *
 * A widget id is only valid while the widget sits on the home screen: removing
 * the widget and adding it again hands out a new id. The settings are
 * therefore also kept under [REMEMBERED], so a widget that is added again
 * starts with the settings of the last widget instead of the defaults.
 */
internal object WidgetStyleStore {
    private const val PREFS = "widget_styles"

    /** Key of the remembered settings, kept when a widget is removed. */
    const val REMEMBERED = "remembered"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun storage(context: Context): WidgetStyleStorage =
        SharedPreferencesStyleStorage(prefs(context))

    /** True when the widget has settings of its own. */
    fun exists(context: Context, id: Int): Boolean = exists(storage(context), id)

    fun exists(storage: WidgetStyleStorage, id: Int): Boolean =
        storage.contains(id.toString())

    /**
     * The settings of a widget: its own ones, the remembered ones, or the
     * defaults.
     */
    fun get(context: Context, id: Int): WidgetOptions = get(storage(context), id)

    fun get(storage: WidgetStyleStorage, id: Int): WidgetOptions =
        read(storage, id.toString()) ?: remembered(storage) ?: WidgetOptions()

    /** The settings the user changed last, if there are any. */
    fun remembered(context: Context): WidgetOptions? = remembered(storage(context))

    fun remembered(storage: WidgetStyleStorage): WidgetOptions? = read(storage, REMEMBERED)

    /**
     * Stores the settings of a widget and remembers them, so a widget that is
     * added later starts with them.
     */
    fun set(context: Context, id: Int, options: WidgetOptions) =
        set(storage(context), id, options)

    fun set(storage: WidgetStyleStorage, id: Int, options: WidgetOptions) {
        val editor = storage.edit()
        write(editor, id.toString(), options)
        write(editor, REMEMBERED, options)
        editor.apply()
    }

    /**
     * Remembers the settings without tying them to a widget, so a widget that
     * is added later starts with them.
     */
    fun remember(context: Context, options: WidgetOptions) = remember(storage(context), options)

    fun remember(storage: WidgetStyleStorage, options: WidgetOptions) {
        val editor = storage.edit()
        write(editor, REMEMBERED, options)
        editor.apply()
    }

    /**
     * Removes the settings of a widget. The remembered settings stay, so a
     * widget that is added again starts with the settings of the last widget.
     */
    fun remove(context: Context, id: Int) = remove(storage(context), id)

    fun remove(storage: WidgetStyleStorage, id: Int) {
        val key = id.toString()
        val editor = storage.edit()
        editor.remove(key)
        editor.remove("$key.accent")
        editor.remove("$key.size")
        editor.remove("$key.action")
        editor.remove("$key.titleSize")
        editor.remove("$key.showTitle")
        editor.remove("$key.opacity")
        editor.apply()
    }

    /** Emits the settings of a widget whenever they change. */
    fun observe(context: Context, id: Int) = callbackFlow {
        val prefs = prefs(context)
        val key = id.toString()
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
                val relevant =
                    changed == key ||
                        changed?.startsWith("$key.") == true ||
                        changed == REMEMBERED ||
                        changed?.startsWith("$REMEMBERED.") == true
                if (relevant) trySend(get(context, id))
            }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(get(context, id))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    /** Reads the settings stored under a key, or null when there are none. */
    private fun read(storage: WidgetStyleStorage, key: String): WidgetOptions? {
        if (!storage.contains(key)) return null
        return WidgetOptions(
            theme = storage.getString(key, "system"),
            accent = widgetAccentSetting(storage.getString("$key.accent", "app")),
            size = storage.getString("$key.size", "medium"),
            action = storage.getString("$key.action", "open"),
            titleSize = storage.getInt("$key.titleSize", 12).coerceIn(8, 24),
            showTitle = storage.getBoolean("$key.showTitle", true),
            opacity = storage.getInt("$key.opacity", 100).coerceIn(0, 100),
        )
    }

    /** Writes the settings under a key. */
    private fun write(
        editor: WidgetStyleStorageEditor,
        key: String,
        options: WidgetOptions,
    ) {
        editor.putString(key, options.theme)
        editor.putString("$key.accent", options.accent)
        editor.putString("$key.size", options.size)
        editor.putString("$key.action", options.action)
        editor.putInt("$key.titleSize", options.titleSize)
        editor.putBoolean("$key.showTitle", options.showTitle)
        editor.putInt("$key.opacity", options.opacity)
    }
}

/** The key-value storage the widget settings live in. */
internal interface WidgetStyleStorage {
    fun contains(key: String): Boolean

    fun getString(key: String, fallback: String): String

    fun getInt(key: String, fallback: Int): Int

    fun getBoolean(key: String, fallback: Boolean): Boolean

    fun edit(): WidgetStyleStorageEditor
}

/** Writes the widget settings. */
internal interface WidgetStyleStorageEditor {
    fun putString(key: String, value: String)

    fun putInt(key: String, value: Int)

    fun putBoolean(key: String, value: Boolean)

    fun remove(key: String)

    fun apply()
}

/** The widget settings in the app's shared preferences. */
private class SharedPreferencesStyleStorage(
    private val prefs: SharedPreferences,
) : WidgetStyleStorage {
    override fun contains(key: String): Boolean = prefs.contains(key)

    override fun getString(key: String, fallback: String): String =
        prefs.getString(key, fallback) ?: fallback

    override fun getInt(key: String, fallback: Int): Int = prefs.getInt(key, fallback)

    override fun getBoolean(key: String, fallback: Boolean): Boolean =
        prefs.getBoolean(key, fallback)

    override fun edit(): WidgetStyleStorageEditor = SharedPreferencesStyleEditor(prefs.edit())
}

/** Writes the widget settings into the app's shared preferences. */
private class SharedPreferencesStyleEditor(
    private val editor: SharedPreferences.Editor,
) : WidgetStyleStorageEditor {
    override fun putString(key: String, value: String) {
        editor.putString(key, value)
    }

    override fun putInt(key: String, value: Int) {
        editor.putInt(key, value)
    }

    override fun putBoolean(key: String, value: Boolean) {
        editor.putBoolean(key, value)
    }

    override fun remove(key: String) {
        editor.remove(key)
    }

    override fun apply() {
        editor.apply()
    }
}
