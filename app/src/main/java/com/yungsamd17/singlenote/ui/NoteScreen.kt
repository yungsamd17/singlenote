package com.yungsamd17.singlenote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yungsamd17.singlenote.R
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun noShadowElevation() = FloatingActionButtonDefaults.elevation(
    defaultElevation = 0.dp,
    pressedElevation = 0.dp,
    focusedElevation = 0.dp,
    hoveredElevation = 0.dp
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteScreen(
    viewModel: NoteViewModel,
    onOpenArchive: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val text by viewModel.text.collectAsStateWithLifecycle()
    val pinned by viewModel.pinned.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val fontFamilyKey by viewModel.fontFamily.collectAsStateWithLifecycle()
    val textSizeKey by viewModel.textSize.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

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
    var showDeleteDialog by remember { mutableStateOf(false) }
    val hasContent = text.isNotBlank()

    // Editing starts the moment the note field gains focus (a tap puts the
    // cursor exactly where it landed) and ends when Done clears focus.
    val fieldInteraction = remember { MutableInteractionSource() }
    val isEditing by fieldInteraction.collectIsFocusedAsState()

    val noteFontFamily = when (fontFamilyKey) {
        "mono" -> FontFamily.Monospace
        "serif" -> FontFamily.Serif
        else -> FontFamily.SansSerif
    }
    val noteFontSize = when (textSizeKey) {
        "small" -> 18.sp
        "large" -> 28.sp
        else -> 22.sp
    }
    // Button labels stay at the default size for "small" and grow with the setting.
    val buttonFontSize = when (textSizeKey) {
        "small" -> 14.sp
        "large" -> 20.sp
        else -> 16.sp
    }

    // The editor field below owns the follow-scroll state; the top padding
    // offset lives with it (see NoteEditorField).

    fun finishEditing() {
        keyboard?.hide()
        focusManager.clearFocus(force = true)
        viewModel.flushSave()
    }

    // IME visibility straight from the Compose insets: no window relayout
    // happens under adjustNothing, so there is no layout pass to observe —
    // a global-layout listener would go silent and miss the close.
    val isKeyboardOpen = WindowInsets.isImeVisible
    var keyboardWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(isKeyboardOpen) {
        if (isKeyboardOpen) {
            keyboardWasOpen = true
        } else if (keyboardWasOpen) {
            keyboardWasOpen = false
            if (isEditing) finishEditing()
        }
    }

    // Guaranteed way out: with the keyboard already hidden, back ends
    // editing (with it open, the IME consumes the press instead).
    BackHandler(enabled = isEditing) {
        finishEditing()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
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
                    Box {
                        TooltipIconButton(
                            tooltip = stringResource(R.string.more_options),
                            onClick = {
                                // Close editing first so the keyboard glides
                                // down and the bar fades before the menu pops
                                // in, instead of everything snapping at once.
                                if (isEditing) finishEditing()
                                menuOpen = true
                            }
                        ) {
                            Icon(
                                Icons.Outlined.MoreVert,
                                contentDescription = stringResource(R.string.more_options)
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                            shape = RoundedCornerShape(28.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            shadowElevation = 8.dp,
                            tonalElevation = 0.dp,
                            modifier = Modifier.widthIn(min = 220.dp)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.menu_share),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Outlined.Share,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                modifier = Modifier.heightIn(min = 56.dp),
                                enabled = hasContent,
                                onClick = {
                                    menuOpen = false
                                    shareNote(context, text)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.menu_copy),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Outlined.ContentCopy,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                modifier = Modifier.heightIn(min = 56.dp),
                                enabled = hasContent,
                                onClick = {
                                    menuOpen = false
                                    copyNote(context, text)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.menu_settings),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Outlined.Settings,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                modifier = Modifier.heightIn(min = 56.dp),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                NoteEditorField(
                    externalText = text,
                    onTextChange = viewModel::onTextChange,
                    interactionSource = fieldInteraction,
                    isEditing = isEditing,
                    fontFamily = noteFontFamily,
                    fontSize = noteFontSize,
                    modifier = Modifier.fillMaxSize()
                )
            }

            AnimatedContent(
                targetState = isEditing,
                label = "bottomBar",
                transitionSpec = {
                    fadeIn(tween(150)).togetherWith(fadeOut(tween(150)))
                },
                // Sole mover of this bar: the window is adjustNothing, so the
                // animated IME inset glides it above the keyboard with no snap.
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) { editing ->
                if (editing) {
                    Button(
                        onClick = ::finishEditing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                            .height(56.dp)
                    ) {
                        Text(
                            stringResource(R.string.action_done),
                            fontSize = buttonFontSize
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        FloatingActionButton(
                            onClick = { if (hasContent) viewModel.archiveCurrent() },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .alpha(if (hasContent) 1f else 0.38f),
                            shape = CircleShape,
                            elevation = noShadowElevation()
                        ) {
                            Icon(
                                Icons.Outlined.Archive,
                                contentDescription = stringResource(R.string.cd_archive)
                            )
                        }

                        if (notificationsEnabled) {
                            FixedWidthPinButton(
                                pinned = pinned,
                                onClick = ::requestPinToggle,
                                fontSize = buttonFontSize,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }

                        FloatingActionButton(
                            onClick = { if (hasContent) showDeleteDialog = true },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .alpha(if (hasContent) 1f else 0.38f),
                            shape = CircleShape,
                            elevation = noShadowElevation()
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.cd_delete)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.delete_dialog_title)) },
            text = { Text(stringResource(R.string.delete_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCurrent()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.delete_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun NoteEditorField(
    externalText: String,
    onTextChange: (String) -> Unit,
    interactionSource: MutableInteractionSource,
    isEditing: Boolean,
    fontFamily: FontFamily,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    // Local editing state: keystrokes recompose only this field, not the
    // whole screen, which keeps typing smooth on slower devices.
    val density = LocalDensity.current
    var fieldValue by remember { mutableStateOf(TextFieldValue(externalText)) }
    LaunchedEffect(externalText) {
        if (externalText != fieldValue.text) {
            fieldValue = TextFieldValue(text = externalText, selection = TextRange(externalText.length))
        }
    }

    // Keeps the typed line in view while typing, and jumps to the tapped
    // cursor when opening a long note (e.g. at its end).
    val scrollState = rememberScrollState()
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    // The text layout origin sits below the card's inner top padding, so the
    // cursor rect has to be shifted down by it to match scroll coordinates.
    val textTopPaddingPx = with(density) { 16.dp.toPx() }
    val followTopPaddingPx = with(density) { 12.dp.toPx() }
    val followBottomPaddingPx = followTopPaddingPx + 10f

    // Scroll offset needed to reveal the cursor, or null when it is visible.
    fun cursorScrollTarget(): Int? {
        val layout = textLayoutResult ?: return null
        if (viewportHeightPx <= 0) return null
        // The layout can lag one frame behind fast typing (or IME
        // completions): never query past what it actually laid out.
        val layoutEnd = layout.layoutInput.text.length
        val offset = fieldValue.selection.start.coerceIn(0, min(fieldValue.text.length, layoutEnd))
        val cursor = layout.getCursorRect(offset)
        val cursorTop = cursor.top + textTopPaddingPx
        val cursorBottom = cursor.bottom + textTopPaddingPx
        val viewTop = scrollState.value.toFloat()
        return when {
            cursorBottom > viewTop + viewportHeightPx ->
                (cursorBottom - viewportHeightPx + followBottomPaddingPx).toInt()
            cursorTop < viewTop ->
                max(0f, cursorTop - followTopPaddingPx).toInt()
            else -> null
        }
    }

    // Single driver for the follow-scroll. Resize frames (keyboard morph)
    // pin instantly per frame — the layout itself is animating, so pinning
    // tracks it with zero lag — while discrete cursor moves (typing, taps,
    // focus gain) glide. This must stay ONE effect: with two competing
    // effects on the same ScrollState, a text relayout during the morph
    // restarts the animated glide every frame, the spring stands still, and
    // the scroll visibly lands only after the keyboard finishes — the cursor
    // lags the card instead of moving with it.
    var lastViewportHeightPx by remember { mutableIntStateOf(0) }
    LaunchedEffect(fieldValue.selection, textLayoutResult, viewportHeightPx, isEditing) {
        if (!isEditing) return@LaunchedEffect
        val target = cursorScrollTarget()
        val resized = viewportHeightPx != lastViewportHeightPx
        lastViewportHeightPx = viewportHeightPx
        if (target == null) return@LaunchedEffect
        if (resized) {
            scrollState.scrollTo(target)
        } else {
            scrollState.animateScrollTo(target)
        }
    }

    BasicTextField(
        value = fieldValue,
        onValueChange = {
            fieldValue = it
            onTextChange(it.text)
        },
        onTextLayout = { textLayoutResult = it },
        interactionSource = interactionSource,
        modifier = modifier
            .onSizeChanged { viewportHeightPx = it.height }
            .verticalScroll(scrollState),
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
                if (fieldValue.text.isEmpty()) {
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

@Composable
fun FixedWidthPinButton(
    pinned: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
) {
    // Reserve the widest label width so the button never resizes when toggled.
    val widestLabel = stringResource(R.string.pin_note)
    val pinLabel = stringResource(if (pinned) R.string.pinned else R.string.pin_note)
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        elevation = noShadowElevation(),
        icon = {
            Icon(
                if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                contentDescription = stringResource(
                    if (pinned) R.string.cd_unpin else R.string.cd_pin
                )
            )
        },
        text = {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    widestLabel,
                    maxLines = 1,
                    fontSize = fontSize,
                    modifier = Modifier.alpha(0f)
                )
                Text(pinLabel, maxLines = 1, fontSize = fontSize)
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
    val tooltipState = rememberTooltipState()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = tooltipState
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled
        ) {
            content()
        }
    }
}
