package com.yungsamd17.singlenote.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the shipped translations in lockstep with the English source.
 *
 * Android falls back to English for a missing key, so a translation can drift
 * without any compile error. This JVM test fails `testDebugUnitTest` when a
 * locale folder is missing, a key is missing or stale, or a numbered format
 * placeholder changes type or disappears.
 */
class TranslationCompletenessTest {

    private val resources = locateResources()

    @Test
    fun everyConfiguredLocaleHasATranslationFolder() {
        val languages = localeTags()
            .map { it.substringBefore('-') }
            .filter { it != "en" }
            .toSet()

        assertEquals(
            "translation folders must match locales_config.xml",
            languages,
            localeFolders().map { it.name.removePrefix("values-") }.toSet(),
        )
        languages.forEach { language ->
            assertTrue(
                "missing values-$language/strings.xml",
                File(resources, "values-$language/strings.xml").isFile,
            )
        }
    }

    @Test
    fun everyTranslationHasExactlyTheSourceKeys() {
        val source = strings(File(resources, "values/strings.xml"))

        localeFolders().forEach { folder ->
            val translated = strings(File(folder, "strings.xml"))
            assertEquals(
                "stale keys in ${folder.name}",
                emptySet<String>(),
                translated.keys - source.keys,
            )
            assertEquals(
                "missing keys in ${folder.name}",
                emptySet<String>(),
                source.keys - translated.keys,
            )
        }
    }

    @Test
    fun placeholdersKeepTheirNumbersAndTypes() {
        val source = strings(File(resources, "values/strings.xml"))

        localeFolders().forEach { folder ->
            strings(File(folder, "strings.xml")).forEach { (key, value) ->
                assertEquals(
                    "placeholders for '$key' in ${folder.name}",
                    placeholders(source.getValue(key)),
                    placeholders(value),
                )
            }
        }
    }

    private fun localeTags(): List<String> =
        LOCALE.findAll(File(resources, "xml/locales_config.xml").readText())
            .map { it.groupValues[1] }
            .toList()

    /** Language-only folders; config qualifiers such as values-night are not locales. */
    private fun localeFolders(): List<File> =
        resources.listFiles().orEmpty()
            .filter { it.isDirectory && LANG_FOLDER.matches(it.name) }
            .sortedBy { it.name }

    private fun strings(file: File): Map<String, String> =
        STRING.findAll(file.readText()).associate { it.groupValues[1] to it.groupValues[2] }

    /** Sorted so a reordered sentence with the same placeholders still passes. */
    private fun placeholders(value: String): List<String> =
        PLACEHOLDER.findAll(value).map { it.value }.sorted().toList()

    companion object {
        private val LANG_FOLDER = Regex("values-[a-z]{2,3}")
        private val STRING = Regex("""<string\s+name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        private val LOCALE = Regex("""<locale\s+android:name="([^"]+)"""")
        private val PLACEHOLDER = Regex("%(?:\\d+\\$)?[-#+ 0,(<]*\\d*(?:\\.\\d+)?[a-zA-Z%]")

        private fun locateResources(): File {
            var directory = File(System.getProperty("user.dir") ?: ".")
            repeat(4) {
                for (relative in listOf("src/main/res", "app/src/main/res")) {
                    val candidate = File(directory, relative)
                    if (candidate.isDirectory) return candidate
                }
                directory = directory.parentFile ?: return@repeat
            }
            error("Cannot locate app/src/main/res from ${System.getProperty("user.dir")}")
        }
    }
}
