package com.yungsamd17.singlenote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.tooltipTrigger
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yungsamd17.singlenote.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(
    viewModel: NoteViewModel,
    onOpenArchive: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val text by viewModel.text.collectAsStateWithLifecycle()
    val pinned by viewModel.pinned.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.togglePinned()
    }

    fun requestPinToggle() {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.togglePinned()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.refreshFromDatabase()
                Lifecycle.Event.ON_STOP -> viewModel.flushSave()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                navigationIcon = {
                    TooltipIconButton(
                        tooltip = stringResource(R.string.cd_open_archive),
                        onClick = onOpenArchive
                    ) {
                        Icon(
                            Icons.Outlined.Inventory2,
                            contentDescription = stringResource(R.string.cd_open_archive)
                        )
                    }
                },
                actions = {
                    TooltipIconButton(
                        tooltip = stringResource(
                            if (pinned) R.string.cd_unpin else R.string.cd_pin
                        ),
                        onClick = ::requestPinToggle
                    ) {
                        Icon(
                            if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = stringResource(
                                if (pinned) R.string.cd_unpin else R.string.cd_pin
                            )
                        )
                    }
                    TooltipIconButton(
                        tooltip = stringResource(R.string.cd_archive),
                        enabled = text.isNotBlank(),
                        onClick = { viewModel.archiveCurrent() }
                    ) {
                        Icon(Icons.Outlined.Archive, contentDescription = stringResource(R.string.cd_archive))
                    }
                    Box {
                        TooltipIconButton(
                            tooltip = stringResource(R.string.more_options),
                            onClick = { menuOpen = true }
                        ) {
                            Icon(
                                Icons.Outlined.MoreVert,
                                contentDescription = stringResource(R.string.more_options)
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_share)) },
                                leadingIcon = { Icon(Icons.Outlined.Share, null) },
                                enabled = text.isNotBlank(),
                                onClick = {
                                    menuOpen = false
                                    shareNote(context, text)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_copy)) },
                                leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                                enabled = text.isNotBlank(),
                                onClick = {
                                    menuOpen = false
                                    copyNote(context, text)
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_settings)) },
                                leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                                onClick = {
                                    menuOpen = false
                                    onOpenSettings()
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        val editorFontFamily = when (viewModel.fontFamily.collectAsStateWithLifecycle().value) {
            "mono" -> FontFamily.Monospace
            "serif" -> FontFamily.Serif
            else -> FontFamily.SansSerif
        }
        val editorFontSize = when (viewModel.textSize.collectAsStateWithLifecycle().value) {
            "small" -> 18.sp
            "large" -> 28.sp
            else -> 22.sp
        }
        NoteEditor(
            text = text,
            onTextChange = viewModel::onTextChange,
            fontFamily = editorFontFamily,
            fontSize = editorFontSize,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}

@Composable
private fun NoteEditor(
    text: String,
    onTextChange: (String) -> Unit,
    fontFamily: FontFamily,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    val scrollState: ScrollState = rememberScrollState()
    BasicTextField(
        value = text,
        onValueChange = onTextChange,
        modifier = modifier.verticalScroll(scrollState),
        textStyle = TextStyle(
            fontFamily = fontFamily,
            fontSize = fontSize,
            lineHeight = (fontSize.value * 1.45f).sp,
            color = MaterialTheme.colorScheme.onSurface
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                contentAlignment = Alignment.TopStart
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.hint_write_one_thing),
                        style = TextStyle(
                            fontFamily = fontFamily,
                            fontSize = fontSize,
                            lineHeight = (fontSize.value * 1.45f).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                innerTextField()
            }
        }
    )
}

private fun shareNote(context: Context, text: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(sendIntent, null))
}

private fun copyNote(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("note", text))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TooltipIconButton(
    tooltip: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val tooltipState = remember { PlainTooltipState() }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = tooltipState
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.tooltipTrigger()
        ) {
            content()
        }
    }
}
