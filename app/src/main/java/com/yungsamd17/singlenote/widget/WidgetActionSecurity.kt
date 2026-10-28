package com.yungsamd17.singlenote.widget

import android.content.Context

/** Authenticates actions delivered through our widget's private PendingIntent. */
internal object WidgetActionSecurity {
    const val EXTRA_TOKEN = "widget_action_token"
    private const val PREFS = "widget_action_auth"
    private const val TOKEN = "token"

    @Synchronized
    fun token(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(TOKEN, null)?.let { return it }
        val token = java.util.UUID.randomUUID().toString()
        check(prefs.edit().putString(TOKEN, token).commit())
        return token
    }

    fun valid(context: Context, candidate: String?): Boolean {
        if (candidate == null) return false
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(TOKEN, null)
        return saved != null && saved == candidate
    }
}
