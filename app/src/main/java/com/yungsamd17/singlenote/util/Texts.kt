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

/**
 * The note's text without its first non-blank line.
 *
 * When the notification title already shows that first line, the body picks
 * up at the second one, so the notification does not repeat itself.
 */
fun notificationBody(text: String): String {
    val lines = text.lines()
    val first = lines.indexOfFirst { it.isNotBlank() }
    if (first < 0) return ""
    return lines.drop(first + 1).joinToString("\n").trim()
}
