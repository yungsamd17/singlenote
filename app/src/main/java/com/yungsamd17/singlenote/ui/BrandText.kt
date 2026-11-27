package com.yungsamd17.singlenote.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.yungsamd17.singlenote.R

private const val BOLD_PREFIX = "Single"

/**
 * App brand: the "Single" of [R.string.app_name] bold, "note" regular —
 * one word, always together on one line.
 */
@Composable
fun BrandText(
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    val name = stringResource(R.string.app_name)
    Text(
        text = buildAnnotatedString {
            if (name.startsWith(BOLD_PREFIX)) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(BOLD_PREFIX)
                }
                append(name.removePrefix(BOLD_PREFIX))
            } else {
                append(name)
            }
        },
        modifier = modifier,
        // Bundled variable fonts have no distinct bold file on Android 10/11,
        // so the brand always uses the system family, which has a real bold.
        style = style.copy(fontFamily = FontFamily.Default),
        color = color,
        maxLines = 1
    )
}
