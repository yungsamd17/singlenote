package com.yungsamd17.singlenote.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the inline markup parser behind [LinkedText]: markdown links, bare
 * URLs, emails, issue references, mentions and bold text.
 */
class LinkifyTest {

    private fun linksOf(result: AnnotatedString): List<LinkAnnotation> =
        result.getLinkAnnotations(0, result.length).map { it.item }

    private fun urlOf(text: String): String? =
        (linksOf(linkify(text)).firstOrNull() as? LinkAnnotation.Url)?.url

    @Test
    fun plainTextIsUnchanged() {
        val result = linkify("just a note")

        assertEquals("just a note", result.text)
        assertTrue(linksOf(result).isEmpty())
    }

    @Test
    fun markdownLinksShowTheirLabel() {
        val result = linkify("see [the docs](https://example.com/docs)")

        assertEquals("see the docs", result.text)
        assertEquals("https://example.com/docs", urlOf("see [the docs](https://example.com/docs)"))
    }

    @Test
    fun bareUrlsLoseTheirScheme() {
        val result = linkify("visit https://example.com/page now")

        assertEquals("visit example.com/page now", result.text)
        assertEquals("https://example.com/page", urlOf("visit https://example.com/page now"))
    }

    @Test
    fun httpUrlsKeepTheirSchemeInTheLink() {
        assertEquals(
            "http://example.com",
            urlOf("visit http://example.com now"),
        )
    }

    @Test
    fun trailingPunctuationStaysOutsideTheLink() {
        val result = linkify("go to https://example.com/page.")

        assertEquals("go to example.com/page.", result.text)
        assertEquals("https://example.com/page", urlOf("go to https://example.com/page."))
    }

    @Test
    fun emailsBecomeMailtoLinks() {
        val result = linkify("write to me@example.com please")

        assertEquals("write to me@example.com please", result.text)
        assertEquals("mailto:me@example.com", urlOf("write to me@example.com please"))
    }

    @Test
    fun issueReferencesPointAtTheRepository() {
        val result = linkify("see #1234 for details")

        assertEquals("see #1234 for details", result.text)
        assertEquals(
            "https://github.com/yungsamd17/singlenote/issues/1234",
            urlOf("see #1234 for details"),
        )
    }

    @Test
    fun mentionsPointAtTheProfile() {
        val result = linkify("thanks @someone")

        assertEquals("thanks @someone", result.text)
        assertEquals("https://github.com/someone", urlOf("thanks @someone"))
    }

    @Test
    fun boldTextIsStyled() {
        val result = linkify("this is **important** here")

        assertEquals("this is important here", result.text)
        assertTrue(
            result.spanStyles.any { it.item.fontWeight == FontWeight.Bold },
        )
    }

    @Test
    fun linksInsideBoldTextStayClickable() {
        val result = linkify("**see https://example.com**")

        assertEquals("see example.com", result.text)
        assertEquals("https://example.com", urlOf("**see https://example.com**"))
        assertTrue(result.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
    }

    @Test
    fun severalLinksInOneTextAreAllFound() {
        val result = linkify("https://a.example and https://b.example")

        assertEquals("a.example and b.example", result.text)
        assertEquals(2, linksOf(result).size)
    }
}
