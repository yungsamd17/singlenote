package com.yungsamd17.singlenote.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Shared motion tokens.
 *
 * Every transition in the app takes its duration from here, so motion reads as
 * one system instead of a pile of hand-picked numbers. The shared curve is a
 * decelerating one (fast out, slow in): it settles quickly, which is what
 * makes a transition feel responsive instead of mechanical.
 */
internal object AppMotion {
    /** Taps, toggles, small state flips (tooltips, indicator, colors). */
    const val FAST_MS = 150

    /** Cards, bars and panels swapping in and out. */
    const val MEDIUM_MS = 220

    /** Full-screen transitions. */
    const val SLOW_MS = 280
}

/** Decelerating spec for float animations (alpha, scale, color). */
internal fun motionSpec(durationMs: Int = AppMotion.MEDIUM_MS): FiniteAnimationSpec<Float> =
    tween(durationMillis = durationMs, easing = FastOutSlowInEasing)

/** Decelerating spec for offset animations (slides in and out). */
internal fun motionOffsetSpec(durationMs: Int = AppMotion.MEDIUM_MS): FiniteAnimationSpec<IntOffset> =
    tween(durationMillis = durationMs, easing = FastOutSlowInEasing)

/** Decelerating spec for size animations (content that grows or shrinks). */
internal fun motionSizeSpec(durationMs: Int = AppMotion.MEDIUM_MS): FiniteAnimationSpec<IntSize> =
    tween(durationMillis = durationMs, easing = FastOutSlowInEasing)

/** Decelerating spec for color animations (the limit indicator blink). */
internal fun motionColorSpec(durationMs: Int = AppMotion.MEDIUM_MS): FiniteAnimationSpec<Color> =
    tween(durationMillis = durationMs, easing = FastOutSlowInEasing)
