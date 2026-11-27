package com.yungsamd17.singlenote.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.yungsamd17.singlenote.R
import com.yungsamd17.singlenote.data.NotePreferences.Companion.APP_FONT_FLEX
import com.yungsamd17.singlenote.data.NotePreferences.Companion.APP_FONT_INTER
import com.yungsamd17.singlenote.data.NotePreferences.Companion.APP_FONT_SLAB
import com.yungsamd17.singlenote.data.NotePreferences.Companion.FONT_APP
import com.yungsamd17.singlenote.data.NotePreferences.Companion.FONT_MONO
import com.yungsamd17.singlenote.data.NotePreferences.Companion.FONT_SERIF
import com.yungsamd17.singlenote.data.NotePreferences.Companion.LINE_SPACING_COMPACT
import com.yungsamd17.singlenote.data.NotePreferences.Companion.LINE_SPACING_COMFORTABLE
import com.yungsamd17.singlenote.data.NotePreferences.Companion.SIZE_LARGE
import com.yungsamd17.singlenote.data.NotePreferences.Companion.SIZE_SMALL

// Variable fonts: declaring both weights lets a bold style render on Android
// 12+ (where the wght axis is applied) and fall back gracefully before that.
internal val FlexFont =
    FontFamily(
        Font(R.font.google_sans_flex, FontWeight.Normal),
        Font(R.font.google_sans_flex, FontWeight.Bold),
    )
private val InterFont =
    FontFamily(
        Font(R.font.inter, FontWeight.Normal),
        Font(R.font.inter, FontWeight.Bold),
    )
private val SlabFont =
    FontFamily(
        Font(R.font.roboto_slab, FontWeight.Normal),
        Font(R.font.roboto_slab, FontWeight.Bold),
    )

/** The app-wide font; null means the platform default. */
internal fun appFontFamilyFor(key: String): FontFamily? =
    when (key) {
        APP_FONT_FLEX -> FlexFont
        APP_FONT_INTER -> InterFont
        APP_FONT_SLAB -> SlabFont
        else -> null
    }

/** Shared between the editor, the archive, the preview and card measurement. */
internal fun noteFontFamilyFor(key: String, appFamily: FontFamily?): FontFamily =
    when (key) {
        FONT_APP -> appFamily ?: FontFamily.Default
        FONT_MONO -> FontFamily.Monospace
        FONT_SERIF -> FontFamily.Serif
        else -> FontFamily.Default
    }

internal fun noteFontSizeFor(key: String) =
    when (key) {
        SIZE_SMALL -> 18.sp
        SIZE_LARGE -> 28.sp
        else -> 22.sp
    }

internal fun noteLineHeightFor(sizeKey: String, spacingKey: String): TextUnit {
    val factor =
        when (spacingKey) {
            LINE_SPACING_COMPACT -> 1.2f
            LINE_SPACING_COMFORTABLE -> 1.7f
            else -> 1.45f
        }
    return noteFontSizeFor(sizeKey) * factor
}
