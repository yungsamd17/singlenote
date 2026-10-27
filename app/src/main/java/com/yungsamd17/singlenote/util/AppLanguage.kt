package com.yungsamd17.singlenote.util

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList

/** Stores the app's optional language override and leaves it unset to follow the system. */
object AppLanguage {
    private const val PREFERENCES_NAME = "app_language"
    private const val LANGUAGE_KEY = "language_tag"

    fun getLanguageTag(context: Context): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)
                .applicationLocales
                .toLanguageTags()
        } else {
            preferences(context).getString(LANGUAGE_KEY, "").orEmpty()
        }

    fun setLanguage(context: Context, languageTag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = if (languageTag.isBlank()) LocaleList.getEmptyLocaleList()
            else LocaleList.forLanguageTags(languageTag)
            context.getSystemService(LocaleManager::class.java).applicationLocales = locales
        } else {
            preferences(context).edit().putString(LANGUAGE_KEY, languageTag).apply()
            context.findActivity()?.recreate()
        }
    }

    /** Applies the saved choice on Android 12 and earlier, where app locales are not platform-managed. */
    fun wrap(context: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return context
        val languageTag = preferences(context).getString(LANGUAGE_KEY, "").orEmpty()
        if (languageTag.isBlank()) return context

        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(languageTag))
        }
        return context.createConfigurationContext(configuration)
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private fun Context.findActivity(): Activity? {
        var current: Context = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return current as? Activity
    }
}
