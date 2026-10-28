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

    private val transitions = java.util.WeakHashMap<Activity, android.widget.ImageView>()

    fun setLanguage(context: Context, languageTag: String) {
        if (getLanguageTag(context) == languageTag) return
        val activity = context.findActivity()
        val content = activity?.findViewById<android.view.ViewGroup>(android.R.id.content)
        if (activity == null || content == null || content.width == 0 || content.height == 0) {
            applyLanguage(context, languageTag)
            return
        }
        transitions.remove(activity)?.let { content.overlay.remove(it) }
        val bitmap = android.graphics.Bitmap.createBitmap(
            content.width, content.height, android.graphics.Bitmap.Config.ARGB_8888
        )
        content.draw(android.graphics.Canvas(bitmap))
        val snapshot = android.widget.ImageView(activity).apply {
            setImageBitmap(bitmap)
            layout(0, 0, content.width, content.height)
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        transitions[activity] = snapshot
        content.overlay.add(snapshot)
        applyLanguage(context, languageTag)
        // Also handle selecting an explicit locale identical to the system
        // locale, which may not dispatch a configuration change.
        content.postDelayed({ finishTransition(activity) }, 500)
    }

    fun finishTransition(activity: Activity) {
        val snapshot = transitions.remove(activity) ?: return
        val content = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(
            object : android.view.ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    content.viewTreeObserver.removeOnPreDrawListener(this)
                    snapshot.animate().alpha(0f).setDuration(240)
                        .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
                        .withEndAction {
                            content.overlay.remove(snapshot)
                            snapshot.setImageDrawable(null)
                        }.start()
                    return true
                }
            }
        )
        content.invalidate()
    }

    private fun applyLanguage(context: Context, languageTag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = if (languageTag.isBlank()) LocaleList.getEmptyLocaleList()
            else LocaleList.forLanguageTags(languageTag)
            context.getSystemService(LocaleManager::class.java).applicationLocales = locales
        } else {
            preferences(context).edit().putString(LANGUAGE_KEY, languageTag).apply()
            val activity = context.findActivity() ?: return
            val configuration = Configuration(activity.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(languageTag))
            }
            // Older Android versions have no app-locale manager. Update the
            // existing activity and dispatch the same configuration callback
            // Compose observes on Android 13+, preserving its window and state.
            @Suppress("DEPRECATION")
            activity.resources.updateConfiguration(configuration, activity.resources.displayMetrics)
            activity.onConfigurationChanged(configuration)
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
