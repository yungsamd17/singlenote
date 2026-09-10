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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.SolidColor
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
import kotlinx.coroutines.launch

private const val LIMIT_HINT_COOLDOWN_MS = 3000L

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
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var lastLimitHintMs by remember { mutableLongStateOf(0L) }

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
    // Fixed editor capacity: the card below is exactly as tall as these
    // lines, so input is capped at what visibly fits with nothing left to
    // scroll to. Smaller fonts fit more text, larger fonts less.
    val noteMaxLength = NoteViewModel.maxLengthForTextSize(textSizeKey)
    val noteMaxLines = NoteViewModel.maxLinesForTextSize(textSizeKey)
    val noteLineHeight = noteFontSize * 1.45f
    // Card height = line capacity plus the editor's vertical padding (16dp
    // top + 16dp bottom) plus 10dp breathing room. Fixed once per font
    // size: it never resizes with the keyboard, only the bottom bar glides
    // above it.
    val density = LocalDensity.current
    val noteCardHeight = with(density) {
        (noteLineHeight * noteMaxLines.toFloat()).toDp() + 42.dp
    }

    // The editor card below is a fixed-size area with no scrolling: a tap
    // puts the cursor exactly where it landed and the capped content
    // always fits, so no follow-scroll is needed.

    fun finishEditing() {
        keyboard?.hide()
        focusManager.clearFocus(force = true)
        viewModel.flushSave()
    }

    // Called by the editor when input hits the note limit. Over-limit
    // keystrokes are swallowed silently; this hint fires at most once per
    // cooldown so holding a key doesn't spam snackbars.
    fun notifyLimitReached() {
        val now = System.currentTimeMillis()
        if (now - lastLimitHintMs < LIMIT_HINT_COOLDOWN_MS) return
        lastLimitHintMs = now
        scope.launch {
            snackbarHostState.showSnackbar(context.getString(R.string.note_limit_reached))
        }
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    .height(noteCardHeight)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                NoteEditorField(
                    externalText = text,
                    onTextChange = viewModel::onTextChange,
                    onLimitReached = ::notifyLimitReached,
                    interactionSource = fieldInteraction,
                    fontFamily = noteFontFamily,
                    fontSize = noteFontSize,
                    lineHeight = noteLineHeight,
                    maxLength = noteMaxLength,
                    maxLines = noteMaxLines,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Takes the slack so the bar sits at the bottom when the keyboard
            // is closed, and collapses to zero when it opens — the card above
            // never changes size, only the bar glides up.
            Spacer(modifier = Modifier.weight(1f))

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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
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
    onLimitReached: () -> Unit,
    interactionSource: MutableInteractionSource,
    fontFamily: FontFamily,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    maxLength: Int,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    // Local editing state: keystrokes recompose only this field, not the
    // whole screen, which keeps typing smooth on slower devices.
    // Over-limit input is swallowed synchronously (the previous value is
    // kept untouched, so nothing flashes and the cursor never jumps) and
    // only genuinely new, fitting content is accepted. An exact
    // visual-line guard on layout remains as the backstop for edits no
    // synchronous check can judge (pastes, IME batch commits) — so the
    // note holds as many characters as visibly fit, whatever their width.
    var fieldValue by remember { mutableStateOf(TextFieldValue(externalText.take(maxLength))) }
    // Last value known to fit the line budget: overflowing edits revert here.
    var lastFitting by remember { mutableStateOf(fieldValue) }
    // True while the content came from typing rather than an external sync:
    // only then is a guard revert propagated back to the store.
    var userEdit by remember { mutableStateOf(false) }
    // Latest truthful layout, used to judge the next keystroke before it
    // renders. Only trusted while it still describes the current value.
    var lastLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var lastLayoutText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(externalText, maxLength, maxLines) {
        val capped = externalText.take(maxLength)
        if (capped != fieldValue.text) {
            val fresh = TextFieldValue(text = capped, selection = TextRange(capped.length))
            fieldValue = fresh
            lastFitting = fresh
        }
        userEdit = false
    }

    // Drop enough trailing characters to re-enter the line budget, always
    // shrinking so repeated layouts converge on a fitting prefix.
    fun shrinkToFit(text: String, lineCount: Int): String {
        if (text.isEmpty()) return text
        val avgPerLine = text.length.toFloat() / lineCount.coerceAtLeast(1)
        val target = (avgPerLine * maxLines).toInt()
            .coerceAtMost(text.length - 1)
            .coerceAtLeast(0)
        return text.take(target)
    }

    // True when the last line has no room for another character, judged
    // from the last truthful layout: its right edge is within ~1.25 average
    // character widths of the field edge.
    fun isLastLineFull(layout: TextLayoutResult): Boolean {
        val last = layout.lineCount - 1
        if (last < 0) return false
        val start = layout.getLineStart(last)
        val end = layout.getLineEnd(last, visibleEnd = true)
        if (end <= start) return false
        val left = layout.getLineLeft(last)
        val right = layout.getLineRight(last)
        val avgChar = (right - left) / (end - start).coerceAtLeast(1)
        return right + avgChar * 1.25f >= layout.size.width
    }

    // No scroll state on purpose: the capped content always fits the card,
    // so a tap puts the cursor exactly where it landed with nothing to
    // follow or reveal while typing.

    BasicTextField(
        value = fieldValue,
        onValueChange = { new ->
            // Swallow what certainly overflows before it ever renders.
            // Anything uncertain is accepted tentatively and the line guard
            // in onTextLayout refines it — that path stays rare.
            if (new.text.length > maxLength) {
                val truncated = new.text.take(maxLength)
                if (truncated == fieldValue.text) {
                    // Pure overtype at the cap: keep the previous value
                    // instance untouched — no flash, no cursor jump — and
                    // hint that the limit is reached.
                    onLimitReached()
                    return@BasicTextField
                }
                // A longer overage (e.g. a paste): keep the fitting prefix.
                fieldValue = TextFieldValue(text = truncated, selection = TextRange(truncated.length))
                userEdit = true
                onTextChange(truncated)
                onLimitReached()
                return@BasicTextField
            }
            val layout = lastLayout
            if (layout != null && lastLayoutText == fieldValue.text &&
                fieldValue.composition == null && layout.lineCount >= maxLines
            ) {
                val addedBreaks = new.text.count { it == '\n' } -
                    fieldValue.text.count { it == '\n' }
                val appendedOne = new.text.length == fieldValue.text.length + 1 &&
                    new.text.startsWith(fieldValue.text)
                // A new hard break past a full budget always overflows, and a
                // single appended character overflows when the last line is
                // already full. Both are swallowed silently with a hint.
                if (addedBreaks > 0 || (appendedOne && isLastLineFull(layout))) {
                    onLimitReached()
                    return@BasicTextField
                }
            }
            fieldValue = new
            userEdit = true
            onTextChange(new.text)
        },
        // No maxLines cap on purpose: the field must report its true line
        // count so the guard below sees overflow. The fixed card viewport
        // can only ever show maxLines lines, so fitting content leaves the
        // internal cursor-follow scroll with nowhere to go.
        onTextLayout = { layout ->
            // Never validate an active IME composition: that would destroy
            // it. The commit ending it comes back through onValueChange and
            // re-validates anyway.
            if (fieldValue.composition != null) return@BasicTextField
            // Snapshot every genuine layout: the next keystroke's pre-check
            // must judge against current metrics (a text/line-count gate
            // here would go stale across font-size changes and could wedge
            // swallowing on. A snapshot write only recomposes; without
            // changed layout inputs nothing relayouts, so this terminates.)
            lastLayout = layout
            lastLayoutText = fieldValue.text
            if (layout.lineCount <= maxLines) {
                lastFitting = fieldValue
                userEdit = false
            } else if (userEdit) {
                // Backstop for edits no synchronous check could judge
                // (pastes, IME batch commits). Single keystrokes revert
                // exactly; bulk input keeps its fitting prefix. Either way
                // the limit was hit, so hint it.
                userEdit = false
                val overBy = fieldValue.text.length - lastFitting.text.length
                val reverted = if (overBy in 1..2) {
                    lastFitting.text
                } else {
                    shrinkToFit(fieldValue.text, layout.lineCount)
                }
                fieldValue = TextFieldValue(text = reverted, selection = TextRange(reverted.length))
                onTextChange(reverted)
                onLimitReached()
            } else {
                val shrunk = shrinkToFit(fieldValue.text, layout.lineCount)
                if (shrunk != fieldValue.text) {
                    fieldValue = TextFieldValue(text = shrunk, selection = TextRange(shrunk.length))
                    onTextChange(shrunk)
                }
            }
        },
        interactionSource = interactionSource,
        modifier = modifier,
        textStyle = TextStyle(
            fontFamily = fontFamily,
            fontSize = fontSize,
            lineHeight = lineHeight,
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
                            lineHeight = lineHeight
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
