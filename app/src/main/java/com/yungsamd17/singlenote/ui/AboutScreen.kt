package com.yungsamd17.singlenote.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yungsamd17.singlenote.BuildConfig
import com.yungsamd17.singlenote.R
import com.yungsamd17.singlenote.util.LinkedText

private const val GITHUB_URL = "https://github.com/yungsamd17/singlenote"
private const val ISSUES_URL = "https://github.com/yungsamd17/singlenote/issues/new/choose"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenLicense: () -> Unit,
    onTestUpdate: () -> Unit,
) {
    val context = LocalContext.current
    val email = stringResource(R.string.email_address)
    var showDiagnostics by remember { mutableStateOf(false) }
    // Hidden unlock: five quick taps on the version line open Diagnostics.
    var versionTaps by remember { mutableIntStateOf(0) }
    var lastVersionTap by remember { mutableLongStateOf(0L) }
    val scroll = rememberSettingsScrollState()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            // Centered like the note and archive toolbars, and opaque so the
            // content scrolls under the title instead of through it. The bar
            // stays put: it never collapses while the page scrolls.
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    TooltipIconButton(
                        onClick = onBack,
                        tooltip = stringResource(R.string.cd_back),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(innerPadding)
                    .onSizeChanged { scroll.viewportHeight = it.height }
                    .verticalScroll(scroll.content, enabled = scroll.overflows)
                    .wrapContentHeight(align = Alignment.Top)
                    .onSizeChanged { scroll.contentHeight = it.height }
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            SettingsGroup(
                listOf(
                    {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                // Dedicated header mark (sticky-fill, same glyph as
                                // the launcher): tinted to match the app-name text.
                                painter = painterResource(R.drawable.ic_about_logo),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(56.dp),
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                                BrandText(style = MaterialTheme.typography.titleLarge)
                                Text(
                                    text =
                                        stringResource(
                                            R.string.about_version_line,
                                            BuildConfig.VERSION_NAME,
                                            BuildConfig.VERSION_CODE,
                                        ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier =
                                        Modifier.clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) {
                                            val now = SystemClock.elapsedRealtime()
                                            versionTaps =
                                                if (now - lastVersionTap > 2_000) 1
                                                else versionTaps + 1
                                            lastVersionTap = now
                                            if (versionTaps >= 5) {
                                                versionTaps = 0
                                                showDiagnostics = true
                                            }
                                        },
                                )
                            }
                        }
                    },
                    {
                        Text(
                            text = stringResource(R.string.about_privacy_blurb),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    },
                ),
                colors = { index ->
                    if (index == 0)
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    else
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                },
            )
            SettingsSectionHeader(stringResource(R.string.settings_legal))
            SettingsGroup(
                listOf(
                    {
                        AboutRow(
                            icon = Icons.Outlined.Description,
                            title = stringResource(R.string.terms_title),
                            subtitle = stringResource(R.string.terms_subtitle),
                            onClick = onOpenTerms,
                        )
                    },
                    {
                        AboutRow(
                            icon = Icons.Outlined.PrivacyTip,
                            title = stringResource(R.string.privacy_title),
                            subtitle = stringResource(R.string.privacy_subtitle),
                            onClick = onOpenPrivacy,
                        )
                    },
                    {
                        AboutRow(
                            icon = painterResource(R.drawable.ic_license),
                            title = stringResource(R.string.about_license),
                            subtitle = stringResource(R.string.license_short),
                            onClick = onOpenLicense,
                        )
                    },
                )
            )
            SettingsSectionHeader(stringResource(R.string.settings_support))
            SettingsGroup(
                listOf(
                    {
                        AboutRow(
                            icon = Icons.Outlined.Code,
                            title = stringResource(R.string.source_title),
                            subtitle = stringResource(R.string.source_subtitle),
                            external = true,
                            onClick = { openUrl(context, GITHUB_URL) },
                        )
                    },
                    {
                        AboutRow(
                            icon = Icons.Outlined.BugReport,
                            title = stringResource(R.string.issue_title),
                            subtitle = stringResource(R.string.issue_subtitle),
                            external = true,
                            onClick = { openUrl(context, ISSUES_URL) },
                        )
                    },
                    {
                        AboutRow(
                            icon = Icons.Outlined.Email,
                            title = stringResource(R.string.email_title),
                            subtitle = email,
                            external = true,
                            onClick = { openUrl(context, "mailto:$email") },
                        )
                    },
                )
            )
        }
    }

    if (showDiagnostics) {
        DiagnosticsDialog(
            onDismiss = { showDiagnostics = false },
            onTestUpdate = onTestUpdate,
        )
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
        body = stringResource(R.string.privacy_body) +
            // Contact and the full-policy link stay the final two paragraphs.
            "\n\n" + stringResource(R.string.privacy_contact),
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
                    TooltipIconButton(
                        onClick = onBack,
                        tooltip = stringResource(R.string.cd_back),
                    ) {
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
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.license_mit_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            LinkedText(
                text = stringResource(R.string.license_full),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 4.dp)
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
            LicenseRow(R.string.license_google_flex)
            LicenseRow(R.string.license_inter)
            LicenseRow(R.string.license_roboto_slab)
            LinkedText(
                text = stringResource(R.string.license_third_party),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 4.dp)
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
                    TooltipIconButton(
                        onClick = onBack,
                        tooltip = stringResource(R.string.cd_back),
                    ) {
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
                .padding(bottom = 32.dp)
        )
    }
}

@Composable
private fun AboutRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    external: Boolean = false,
    onClick: () -> Unit,
) {
    AboutRow(
        icon = rememberVectorPainter(icon),
        title = title,
        subtitle = subtitle,
        external = external,
        onClick = onClick,
    )
}

@Composable
private fun AboutRow(
    icon: Painter,
    title: String,
    subtitle: String? = null,
    external: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f).padding(start = 20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (external)
            Icon(
                Icons.Outlined.NorthEast,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
internal fun openUrl(context: Context, url: String) {
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
