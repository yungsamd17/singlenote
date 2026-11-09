package com.yungsamd17.singlenote.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * True when the page content is taller than the space below the toolbar.
 *
 * The toolbar is pinned, so the viewport never changes while scrolling:
 * measuring it alone keeps the answer stable and stops the scroll state
 * from flip-flopping mid-gesture.
 */
internal fun settingsContentOverflows(
    contentHeight: Int,
    viewportHeight: Int,
): Boolean = contentHeight > 0 && viewportHeight > 0 && contentHeight > viewportHeight

/** Scroll bookkeeping for the settings and about pages. */
internal class SettingsScrollState(val content: ScrollState) {
    var contentHeight by mutableIntStateOf(0)
    var viewportHeight by mutableIntStateOf(0)
    val measured by derivedStateOf { contentHeight > 0 && viewportHeight > 0 }
    val overflows by derivedStateOf {
        settingsContentOverflows(contentHeight, viewportHeight)
    }
}

@Composable
internal fun rememberSettingsScrollState(): SettingsScrollState {
    val content = rememberScrollState()
    val state = remember(content) { SettingsScrollState(content) }
    LaunchedEffect(state.measured, state.overflows) {
        // The page only stops overflowing when the content or the viewport
        // changes (rotation, font scale, another language). Park the offset
        // at the top then, instead of leaving the content half scrolled.
        if (state.measured && !state.overflows) {
            content.scrollTo(0)
        }
    }
    return state
}

/** Minimal movement keeps the caret and a little breathing room inside the editor viewport. */
internal fun caretScrollOffset(
    caretTop: Int,
    caretBottom: Int,
    viewportHeight: Int,
    currentOffset: Int,
    maxOffset: Int,
    margin: Int,
): Int {
    if (viewportHeight <= 0) return currentOffset.coerceIn(0, maxOffset)
    val top = caretTop - margin
    val bottom = caretBottom + margin
    val target =
        when {
            top < currentOffset -> top
            bottom > currentOffset + viewportHeight -> bottom - viewportHeight
            else -> currentOffset
        }
    return target.coerceIn(0, maxOffset)
}
