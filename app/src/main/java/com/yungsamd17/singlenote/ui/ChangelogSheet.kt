package com.yungsamd17.singlenote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yungsamd17.singlenote.BuildConfig
import com.yungsamd17.singlenote.R
import com.yungsamd17.singlenote.util.GithubReleases
import com.yungsamd17.singlenote.util.MarkdownText
import com.yungsamd17.singlenote.util.stripLeadingVersionHeading

/**
 * Resolves the release notes for the installed version. A dev build with no
 * matching tag falls back to the newest release. Returns null on any failure
 * or when there is nothing to show — the caller renders the error state.
 */
internal suspend fun loadInstalledChangelog(): GithubReleases.Release? =
    try {
        val releases = GithubReleases.fetch()
        val installed = BuildConfig.VERSION_NAME
        val picked =
            releases.firstOrNull {
                it.tag.equals("v$installed", ignoreCase = true) ||
                    it.tag.equals(installed, ignoreCase = true)
            } ?: releases.firstOrNull()
        val core = (picked?.tag ?: installed).trimStart('v', 'V')
        picked?.copy(
            body = stripLeadingVersionHeading(
                picked.body,
                listOf(picked.tag, core, "v$core"),
            )
        )
    } catch (_: Exception) {
        null
    }

/**
 * Changelog bottom sheet, shared by the Updates screen. [release] is null while
 * loading or after a failed fetch ([failed] distinguishes the two).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogSheet(
    release: GithubReleases.Release?,
    failed: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.changelog_version_title,
                    release?.tag?.trimStart('v', 'V') ?: BuildConfig.VERSION_NAME,
                ),
                style = MaterialTheme.typography.headlineSmall,
            )
            when {
                failed ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.changelog_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                // Blank while the notes load: no spinner flash for a fetch
                // that usually resolves in a beat.
                release == null ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    ) {}
                else ->
                    MarkdownText(
                        markdown = release.body.ifBlank { context.getString(R.string.changelog_empty_notes) },
                        bodyColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    )
            }
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(56.dp),
            ) {
                Text(stringResource(R.string.action_ok))
            }
        }
    }
}
