package com.yungsamd17.singlenote.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yungsamd17.singlenote.R
import com.yungsamd17.singlenote.util.GithubReleases

/**
 * Launch-time prompt for a newer GitHub release. It only ever opens the
 * release page — never an APK directly (no SHA-256 pinning, see
 * [GithubReleases]).
 *
 * "Don't show again" is a small checkbox inside the body, so either action
 * (Later / Download) still works and can carry the opt-out with it. The
 * checkbox and its label are one clickable row, the standard Compose pattern.
 * Must be composed inside the app's MaterialTheme so it follows the selected
 * theme, accent, monochrome/AMOLED mode and app font.
 */
@Composable
fun UpdateDialog(
    release: GithubReleases.Release,
    onDismiss: () -> Unit,
    onDontShowAgain: () -> Unit,
) {
    val context = LocalContext.current
    var dontShowAgain by remember { mutableStateOf(false) }

    fun finish(after: () -> Unit) {
        if (dontShowAgain) onDontShowAgain()
        after()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        // Match the width of the app's other dialogs instead of the narrow
        // intrinsic width the two short action labels produce.
        modifier = Modifier.fillMaxWidth(),
        icon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
        title = { Text(stringResource(R.string.update_prompt_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.update_prompt_message, release.tag),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.clickable { dontShowAgain = !dontShowAgain },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = { dontShowAgain = it },
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.update_prompt_dont_show),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { finish { openUrl(context, release.htmlUrl) } }) {
                Text(stringResource(R.string.action_download))
            }
        },
        dismissButton = {
            TextButton(onClick = { finish {} }) {
                Text(
                    stringResource(R.string.update_prompt_later),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
