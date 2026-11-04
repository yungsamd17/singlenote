package com.yungsamd17.singlenote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.launch

/**
 * Hidden developer dialog: build identity, widget state, recorded widget failures,
 * the last crash and this app's logcat tail in one copyable block. Reachable from
 * About by tapping the version line five times, in every build type — release APKs
 * need it most, since they cannot be attached to a debugger.
 */
@Composable
fun DiagnosticsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { report = Diagnostics.report(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.diagnostics_title)) },
        text = {
            Column {
                val body = report
                if (body == null) {
                    Text(
                        text = stringResource(R.string.diagnostics_collecting),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    SelectionContainer {
                        Text(
                            text = body,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            modifier =
                                Modifier.heightIn(max = 380.dp)
                                    .verticalScroll(rememberScrollState()),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        enabled = !refreshing,
                        onClick = {
                            refreshing = true
                            scope.launch {
                                val error =
                                    runCatching { refreshSinglenoteWidgets(context) }
                                        .exceptionOrNull()
                                if (error != null) Diagnostics.failure("widget", error)
                                report = Diagnostics.report(context)
                                refreshing = false
                                Toast.makeText(
                                        context,
                                        if (error == null)
                                            context.getString(
                                                R.string.diagnostics_widget_update_requested
                                            )
                                        else
                                            context.getString(
                                                R.string.diagnostics_widget_update_failed,
                                                error.javaClass.simpleName,
                                            ),
                                        Toast.LENGTH_SHORT,
                                    )
                                    .show()
                            }
                        },
                    ) {
                        Text(stringResource(R.string.diagnostics_widget_update))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.diagnostics_close)) }
        },
        dismissButton = {
            Row {
                TextButton(
                    enabled = report != null,
                    onClick = {
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText(
                                context.getString(R.string.diagnostics_title),
                                report.orEmpty(),
                            )
                        )
                        Toast.makeText(
                                context,
                                context.getString(R.string.diagnostics_copied),
                                Toast.LENGTH_SHORT,
                            )
                            .show()
                    },
                ) {
                    Text(stringResource(R.string.diagnostics_copy))
                }
                TextButton(
                    enabled = report != null,
                    onClick = {
                        val send =
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, report.orEmpty())
                            }
                        context.startActivity(
                            Intent.createChooser(send, context.getString(R.string.diagnostics_share))
                        )
                    },
                ) {
                    Text(stringResource(R.string.diagnostics_share))
                }
            }
        },
    )
}
