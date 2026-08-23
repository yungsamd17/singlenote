package com.yungsamd17.singlenote.util

fun firstNonBlankLine(text: String): String? =
    text.lineSequence().firstOrNull { it.isNotBlank() }

fun truncate(text: String, maxLength: Int): String =
    if (text.length <= maxLength) text else text.take(maxLength)
