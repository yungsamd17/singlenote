package com.yungsamd17.singlenote.ui

import com.yungsamd17.singlenote.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageEntriesTest {
    @Test
    fun everyLanguageRowCarriesADescription() {
        // Each row renders a second line, so the list keeps one row height:
        // the device-language row used to be shorter than the rest.
        assertTrue(languageEntries.all { it.description != 0 })
    }

    @Test
    fun descriptionsNameTheLanguageNotTheCountry() {
        // The second line spells the language out in English (Polski →
        // Polish), and the device row says the app follows the system.
        val expected = mapOf(
            "" to R.string.theme_system,
            "en-US" to R.string.language_english_us,
            "de-DE" to R.string.language_german_english,
            "es-ES" to R.string.language_spanish_english,
            "fr-FR" to R.string.language_french_english,
            "pl-PL" to R.string.language_polish_english,
            "ru-RU" to R.string.language_russian_english,
            "zh-CN" to R.string.language_chinese_english,
            "sk-SK" to R.string.language_slovak_english,
        )
        assertEquals(expected, languageEntries.associate { it.code to it.description })
    }

    @Test
    fun deviceLanguageIsTheFirstEntry() {
        // The empty code follows the device language.
        assertTrue(languageEntries.first().code.isEmpty())
    }
}
