package com.yungsamd17.singlenote.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.yungsamd17.singlenote.R
import kotlinx.coroutines.launch

/**
 * The shared confirmation flow for adding a widget from inside the app.
 *
 * The returned callback opens a dialog instead of pinning straight away. If a
 * widget is already on the home screen, the dialog asks to remove it first, so
 * the user can re-add it with the current remembered style.
 */
@Composable
internal fun rememberAddWidgetRequest(snackbarHostState: SnackbarHostState): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        val existingIds = appWidgetIds(context)
        val hasWidget = existingIds.isNotEmpty()
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.widget_add_home)) },
            text = {
                Text(
                    stringResource(
                        if (hasWidget) R.string.widget_readd_desc else R.string.widget_add_home_desc,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (hasWidget) {
                            // Only the launcher can remove a widget from the
                            // home screen. Clear its saved look so the widget
                            // added next starts from the remembered style.
                            existingIds.forEach { WidgetStyleStore.remove(context, it) }
                        }
                        requestAddSinglenoteWidget(context)
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.widget_added))
                        }
                        showDialog = false
                    },
                ) {
                    Text(
                        stringResource(if (hasWidget) R.string.widget_readd else R.string.widget_config_add),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(
                        stringResource(R.string.cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }

    return { showDialog = true }
}

private fun appWidgetIds(context: Context): IntArray =
    try {
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, SinglenoteWidgetReceiver::class.java))
    } catch (_: Exception) {
        IntArray(0)
    }
