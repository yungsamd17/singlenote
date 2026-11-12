package com.yungsamd17.singlenote.ui

import com.yungsamd17.singlenote.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageEntriesTest {
    @Test
    fun everyLanguageRowCarriesBothNames() {
        // Each row renders two lines — the English name and the native one —
        // so the list keeps one row height: the device-language row used to be
        // shorter than the rest.
        assertTrue(languageEntries.all { it.englishName != 0 && it.nativeName != 0 })
    }

    @Test
    fun theFirstLineNamesTheLanguageInEnglish() {
        // The first line spells the language out in English (Deutsch →
        // German), and the device row says the app follows the system.
        val expected = mapOf(
            "" to R.string.theme_system,
            "en-US" to R.string.language_english_english,
            "de-DE" to R.string.language_german_english,
            "es-ES" to R.string.language_spanish_english,
            "fr-FR" to R.string.language_french_english,
            "it-IT" to R.string.language_italian_english,
            "nl-NL" to R.string.language_dutch_english,
            "pl-PL" to R.string.language_polish_english,
            "pt-BR" to R.string.language_portuguese_english,
            "ru-RU" to R.string.language_russian_english,
            "zh-CN" to R.string.language_chinese_english,
            "sk-SK" to R.string.language_slovak_english,
            "tr-TR" to R.string.language_turkish_english,
        )
        assertEquals(expected, languageEntries.associate { it.code to it.englishName })
    }

    @Test
    fun theSecondLineNamesTheLanguageInItsOwnScript() {
        // The second line shows the language as its own speakers write it.
        val expected = mapOf(
            "" to R.string.settings_device_language,
            "en-US" to R.string.language_english,
            "de-DE" to R.string.language_german,
            "es-ES" to R.string.language_spanish,
            "fr-FR" to R.string.language_french,
            "it-IT" to R.string.language_italian,
            "nl-NL" to R.string.language_dutch,
            "pl-PL" to R.string.language_polish,
            "pt-BR" to R.string.language_portuguese,
            "ru-RU" to R.string.language_russian,
            "zh-CN" to R.string.language_chinese,
            "sk-SK" to R.string.language_slovak,
            "tr-TR" to R.string.language_turkish,
        )
        assertEquals(expected, languageEntries.associate { it.code to it.nativeName })
    }

    @Test
    fun deviceLanguageIsTheFirstEntry() {
        // The empty code follows the device language.
        assertTrue(languageEntries.first().code.isEmpty())
    }

    @Test
    fun languagesAreAlphabeticalAfterEnglish() {
        // English stays at the top as the source language; everything after it
        // is ordered by the English name on the first line, with the Chinese
        // variant last, the way other apps group the regional variants.
        assertEquals(
            listOf(
                "en-US", "nl-NL", "fr-FR", "de-DE", "it-IT", "pl-PL",
                "pt-BR", "ru-RU", "sk-SK", "es-ES", "tr-TR", "zh-CN",
            ),
            languageEntries.drop(1).map { it.code },
        )
    }

    @Test
    fun everyLanguageCarriesTheRegionCodeForItsBadge() {
        // The round badge in front of a row shows the region of the locale;
        // only the device row has none.
        assertTrue(languageEntries.drop(1).all { it.code.substringAfter('-', "").length == 2 })
        assertEquals("", languageEntries.first().code.substringAfter('-', ""))
    }
}
