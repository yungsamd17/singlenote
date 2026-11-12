package com.yungsamd17.singlenote.util

import android.content.Context

/**
 * Authenticates actions delivered through one of our own private
 * PendingIntents (the home-screen widget and the pinned notification).
 *
 * The activity behind those intents is exported, so without this token any
 * other app could fire a widget action or the quick capture and have the
 * user's note archived behind their back.
 */
internal object ActionSecurity {
    const val EXTRA_TOKEN = "action_token"
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
