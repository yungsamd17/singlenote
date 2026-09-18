package com.yungsamd17.singlenote.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yungsamd17.singlenote.BuildConfig
import com.yungsamd17.singlenote.R
import com.yungsamd17.singlenote.util.GithubReleases
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
    var changelog by remember { mutableStateOf<List<GithubReleases.Release>?>(null) }
    var changelogFailed by remember { mutableStateOf(false) }
    fun openChangelog() {
        showChangelog = true
        changelog = null
        changelogFailed = false
        scope.launch {
            try {
                changelog = GithubReleases.fetch()
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
                    Image(
                        // Note: the launcher adaptive icon (mipmap XML) can't
                        // load via painterResource — only vectors and rasters
                        // can — so the note vector doubles as the header art.
                        painter = painterResource(R.drawable.ic_notification),
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge
                        )
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
                icon = Icons.Outlined.Article,
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
            Text(
                text = stringResource(R.string.changelog_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            when {
                changelogFailed || changelog?.isEmpty() == true -> Text(
                    text = stringResource(R.string.changelog_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
                changelog == null -> Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                ) {
                    CircularProgressIndicator()
                }
                else -> LazyColumn(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(changelog!!, key = { it.tag }) { release ->
                        Column {
                            Text(
                                text = release.name.ifBlank { release.tag },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = release.body.ifBlank {
                                    stringResource(R.string.changelog_empty_notes)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
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
                modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
            )
            LicenseRow(R.string.license_kotlin)
            LicenseRow(R.string.license_compose)
            LicenseRow(R.string.license_room)
            LicenseRow(R.string.license_datastore)
            LicenseRow(R.string.license_glance)
            LicenseRow(R.string.license_navigation)
            LicenseRow(R.string.license_coroutines)
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
        Text(
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
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = false, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
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

private fun openUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
