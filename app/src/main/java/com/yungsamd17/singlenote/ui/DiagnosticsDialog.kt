package com.yungsamd17.singlenote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yungsamd17.singlenote.R
import com.yungsamd17.singlenote.util.Diagnostics
import com.yungsamd17.singlenote.widget.refreshSinglenoteWidgets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hidden developer dialog: build identity, widget state, recorded failures,
 * the last crash and this app's own logcat tail. Reachable from About by
 * tapping the version line five times.
 *
 * Logs are only cleared when the user taps Clear; the dialog never wipes them
 * on its own, so a report can be generated around a single test.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsDialog(onDismiss: () -> Unit, onTestUpdate: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    // SAF keeps this permission-free: the user picks where the report lands.
    val saveLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val message =
                try {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(report.orEmpty().toByteArray(Charsets.UTF_8))
                    }
                    context.getString(R.string.diagnostics_saved)
                } catch (e: Exception) {
                    context.getString(R.string.diagnostics_save_failed, e.javaClass.simpleName)
                }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

    fun reload() {
        scope.launch { report = Diagnostics.report(context) }
    }

    LaunchedEffect(Unit) { reload() }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.diagnostics_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // The report sits in its own quiet surface instead of floating
                // directly on the dialog background.
                Surface(
                    shape = AppCardShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SelectionContainer {
                        Text(
                            text = report ?: stringResource(R.string.diagnostics_collecting),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .heightIn(max = 280.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = {
                            refreshing = true
                            scope.launch {
                                val error =
                                    runCatching { refreshSinglenoteWidgets(context) }
                                        .exceptionOrNull()
                                if (error != null) Diagnostics.failure("widget", error)
                                reload()
                                refreshing = false
                                Toast.makeText(
                                    context,
                                    if (error == null) {
                                        context.getString(R.string.diagnostics_widget_update_requested)
                                    } else {
                                        context.getString(R.string.diagnostics_widget_update_failed, error.javaClass.simpleName)
                                    },
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        enabled = !refreshing,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                        Text(stringResource(R.string.diagnostics_widget_update), modifier = Modifier.padding(start = 8.dp))
                    }
                    FilledTonalButton(
                        onClick = {
                            onDismiss()
                            onTestUpdate()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.BugReport, contentDescription = null)
                        Text(stringResource(R.string.diagnostics_test_update), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.diagnostics_close)) }
        },
        dismissButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TooltipIconButton(
                    tooltip = stringResource(R.string.diagnostics_clear),
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            Diagnostics.clear(context)
                            withContext(Dispatchers.Main) {
                                reload()
                                Toast.makeText(context, context.getString(R.string.diagnostics_cleared), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                ) { Icon(Icons.Outlined.Delete, contentDescription = null) }
                TooltipIconButton(
                    tooltip = stringResource(R.string.diagnostics_copy),
                    enabled = report != null,
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.diagnostics_title), report.orEmpty()))
                        Toast.makeText(context, context.getString(R.string.diagnostics_copied), Toast.LENGTH_SHORT).show()
                    },
                ) { Icon(Icons.Outlined.ContentCopy, contentDescription = null) }
                TooltipIconButton(
                    tooltip = stringResource(R.string.diagnostics_save),
                    enabled = report != null,
                    onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                        saveLauncher.launch("singlenote-diagnostics-$stamp.txt")
                    },
                ) { Icon(Icons.Outlined.SaveAlt, contentDescription = null) }
                TooltipIconButton(
                    tooltip = stringResource(R.string.diagnostics_share),
                    enabled = report != null,
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, report.orEmpty())
                        }
                        context.startActivity(Intent.createChooser(send, context.getString(R.string.diagnostics_share)))
                    },
                ) { Icon(Icons.Outlined.Share, contentDescription = null) }
            }

        },
    )
}
