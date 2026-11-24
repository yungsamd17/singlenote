package com.yungsamd17.singlenote.widget

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG contrast helpers for the widget. The text color is chosen against the
 * *effective* background — the theme card composited over whatever is actually
 * behind the widget (wallpaper on the home screen, the activity surface in the
 * configuration preview) at the chosen opacity.
 */
internal object WidgetContrast {

    /** Alpha-composites [foreground] over [backdrop] using [opacity]. */
    fun composite(foreground: Color, backdrop: Color, opacity: Float): Color =
        Color(
            red = foreground.red * opacity + backdrop.red * (1f - opacity),
            green = foreground.green * opacity + backdrop.green * (1f - opacity),
            blue = foreground.blue * opacity + backdrop.blue * (1f - opacity),
        )

    private fun channel(value: Float): Float =
        if (value <= 0.03928f) value / 12.92f
        else ((value + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()

    fun luminance(color: Color): Float =
        0.2126f * channel(color.red) + 0.7152f * channel(color.green) + 0.0722f * channel(color.blue)

    fun ratio(a: Color, b: Color): Float {
        val first = luminance(a)
        val second = luminance(b)
        return (max(first, second) + 0.05f) / (min(first, second) + 0.05f)
    }

    /** True when the dark ink is more readable on [background]. */
    fun prefersDarkInk(inkOnLight: Color, inkOnDark: Color, background: Color): Boolean =
        ratio(inkOnLight, background) >= ratio(inkOnDark, background)
}
