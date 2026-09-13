package com.yungsamd17.singlenote.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.yungsamd17.singlenote.data.NotePreferences.Companion.ACCENT_BLUE
import com.yungsamd17.singlenote.data.NotePreferences.Companion.ACCENT_GREEN
import com.yungsamd17.singlenote.data.NotePreferences.Companion.ACCENT_ORANGE
import com.yungsamd17.singlenote.data.NotePreferences.Companion.ACCENT_PINK
import com.yungsamd17.singlenote.data.NotePreferences.Companion.ACCENT_TEAL

// One accent = four tuned tones per mode. Light primaries stay dark enough
// for white text and legible controls on a light background; dark primaries
// stay bright enough on near-black. Containers carry the same hue so filled,
// tonal, and extended buttons read as one family.
private data class AccentTones(
    val lightPrimary: Color,
    val lightOnPrimary: Color,
    val lightContainer: Color,
    val lightOnContainer: Color,
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkContainer: Color,
    val darkOnContainer: Color,
)

private val DefaultTones = AccentTones(
    lightPrimary = Color(0xFF6750A4),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightContainer = Color(0xFFEADDFF),
    lightOnContainer = Color(0xFF21005D),
    darkPrimary = Color(0xFFD0BCFF),
    darkOnPrimary = Color(0xFF381E72),
    darkContainer = Color(0xFF4F378B),
    darkOnContainer = Color(0xFFEADDFF),
)

private val BlueTones = AccentTones(
    lightPrimary = Color(0xFF0B57D0),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightContainer = Color(0xFFD3E3FD),
    lightOnContainer = Color(0xFF041E49),
    darkPrimary = Color(0xFFA8C7FA),
    darkOnPrimary = Color(0xFF062E6F),
    darkContainer = Color(0xFF0842A0),
    darkOnContainer = Color(0xFFD7E3FF),
)

private val TealTones = AccentTones(
    lightPrimary = Color(0xFF00695C),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightContainer = Color(0xFFB2DFDB),
    lightOnContainer = Color(0xFF004D40),
    darkPrimary = Color(0xFF80CBC4),
    darkOnPrimary = Color(0xFF004D40),
    darkContainer = Color(0xFF004D40),
    darkOnContainer = Color(0xFFB2DFDB),
)

private val GreenTones = AccentTones(
    lightPrimary = Color(0xFF2E7D32),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightContainer = Color(0xFFC8E6C9),
    lightOnContainer = Color(0xFF1B5E20),
    darkPrimary = Color(0xFFA5D6A7),
    darkOnPrimary = Color(0xFF1B5E20),
    darkContainer = Color(0xFF1B5E20),
    darkOnContainer = Color(0xFFC8E6C9),
)

private val OrangeTones = AccentTones(
    lightPrimary = Color(0xFF8A5100),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightContainer = Color(0xFFFFDDB3),
    lightOnContainer = Color(0xFF331E00),
    darkPrimary = Color(0xFFFFB787),
    darkOnPrimary = Color(0xFF502400),
    darkContainer = Color(0xFF723600),
    darkOnContainer = Color(0xFFFFDDB3),
)

private val PinkTones = AccentTones(
    lightPrimary = Color(0xFFC2185B),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightContainer = Color(0xFFF8BBD0),
    lightOnContainer = Color(0xFF880E4F),
    darkPrimary = Color(0xFFF48FB1),
    darkOnPrimary = Color(0xFF880E4F),
    darkContainer = Color(0xFF880E4F),
    darkOnContainer = Color(0xFFF8BBD0),
)

private fun tonesFor(accent: String): AccentTones = when (accent) {
    ACCENT_BLUE -> BlueTones
    ACCENT_TEAL -> TealTones
    ACCENT_GREEN -> GreenTones
    ACCENT_ORANGE -> OrangeTones
    ACCENT_PINK -> PinkTones
    else -> DefaultTones
}

// App color scheme for the given accent + mode. Secondary follows primary
// so tonal controls (switches, tonal buttons, FABs) share the accent hue;
// everything else stays on the Material baseline.
fun accentScheme(accent: String, dark: Boolean): ColorScheme {
    val tones = tonesFor(accent)
    return if (dark) {
        darkColorScheme(
            primary = tones.darkPrimary,
            onPrimary = tones.darkOnPrimary,
            primaryContainer = tones.darkContainer,
            onPrimaryContainer = tones.darkOnContainer,
            secondary = tones.darkPrimary,
            onSecondary = tones.darkOnPrimary,
            secondaryContainer = tones.darkContainer,
            onSecondaryContainer = tones.darkOnContainer,
        )
    } else {
        lightColorScheme(
            primary = tones.lightPrimary,
            onPrimary = tones.lightOnPrimary,
            primaryContainer = tones.lightContainer,
            onPrimaryContainer = tones.lightOnContainer,
            secondary = tones.lightPrimary,
            onSecondary = tones.lightOnPrimary,
            secondaryContainer = tones.lightContainer,
            onSecondaryContainer = tones.lightOnContainer,
        )
    }
}
