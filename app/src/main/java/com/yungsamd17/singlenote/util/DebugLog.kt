package com.yungsamd17.singlenote.util

import android.os.SystemClock
import com.yungsamd17.singlenote.BuildConfig

/**
 * In-memory editing diagnostics for keyboard/focus issues. Collection is a
 * no-op in release builds; the UI that exposes it (Settings → Copy debug
 * log) is also debug-only, so nothing here ships to users.
 */
object DebugLog {
    private const val MAX_LINES = 300
    private val lock = Any()
    private val lines = ArrayDeque<String>()

    fun log(event: String) {
        if (!BuildConfig.DEBUG) return
        val t = SystemClock.elapsedRealtime()
        synchronized(lock) {
            lines.addLast("$t $event")
            while (lines.size > MAX_LINES) lines.removeFirst()
        }
    }

    fun dump(): String = synchronized(lock) { lines.joinToString("\n") }

    fun clear() {
        synchronized(lock) { lines.clear() }
    }
}
