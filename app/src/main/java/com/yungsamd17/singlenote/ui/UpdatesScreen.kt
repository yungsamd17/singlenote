package com.yungsamd17.singlenote.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yungsamd17.singlenote.BuildConfig
import com.yungsamd17.singlenote.R
import com.yungsamd17.singlenote.util.GithubReleases
import kotlinx.coroutines.launch

/**
 * Everything about releases in one place: the launch-prompt toggle, a manual
 * check, and the notes for the installed version. Keeps About focused on app
 * identity and legal docs.
 */
@Composable
fun UpdatesScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val enabled by viewModel.updateChecksEnabled.collectAsStateWithLifecycle()
    var checking by remember { mutableStateOf(false) }
    var showChangelog by remember { mutableStateOf(false) }
    var changelogRelease by remember { mutableStateOf<GithubReleases.Release?>(null) }
    var changelogFailed by remember { mutableStateOf(false) }

    fun loadChangelog() {
        changelogRelease = null
        changelogFailed = false
        scope.launch {
            val release = loadInstalledChangelog()
            changelogRelease = release
            changelogFailed = release == null
        }
    }

    fun checkNow() {
        if (checking) return
        checking = true
        scope.launch {
            try {
                val latest = GithubReleases.fetch(limit = 5).firstOrNull()
                if (latest == null) {
                    snackbarHostState.showSnackbar(context.getString(R.string.update_check_failed))
                } else if (GithubReleases.isNewerTag(latest.tag, BuildConfig.VERSION_NAME)) {
                    val result =
                        snackbarHostState.showSnackbar(
                            message = context.getString(R.string.update_available, latest.tag),
                            actionLabel = context.getString(R.string.action_download),
                        )
                    if (result == SnackbarResult.ActionPerformed) {
                        // Release page, never a raw APK: no SHA-256 pinning.
                        openUrl(context, latest.htmlUrl)
                    }
                } else {
                    snackbarHostState.showSnackbar(context.getString(R.string.update_uptodate))
                }
            } catch (_: Exception) {
                snackbarHostState.showSnackbar(context.getString(R.string.update_check_failed))
            } finally {
                checking = false
            }
        }
    }

    SettingsPage(
        title = stringResource(R.string.settings_section_updates),
        onBack = onBack,
        snackbarHost = { SwipeableSnackbarHost(snackbarHostState) },
    ) {
        SettingsGroup(
            listOf(
                {
                    ToggleRow(
                        Icons.Outlined.Notifications,
                        stringResource(R.string.settings_update_checks),
                        stringResource(R.string.settings_update_checks_desc),
                        enabled,
                        viewModel::setUpdateChecksEnabled,
                    )
                },
                {
                    ValueRow(
                        Icons.Outlined.Refresh,
                        stringResource(R.string.cd_check_updates),
                        stringResource(R.string.updates_check_now_desc),
                        ::checkNow,
                        enabled = !checking,
                    )
                },
                {
                    ValueRow(
                        Icons.Outlined.History,
                        stringResource(R.string.changelog_title),
                        stringResource(R.string.changelog_subtitle),
                        { showChangelog = true; loadChangelog() },
                    )
                },
            )
        )
    }

    if (showChangelog) {
        ChangelogSheet(
            release = changelogRelease,
            failed = changelogFailed,
            onRetry = ::loadChangelog,
            onDismiss = { showChangelog = false },
        )
    }
}
