package com.yungsamd17.singlenote.util

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.withLink
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

private const val ISSUE_BASE_URL = "https://github.com/yungsamd17/singlenote/issues"
private const val PROFILE_BASE_URL = "https://github.com"

// [label](url) | bare https://… | email | #1234 | @user — in this order so
// emails and URLs win over the looser @-mention match.
private val LINK_PATTERN = Regex(
    """\[([^]]+)]\((https?://[^)\s]+|mailto:[^)\s]+)\)""" +
        """|(https?://[^\s)<\]]+)""" +
        """|([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,})""" +
        """|(?<!\S)#(\d{2,6})\b""" +
        """|(?<!\S)@([A-Za-z0-9][A-Za-z0-9-]*)"""
)

/**
 * Inline links rendered accent-colored and underlined, shown without the
 * https:// prefix. Clicks open via the ambient UriHandler (Text handles
 * LinkAnnotation automatically): web links in the browser, mailto: in mail.
 */
@Composable
fun LinkedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color = Color.Unspecified,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, linkColor) { linkified(text, linkColor) }
    Text(text = annotated, modifier = modifier, style = style, color = color)
}

/**
 * Minimal block markdown for release notes and legal text: "# " / "## "
 * headings, "- " bullets, blank-line spacing — everything else is a
 * [LinkedText] paragraph so inline links keep working inside blocks.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    bodyStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    bodyColor: Color = Color.Unspecified,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        markdown.lines().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.startsWith("## ") -> Text(
                    text = line.removePrefix("## ").trim(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                line.startsWith("# ") -> Text(
                    text = line.removePrefix("# ").trim(),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                line.startsWith("- ") || line.startsWith("* ") -> Row {
                    Text(text = "•  ", style = bodyStyle, color = bodyColor)
                    LinkedText(
                        text = line.drop(2).trim(),
                        style = bodyStyle,
                        color = bodyColor,
                        modifier = Modifier.weight(1f)
                    )
                }
                line.isBlank() -> Spacer(modifier = Modifier.height(4.dp))
                else -> LinkedText(text = line.trim(), style = bodyStyle, color = bodyColor)
            }
        }
    }
}

private fun linkified(source: String, linkColor: Color): AnnotatedString {
    val link = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
    return AnnotatedString.Builder().apply {
        var pos = 0
        for (match in LINK_PATTERN.findAll(source)) {
            if (match.range.first > pos) append(source.substring(pos, match.range.first))
            val mdLabel = match.groups[1]?.value
            val mdUrl = match.groups[2]?.value
            val url = match.groups[3]?.value
            val email = match.groups[4]?.value
            val issue = match.groups[5]?.value
            val mention = match.groups[6]?.value
            when {
                mdLabel != null -> withLink(LinkAnnotation.Url(mdUrl!!)) {
                    withStyle(link) { append(mdLabel) }
                }
                url != null -> {
                    val clean = url.trimEnd('.', ',', ';', ':', '!', '?', ')')
                    withLink(LinkAnnotation.Url(clean)) {
                        withStyle(link) {
                            append(clean.removePrefix("https://").removePrefix("http://"))
                        }
                    }
                    append(url.substring(clean.length))
                }
                email != null -> withLink(LinkAnnotation.Url("mailto:$email")) {
                    withStyle(link) { append(email) }
                }
                issue != null -> withLink(LinkAnnotation.Url("$ISSUE_BASE_URL/$issue")) {
                    withStyle(link) { append("#$issue") }
                }
                mention != null -> withLink(LinkAnnotation.Url("$PROFILE_BASE_URL/$mention")) {
                    withStyle(link) { append("@$mention") }
                }
            }
            pos = match.range.last + 1
        }
        if (pos < source.length) append(source.substring(pos))
    }.toAnnotatedString()
}
