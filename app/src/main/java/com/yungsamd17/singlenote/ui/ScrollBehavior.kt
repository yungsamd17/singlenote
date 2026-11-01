package com.yungsamd17.singlenote.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Compare against the expanded viewport so collapsing the bar cannot disable its own expansion. */
internal fun settingsContentOverflows(
    contentHeight: Int,
    viewportHeight: Int,
    barOffset: Float,
): Boolean = contentHeight > 0 && viewportHeight > 0 && contentHeight > viewportHeight + barOffset

@OptIn(ExperimentalMaterial3Api::class)
internal class SettingsScrollState(val content: ScrollState, val appBar: TopAppBarState) {
    var contentHeight by mutableIntStateOf(0)
    var viewportHeight by mutableIntStateOf(0)
    val measured by derivedStateOf { contentHeight > 0 && viewportHeight > 0 }
    val overflows by derivedStateOf {
        settingsContentOverflows(contentHeight, viewportHeight, appBar.heightOffset)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberSettingsScrollState(): SettingsScrollState {
    val content = rememberScrollState()
    val appBar = rememberTopAppBarState()
    val state = remember(content, appBar) { SettingsScrollState(content, appBar) }
    LaunchedEffect(state.measured, state.overflows) {
        if (state.measured && !state.overflows) {
            appBar.heightOffset = 0f
            appBar.contentOffset = 0f
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
