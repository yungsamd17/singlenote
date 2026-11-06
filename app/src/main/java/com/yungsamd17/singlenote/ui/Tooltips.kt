package com.yungsamd17.singlenote.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipScope
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The app's tooltip bubble.
 *
 * The stock Material 3 bubble is a 4dp-cornered chip in the body style; this
 * one rounds it to 12dp, lifts it with a soft shadow and uses the label
 * typography, so the hint reads as a quiet companion to the icon instead of a
 * black box. The colors stay on the inverse surface pair, which keeps the
 * bubble legible in both themes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TooltipScope.AppTooltip(text: String) {
    PlainTooltip(
        containerColor = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/**
 * Wraps any icon-only control in a tooltip.
 *
 * Long press shows the hint on touch (the tap still belongs to the button),
 * so the label is reachable without stealing a tap or adding a visible
 * caption to every toolbar.
 *
 * The modifier lands on the tooltip's anchor, which Material 3 nests two
 * layouts deep. A `BoxScope.align` modifier therefore never reaches the
 * parent box and the wrapped child stacks on top of its siblings: put the
 * alignment on a plain box around this wrapper instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TooltipBubble(
    tooltip: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val tooltipState = rememberTooltipState()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { AppTooltip(tooltip) },
        state = tooltipState,
        modifier = modifier,
        enableUserInput = true,
    ) {
        content()
    }
}

/** Icon button with a tooltip - the default for every icon-only action. */
@Composable
internal fun TooltipIconButton(
    tooltip: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    TooltipBubble(tooltip = tooltip) {
        IconButton(onClick = onClick, enabled = enabled) {
            content()
        }
    }
}
