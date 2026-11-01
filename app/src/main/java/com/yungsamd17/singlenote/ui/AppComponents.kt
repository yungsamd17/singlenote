package com.yungsamd17.singlenote.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/** Matches the rounded ends of the editor's 56 dp action buttons. */
internal val NoteCardHorizontalPadding = 16.dp
internal val NoteCardTopPadding = 8.dp

internal val AppCardShape = RoundedCornerShape(28.dp)

@Composable
internal fun AppSnackbar(data: SnackbarData) {
    val colors = MaterialTheme.colorScheme
    val actionLabel = data.visuals.actionLabel
    androidx.compose.material3.Surface(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        shape = AppCardShape,
        color = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.heightIn(min = 56.dp).padding(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(data.visuals.message, modifier = Modifier.weight(1f).padding(start = 12.dp, end = 12.dp))
            if (actionLabel != null) {
                FilledTonalButton(
                    onClick = data::performAction,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = lerp(colors.surfaceContainerHigh, colors.onSurface, 0.12f),
                        contentColor = colors.onSurface,
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                ) { Text(actionLabel) }
            }
        }
    }
}
