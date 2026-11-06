package com.yungsamd17.singlenote.util

fun firstNonBlankLine(text: String): String? =
    text.lineSequence().firstOrNull { it.isNotBlank() }

fun truncate(text: String, maxLength: Int): String =
    if (text.length <= maxLength) text else text.take(maxLength)

/** Android does not cap notification titles, so the app caps it itself. */
const val NOTIFICATION_TITLE_MAX_CHARS = 20

/**
 * Notification title built from the note: its first non-blank line, trimmed
 * to [maxChars] and marked with an ellipsis when it had to be cut.
 */
fun notificationTitle(text: String, maxChars: Int = NOTIFICATION_TITLE_MAX_CHARS): String {
    val line = firstNonBlankLine(text)?.trim().orEmpty()
    if (line.isEmpty()) return ""
    if (line.length <= maxChars) return line
    // The ellipsis is part of the budget, so the title never grows past it.
    return line.take((maxChars - 1).coerceAtLeast(1)).trimEnd() + "…"
}
