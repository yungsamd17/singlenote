package com.yungsamd17.singlenote.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yungsamd17.singlenote.BuildConfig
import com.yungsamd17.singlenote.R
import com.yungsamd17.singlenote.util.GithubReleases
import com.yungsamd17.singlenote.util.LinkedText
import com.yungsamd17.singlenote.util.MarkdownText
import com.yungsamd17.singlenote.util.stripLeadingVersionHeading
import kotlinx.coroutines.launch

private const val GITHUB_URL = "https://github.com/yungsamd17/singlenote"
private const val ISSUES_URL = "https://github.com/yungsamd17/singlenote/issues/new/choose"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenLicense: () -> Unit,
) {
    val context = LocalContext.current
    val email = stringResource(R.string.email_address)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var checkingUpdate by remember { mutableStateOf(false) }
    var showChangelog by remember { mutableStateOf(false) }
    var changelogRelease by remember { mutableStateOf<GithubReleases.Release?>(null) }
    var changelogFailed by remember { mutableStateOf(false) }
    fun openChangelog() {
        showChangelog = true
        changelogRelease = null
        changelogFailed = false
        scope.launch {
            try {
                val releases = GithubReleases.fetch()
                val installed = BuildConfig.VERSION_NAME
                // Only this install's notes: match v0.3.3 or plain 0.3.3,
                // fall back to the newest release on dev builds.
                val picked = releases.firstOrNull {
                    it.tag.equals("v$installed", ignoreCase = true) ||
                        it.tag.equals(installed, ignoreCase = true)
                } ?: releases.firstOrNull()
                // The sheet title already carries the version: drop the
                // duplicate leading line from the notes themselves.
                val core = (picked?.tag ?: installed).trimStart('v', 'V')
                changelogRelease = picked?.copy(
                    body = stripLeadingVersionHeading(
                        picked.body,
                        listOf(picked.tag, core, "v$core")
                    )
                )
                if (changelogRelease == null) changelogFailed = true
            } catch (_: Exception) {
                changelogFailed = true
            }
        }
    }
    fun checkForUpdates() {
        if (checkingUpdate) return
        checkingUpdate = true
        scope.launch {
            try {
                val latest = GithubReleases.fetch(limit = 5).firstOrNull()
                if (latest == null) {
                    snackbarHostState.showSnackbar(context.getString(R.string.update_check_failed))
                } else if (GithubReleases.isNewerTag(latest.tag, BuildConfig.VERSION_NAME)) {
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.update_available, latest.tag),
                        actionLabel = context.getString(R.string.action_download)
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        openUrl(context, latest.apkUrl ?: latest.htmlUrl)
                    }
                } else {
                    snackbarHostState.showSnackbar(context.getString(R.string.update_uptodate))
                }
            } catch (_: Exception) {
                snackbarHostState.showSnackbar(context.getString(R.string.update_check_failed))
            } finally {
                checkingUpdate = false
            }
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        // Dedicated header mark (sticky-fill, same glyph as
                        // the launcher): tinted to match the app-name text.
                        painter = painterResource(R.drawable.ic_about_logo),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(56.dp)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp)
                    ) {
                        BrandText(style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = stringResource(
                                R.string.about_version_line,
                                BuildConfig.VERSION_NAME,
                                BuildConfig.VERSION_CODE
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    IconButton(
                        onClick = ::checkForUpdates,
                        enabled = !checkingUpdate
                    ) {
                        if (checkingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.cd_check_updates)
                            )
                        }
                    }
                }
            }
            Text(
                text = stringResource(R.string.about_privacy_blurb),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
            AboutRow(
                icon = Icons.Outlined.Description,
                title = stringResource(R.string.terms_title),
                onClick = onOpenTerms
            )
            AboutRow(
                icon = Icons.Outlined.PrivacyTip,
                title = stringResource(R.string.privacy_title),
                onClick = onOpenPrivacy
            )
            AboutRow(
                icon = painterResource(R.drawable.ic_license),
                title = stringResource(R.string.about_license),
                subtitle = stringResource(R.string.license_short),
                onClick = onOpenLicense
            )
            AboutRow(
                icon = Icons.Outlined.Code,
                title = stringResource(R.string.source_title),
                onClick = { openUrl(context, GITHUB_URL) }
            )
            AboutRow(
                icon = Icons.Outlined.BugReport,
                title = stringResource(R.string.issue_title),
                onClick = { openUrl(context, ISSUES_URL) }
            )
            AboutRow(
                icon = Icons.Outlined.History,
                title = stringResource(R.string.changelog_title),
                onClick = ::openChangelog
            )
            AboutRow(
                icon = Icons.Outlined.Email,
                title = stringResource(R.string.email_title),
                subtitle = email,
                onClick = { openUrl(context, "mailto:$email") }
            )
        }
    }

    if (showChangelog) {
        ModalBottomSheet(onDismissRequest = { showChangelog = false }) {
            val release = changelogRelease
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(
                        R.string.changelog_version_title,
                        release?.tag?.trimStart('v', 'V') ?: BuildConfig.VERSION_NAME
                    ),
                    style = MaterialTheme.typography.headlineSmall
                )
                when {
                    changelogFailed -> Text(
                        text = stringResource(R.string.changelog_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    release == null -> Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp)
                    ) {
                        CircularProgressIndicator()
                    }
                    else -> MarkdownText(
                        markdown = release.body.ifBlank {
                            context.getString(R.string.changelog_empty_notes)
                        },
                        bodyColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
                Button(
                    onClick = { showChangelog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(56.dp)
                ) {
                    Text(stringResource(R.string.action_ok))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsScreen(onBack: () -> Unit) {
    LegalScreen(
        title = stringResource(R.string.terms_title),
        body = stringResource(R.string.terms_body),
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    LegalScreen(
        title = stringResource(R.string.privacy_title),
        body = stringResource(R.string.privacy_body),
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.about_license)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.license_mit_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Text(
                text = stringResource(R.string.license_components_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
            LicenseRow(R.string.license_kotlin)
            LicenseRow(R.string.license_compose)
            LicenseRow(R.string.license_room)
            LicenseRow(R.string.license_datastore)
            LicenseRow(R.string.license_glance)
            LicenseRow(R.string.license_navigation)
            LicenseRow(R.string.license_coroutines)
            LicenseRow(R.string.license_symbols)
            LicenseRow(R.string.license_bootstrap)
            LinkedText(
                text = stringResource(R.string.license_third_party),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            LinkedText(
                text = stringResource(R.string.license_full),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegalScreen(
    title: String,
    body: String,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LinkedText(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun AboutRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    AboutRow(
        icon = rememberVectorPainter(icon),
        title = title,
        subtitle = subtitle,
        onClick = onClick
    )
}

@Composable
private fun AboutRow(
    icon: Painter,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = false, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.padding(start = 20.dp)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun LicenseRow(textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

// No browser or mail app installed (or no handler at all): tell the
// user instead of crashing with ActivityNotFoundException.
private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(
            context,
            context.getString(R.string.no_app_to_open_link),
            Toast.LENGTH_SHORT
        ).show()
    }
}
