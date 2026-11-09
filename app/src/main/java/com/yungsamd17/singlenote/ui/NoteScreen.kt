package com.yungsamd17.singlenote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.view.ViewTreeObserver
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LIMIT_HINT_COOLDOWN_MS = 3000L

// Closed-state settle before a system-hide exits editing: transient
// isImeVisible edges last a frame or two, a real landing settles for
// good, so this margin keeps reopens alive without a visible lag.
private const val LANDING_SETTLE_MS = 75L

// The character counter only shows up once the note is long enough to
// need it - half of the smallest font budget.
private const val LIMIT_INDICATOR_THRESHOLD = 500

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
    widgetAction: String? = null,
    onWidgetActionHandled: () -> Unit = {},
    onOpenArchive: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val text by viewModel.text.collectAsStateWithLifecycle()
    val pinned by viewModel.pinned.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val fontFamilyKey by viewModel.fontFamily.collectAsStateWithLifecycle()
    val textSizeKey by viewModel.textSize.collectAsStateWithLifecycle()
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val widgetFocusRequester = remember { FocusRequester() }
    var widgetCursorRequest by remember { mutableIntStateOf(0) }
    LaunchedEffect(widgetAction, ready) {
        val action = widgetAction ?: return@LaunchedEffect
        if (!ready) return@LaunchedEffect
        if (action == "archive") viewModel.archiveCurrent().join()
        if (action == "edit" || action == "archive") {
            widgetCursorRequest++
            androidx.compose.runtime.withFrameNanos { }
            widgetFocusRequester.requestFocus()
            keyboard?.show()
        }
        onWidgetActionHandled()
    }

    // Tapjacking defense: drop touches that land while another visible
    // window obscures the app, so one-tap Archive/Delete/Pin can't fire
    // through an overlay. One flag on the Compose host view covers every
    // clickable in this hierarchy.
    SideEffect { view.filterTouchesWhenObscured = true }

    var showNotificationSettings by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.togglePinned() else showNotificationSettings = true
    }

    fun requestPinToggle() {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        if (pinned) {
            viewModel.togglePinned()
        } else if (needsPermission) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else if (!androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            showNotificationSettings = true
        } else {
            viewModel.togglePinned()
        }
    }

    if (showNotificationSettings) {
        AlertDialog(
            onDismissRequest = { showNotificationSettings = false },
            icon = { Icon(Icons.Outlined.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.notification_settings_title)) },
            text = { Text(stringResource(R.string.notification_settings_message)) },
            confirmButton = { TextButton(onClick = {
                showNotificationSettings = false
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
            }) { Text(stringResource(R.string.notification_settings_open)) } },
            dismissButton = { TextButton(onClick = { showNotificationSettings = false }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) { Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        )
    }

    DisposableEffect(lifecycleOwner, view) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.refreshFromDatabase()
                Lifecycle.Event.ON_STOP -> viewModel.flushSave()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        // Persist before the foreground is lost (e.g. the notification shade
        // opens): a shade action like Archive must operate on the saved text,
        // not on a note whose last keystrokes are still debouncing.
        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (!hasFocus) viewModel.flushSave()
        }
        val treeObserver = view.viewTreeObserver
        treeObserver.addOnWindowFocusChangeListener(focusListener)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (treeObserver.isAlive) {
                treeObserver.removeOnWindowFocusChangeListener(focusListener)
            }
        }
    }

    var menuOpen by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val hasContent = text.isNotBlank()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val limitShake = remember { androidx.compose.animation.core.Animatable(0f) }
    var limitBlink by remember { mutableStateOf(false) }
    var lastLimitAnnounce by remember { mutableLongStateOf(0L) }
    var limitJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val pendingUndo by viewModel.pendingUndo.collectAsStateWithLifecycle()
    val undoArchiveLabel = stringResource(R.string.note_archived)
    val undoDeleteLabel = stringResource(R.string.note_deleted)
    val undoSwapLabel = stringResource(R.string.notes_swapped)
    val undoReplaceLabel = stringResource(R.string.note_replaced)
    val undoActionLabel = stringResource(R.string.action_undo)

    // Undo window: ViewModel holds the snapshot so rotation re-shows the
    // bar instead of losing the chance to restore — but only inside the
    // window: navigating away cancels the bar without resolving it, so a
    // stale snapshot must not resurrect the bar on return.
    LaunchedEffect(pendingUndo) {
        val pending = pendingUndo ?: return@LaunchedEffect
        if (System.currentTimeMillis() - pending.createdAtMs > NoteViewModel.UNDO_WINDOW_MS) {
            viewModel.consumePendingUndo()
            return@LaunchedEffect
        }
        val message = when (pending.kind) {
            NoteViewModel.UndoKind.ARCHIVE -> undoArchiveLabel
            NoteViewModel.UndoKind.DELETE -> undoDeleteLabel
            NoteViewModel.UndoKind.SWAP -> undoSwapLabel
            NoteViewModel.UndoKind.REPLACE -> undoReplaceLabel
        }
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = undoActionLabel,
            // Short on purpose: the bar is a 4s Undo window, and a swipe
            // settles exactly like the timeout (the pending op commits).
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoPending()
        } else {
            viewModel.consumePendingUndo()
        }
    }

    // Editing starts the moment the note field gains focus (a tap puts the
    // cursor exactly where it landed) and ends when Done clears focus.
    val fieldInteraction = remember { MutableInteractionSource() }
    val isEditing by fieldInteraction.collectIsFocusedAsState()

    val noteFontFamily = when (fontFamilyKey) {
        "mono" -> FontFamily.Monospace
        "serif" -> FontFamily.Serif
        else -> MaterialTheme.typography.bodyLarge.fontFamily ?: FontFamily.Default
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
    // Keep the original minimum viewport, then grow to fit laid-out content.
    // Once available screen space is exhausted, the field itself scrolls.
    val noteMaxLength = NoteViewModel.MAX_LENGTH_SMALL
    val noteMaxLines = NoteViewModel.maxLinesForTextSize(textSizeKey)
    val noteLineHeight = noteFontSize * 1.45f
    val density = LocalDensity.current
    val noteCardHeight = with(density) {
        (noteLineHeight * noteMaxLines.toFloat()).toDp() + 42.dp
    }

    val noteMeasurer = androidx.compose.ui.text.rememberTextMeasurer()

    // Keyboard already closed on every path below (landing flip,
    // hidden back-press, closed Done tap): just release focus and save.
    // Never hide() here — a hide into the IME's settle window bounces
    // Gboard back up for ~600ms. Hides are issued only by the Done tap.
    fun finishEditing() {
        focusManager.clearFocus(force = true)
        viewModel.flushSave()
    }

    // Copy always confirms: same snackbar position as the limit hint,
    // so it floats above Done/actions whether the keyboard is open or not.
    // Plain-text clip on purpose: marking it sensitive (API 33+) makes
    // Gboard mask it as a password (******) in suggestions and history.
    // Share stays a plain-text chooser to the app you pick (see the
    // Privacy Policy for both caveats, including keyboard history).
    fun copyNoteWithFeedback() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), text))
        scope.launch {
            snackbarHostState.showSnackbar(context.getString(R.string.note_copied))
        }
    }

    // Called by the editor when input hits the note limit. Over-limit
    // keystrokes are swallowed silently; this hint fires at most once per
    // cooldown so holding a key doesn't spam snackbars.
    fun notifyLimitReached() {
        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        // The haptic and the shake are invisible to a screen reader: also
        // announce the limit, throttled by the same hint cooldown so key
        // repeat cannot flood the queue.
        val announceNow = SystemClock.elapsedRealtime()
        if (announceNow - lastLimitAnnounce >= LIMIT_HINT_COOLDOWN_MS) {
            lastLimitAnnounce = announceNow
            view.announceForAccessibility(context.getString(R.string.note_limit_reached))
        }
        limitJob?.cancel()
        limitJob = scope.launch {
            limitShake.snapTo(0f)
            repeat(2) {
                limitBlink = true
                limitShake.animateTo(7f, tween(55))
                limitShake.animateTo(-7f, tween(110))
                limitBlink = false
                limitShake.animateTo(0f, tween(55))
                kotlinx.coroutines.delay(70)
            }
            limitBlink = false
        }
    }

    // Preserve the input connection until the keyboard has settled closed.
    // Clearing focus earlier can make the IME switch layouts mid-dismissal,
    // changing its height and pushing the Done bar upward for a frame.
    // A reopen cancels this pending focus clear through the effect keys.
    val isKeyboardOpen = WindowInsets.isImeVisible
    val keyboardTargetOpen = WindowInsets.imeAnimationTarget.getBottom(density) > 0
    val imeInsets = WindowInsets.ime
    val keyboardHasInsets by remember(imeInsets, density) {
        derivedStateOf { imeInsets.getBottom(density) > 0 }
    }
    val keyboardNearClosed by remember(imeInsets, density) {
        derivedStateOf { imeInsets.getBottom(density) <= with(density) { 64.dp.toPx() } }
    }
    var keyboardWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(isKeyboardOpen, keyboardTargetOpen, keyboardHasInsets, isEditing) {
        if (isKeyboardOpen || keyboardTargetOpen || keyboardHasInsets) {
            keyboardWasOpen = true
        } else if (isEditing && keyboardWasOpen) {
            delay(LANDING_SETTLE_MS)
            finishEditing()
            keyboardWasOpen = false
        } else if (!isEditing) {
            keyboardWasOpen = false
        }
    }

    // Guaranteed way out: with the keyboard already hidden, back ends
    // editing (with it open, the IME consumes the press instead).
    BackHandler(enabled = isEditing && !isKeyboardOpen && !keyboardTargetOpen && !keyboardHasInsets) {
        finishEditing()
    }

    // Hide while keeping the editor's input connection alive. The closed-state
    // observer above clears focus only after the dismissal finishes, shared
    // by Done and system back. Do not send a second hide after focus changes.
    fun requestFinishEditing() {
        viewModel.flushSave()
        if (isKeyboardOpen || keyboardTargetOpen || keyboardHasInsets) {
            keyboard?.hide()
        } else {
            finishEditing()
        }
    }

    // First frame waits for stored truth (see viewModel.ready): the whole
    // screen — toolbar included — appears together after one blank beat,
    // so nothing staggers in pieces and no spinner flashes.
    if (!ready) {
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    Scaffold(
        // The limit shake is a pure visual nudge: translating in the draw
        // phase keeps the whole screen from re-measuring on every frame.
        modifier = Modifier.graphicsLayer { translationX = limitShake.value.dp.toPx() },
        // Lifted above the 88dp bottom bar (and the keyboard via
        // imePadding) so limit hints never cover Done or the actions.
        snackbarHost = {
            SwipeableSnackbarHost(
                snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(bottom = 88.dp)
            )
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = { BrandText() },
                navigationIcon = {
                    TooltipIconButton(
                        tooltip = stringResource(R.string.cd_open_archive),
                        onClick = {
                            scope.launch {
                                viewModel.flushSaveAndAwait()
                                onOpenArchive()
                            }
                        }
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
                                if (isEditing) requestFinishEditing()
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
                                        // Disabled items dim their text
                                        // automatically, but an explicit tint
                                        // would stay bright — match it so the
                                        // icon dims with the option.
                                        tint = if (hasContent) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = 0.38f
                                            )
                                        }
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
                                        // Same disabled-dim match as Share above.
                                        tint = if (hasContent) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = 0.38f
                                            )
                                        }
                                    )
                                },
                                modifier = Modifier.heightIn(min = 56.dp),
                                enabled = hasContent,
                                onClick = {
                                    menuOpen = false
                                    copyNoteWithFeedback()
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
                },
                // M3 defaults tint navigation (onSurface) and actions
                // (onSurfaceVariant) differently — force both to onSurface
                // so archive and overflow match.
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        // Subtle entrance after the blank gate: a fast fade with a small
        // rise so the card and bar arrive together instead of popping in.
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(motionSpec(AppMotion.FAST_MS)) + slideInVertically(
                motionOffsetSpec(AppMotion.FAST_MS)
            ) { it / 16 },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val measuredNoteHeight = with(density) {
                    noteMeasurer.measure(
                        text = text,
                        style = TextStyle(fontFamily = noteFontFamily, fontSize = noteFontSize, lineHeight = noteLineHeight),
                        constraints = androidx.compose.ui.unit.Constraints(maxWidth = (maxWidth - 72.dp).roundToPx().coerceAtLeast(1))
                    ).size.height.toDp()
                }
                Card(
                    shape = AppCardShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(maxOf(noteCardHeight, measuredNoteHeight + 48.dp).coerceAtMost(maxHeight))
                        .padding(horizontal = NoteCardHorizontalPadding, vertical = NoteCardTopPadding)
                ) {
                    Box(Modifier.fillMaxSize()) {
                    NoteEditorField(
                        externalText = text,
                        cursorToEndRequest = widgetCursorRequest,
                        onTextChange = viewModel::onTextChange,
                        onLimitReached = ::notifyLimitReached,
                        interactionSource = fieldInteraction,
                        fontFamily = noteFontFamily,
                        fontSize = noteFontSize,
                        lineHeight = noteLineHeight,
                        maxLength = noteMaxLength,
                        modifier = Modifier.fillMaxSize().focusRequester(widgetFocusRequester)
                    )
                    val remaining = (noteMaxLength - text.length).coerceAtLeast(0)
                    // Warning amber that clears 4.5:1 against the surface it
                    // sits on: the stock #FFB300 washes out on light cards,
                    // the darker tone keeps the countdown readable there.
                    val warningColor =
                        if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
                            androidx.compose.ui.graphics.Color(0xFF8A5A00)
                        } else {
                            androidx.compose.ui.graphics.Color(0xFFFFB300)
                        }
                    val indicatorColor = when {
                        remaining == 0 -> MaterialTheme.colorScheme.error
                        remaining <= 20 -> warningColor
                        else -> MaterialTheme.colorScheme.primary
                    }
                    // The indicator only exists once the note is long enough
                    // to need it: it grows and fades in instead of popping
                    // over the text, and fades back out when it is not needed.
                    AnimatedVisibilityBox(
                        visible = text.length > LIMIT_INDICATOR_THRESHOLD,
                        enter = fadeIn(motionSpec(AppMotion.FAST_MS)) +
                            scaleIn(
                                motionSpec(AppMotion.FAST_MS),
                                initialScale = 0.7f,
                            ),
                        exit = fadeOut(motionSpec(AppMotion.FAST_MS)) +
                            scaleOut(
                                motionSpec(AppMotion.FAST_MS),
                                targetScale = 0.7f,
                            ),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                    ) {
                        // The blink is a color change: animating it keeps the
                        // flash from snapping between two colors.
                        val background by animateColorAsState(
                            targetValue = if (limitBlink) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                            },
                            animationSpec = motionColorSpec(AppMotion.FAST_MS),
                            label = "limitIndicatorBackground",
                        )
                        val labelColor by animateColorAsState(
                            targetValue = if (limitBlink) {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            } else {
                                indicatorColor
                            },
                            animationSpec = motionColorSpec(AppMotion.FAST_MS),
                            label = "limitIndicatorLabel",
                        )
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(background, androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                progress = { (text.length.toFloat() / noteMaxLength).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxSize(),
                                color = indicatorColor,
                                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                strokeWidth = 3.dp,
                            )
                            if (remaining <= 20) Text("$remaining", style = MaterialTheme.typography.labelSmall, color = labelColor)
                        }
                    }
                    }
                }

            }

            // Follow the platform keyboard directly. The content swap starts
            // near its landing; focus clearing waits until it settles closed.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // Begin the visual swap during the final 64dp of dismissal.
                // Focus remains intact until the separate closed-state observer
                // clears it safely; a reopen immediately keeps Done visible.
                val barDone = if (keyboardWasOpen && !keyboardTargetOpen) {
                    !keyboardNearClosed
                } else {
                    isEditing || keyboardHasInsets || keyboardTargetOpen
                }
                AnimatedContent(
                    targetState = barDone,
                    label = "bottomBar",
                    transitionSpec = {
                        // Fade-only swap: no scale, so the button itself
                        // never grows or shifts while the glide carries it
                        // — the swap dissolves in place, in the same
                        // accent-tinted container family both ways.
                        if (targetState) {
                            fadeIn(motionSpec(AppMotion.MEDIUM_MS)) togetherWith
                                fadeOut(motionSpec(AppMotion.FAST_MS))
                        } else {
                            fadeIn(motionSpec(AppMotion.SLOW_MS)) togetherWith
                                fadeOut(motionSpec(AppMotion.MEDIUM_MS))
                        }
                    },
                    // Fixed box: both bars are 56dp content + 16dp vertical
                    // padding = 88dp, so the crossfade dissolves in place with
                    // no size morph while the keyboard glides.
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                ) { done ->
                    if (done) {
                        Button(
                            onClick = ::requestFinishEditing,
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
                            // The alignment lives on a plain box, not on the
                            // tooltip: Material 3 nests the tooltip anchor
                            // two layouts deep, so an `align` modifier handed
                            // to the tooltip never reaches this parent box
                            // and both buttons would stack on the same spot.
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .alpha(if (hasContent) 1f else 0.38f),
                            ) {
                                TooltipBubble(
                                    tooltip = stringResource(R.string.cd_archive),
                                ) {
                                    FloatingActionButton(
                                        onClick = { if (hasContent) viewModel.archiveCurrent() },
                                        modifier = Modifier.semantics { if (!hasContent) disabled() },
                                        shape = CircleShape,
                                        elevation = noShadowElevation()
                                    ) {
                                        Icon(
                                            Icons.Outlined.Archive,
                                            contentDescription = stringResource(R.string.cd_archive)
                                        )
                                    }
                                }
                            }

                            if (notificationsEnabled) {
                                PinButton(
                                    pinned = pinned,
                                    onClick = ::requestPinToggle,
                                    fontSize = buttonFontSize,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .alpha(if (hasContent) 1f else 0.38f),
                            ) {
                                TooltipBubble(
                                    tooltip = stringResource(R.string.cd_delete),
                                ) {
                                    FloatingActionButton(
                                        onClick = { if (hasContent) showDeleteDialog = true },
                                        modifier = Modifier.semantics { if (!hasContent) disabled() },
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
                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun NoteEditorField(
    externalText: String,
    cursorToEndRequest: Int,
    onTextChange: (String) -> Unit,
    onLimitReached: () -> Unit,
    interactionSource: MutableInteractionSource,
    fontFamily: FontFamily,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    maxLength: Int,
    modifier: Modifier = Modifier,
) {
    // Local editing state: keystrokes recompose only this field, not the
    // whole screen, which keeps typing smooth on slower devices.
    // Over-limit input is swallowed synchronously (the previous value is
    // kept untouched, so nothing flashes and the cursor never jumps) and
    // only genuinely new, fitting content is accepted.
    // Stored content is never truncated here: a note saved under a smaller
    // font holds more than a larger font allows, and must survive relaunch
    // and font-size changes unchanged. Within the absolute cap the field
    // scrolls, so pasted content stays reachable at any font scale.
    var fieldValue by remember { mutableStateOf(TextFieldValue(externalText)) }
    val scrollState = rememberScrollState()
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    val focused by interactionSource.collectIsFocusedAsState()
    val contentPadding = with(LocalDensity.current) { 16.dp.roundToPx() }
    // The outer scroll container owns the viewport, so explicitly follow the caret
    // after text/selection changes and keyboard, font, or card-size changes.
    // Scroll position is deliberately not an effect key: manual scrolling stays put.
    LaunchedEffect(fieldValue.text, fieldValue.selection, textLayout, viewportHeight,
        scrollState.maxValue, focused) {
        val layout = textLayout ?: return@LaunchedEffect
        if (!focused || viewportHeight <= 0 || layout.layoutInput.text.text != fieldValue.text) {
            return@LaunchedEffect
        }
        val caret = layout.getCursorRect(fieldValue.selection.end.coerceIn(0, fieldValue.text.length))
        val target = caretScrollOffset(
            caretTop = kotlin.math.floor(caret.top).toInt() + contentPadding,
            caretBottom = kotlin.math.ceil(caret.bottom).toInt() + contentPadding,
            viewportHeight = viewportHeight,
            currentOffset = scrollState.value,
            maxOffset = scrollState.maxValue,
            margin = contentPadding,
        )
        if (target != scrollState.value) scrollState.scrollTo(target)
    }

    // Sync external truth (database load, restore, archive clear) whole —
    // never capped. Deliberately keyed on externalText only so a font-size
    // change never resets the field or moves the cursor. Jumps back to the
    // top so restored content starts visible.
    LaunchedEffect(externalText) {
        if (externalText != fieldValue.text) {
            val fresh = TextFieldValue(text = externalText, selection = TextRange(externalText.length))
            fieldValue = fresh
            scrollState.scrollTo(0)
        }
    }

    LaunchedEffect(cursorToEndRequest) {
        if (cursorToEndRequest > 0) {
            fieldValue = fieldValue.copy(selection = TextRange(fieldValue.text.length), composition = null)
            androidx.compose.runtime.withFrameNanos { }
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    // Accessible label: the visual hint overlay below is invisible to
    // TalkBack, so expose it as the text field's content description.
    val editorHint = stringResource(R.string.hint_write_one_thing)
    BasicTextField(
        value = fieldValue,
        onValueChange = { incoming ->
            val old = fieldValue
            val cursor = old.selection.start
            val insertedNewline = old.selection.collapsed && old.composition == null &&
                incoming.composition == null && incoming.text.length == old.text.length + 1 &&
                incoming.text == old.text.substring(0, cursor) + "\n" + old.text.substring(cursor)
            val lineStart = old.text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(-1)) + 1
            val line = old.text.substring(lineStart, cursor)
            val prefix = if (line.startsWith("- ")) "- " else if (line.startsWith("-")) "-" else ""
            val new = if (insertedNewline && prefix.isNotEmpty() && incoming.text.length + prefix.length <= maxLength) {
                incoming.copy(
                    text = incoming.text.substring(0, cursor + 1) + prefix + incoming.text.substring(cursor + 1),
                    selection = TextRange(cursor + 1 + prefix.length)
                )
            } else incoming
            // Absolute backstop only: within budget everything is accepted
            // whole — including long pastes — and the scrollable field
            // keeps it reachable. Only input past the cap is cut, always
            // with a hint, never silently.
            if (new.text.length > maxLength) {
                // Net deletions toward the budget are always accepted as-is,
                // even while still over it: stored content is adopted whole,
                // and editing back toward the cap must delete one character
                // at a time — never truncate to the cap.
                if (new.text.length < fieldValue.text.length) {
                    fieldValue = new
                    onTextChange(new.text)
                    return@BasicTextField
                }
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
                onTextChange(truncated)
                onLimitReached()
                return@BasicTextField
            }
            if (new.text.length == maxLength && fieldValue.text.length < maxLength) onLimitReached()
            fieldValue = new
            onTextChange(new.text)
        },
        onTextLayout = { textLayout = it },
        interactionSource = interactionSource,
        modifier = modifier
            .onSizeChanged { viewportHeight = it.height }
            .verticalScroll(scrollState)
            .semantics { contentDescription = editorHint },
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        // Visual hint only: the field already exposes it as
                        // its content description, so keep it out of the
                        // accessibility tree to avoid reading it twice.
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
                innerTextField()
            }
        }
    )
}

@Composable
fun PinButton(
    pinned: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
) {
    // The visible label and the icon would otherwise both land in the
    // merged FAB semantics ("Pinned, Unpin note"): describe the action once
    // and keep the decorative children out of the tree.
    val description = stringResource(if (pinned) R.string.cd_unpin else R.string.cd_pin)
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = description },
        shape = CircleShape,
        elevation = noShadowElevation(),
    ) {
        AnimatedContent(
            targetState = pinned,
            transitionSpec = {
                (fadeIn(motionSpec(AppMotion.MEDIUM_MS)) togetherWith
                    fadeOut(motionSpec(AppMotion.FAST_MS))) using
                    SizeTransform(clip = true) { _, _ -> motionSizeSpec(AppMotion.SLOW_MS) }
            },
            contentAlignment = Alignment.Center,
            label = "pinButton",
        ) { isPinned ->
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .clearAndSetSemantics {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = null
                )
                Spacer(Modifier.width(12.dp))
                Text(stringResource(if (isPinned) R.string.pinned else R.string.pin_note), maxLines = 1, fontSize = fontSize)
            }
        }
    }
}

/**
 * Visibility animation without a layout scope.
 *
 * Material 3 ships scope-specific `AnimatedVisibility` overloads, and the
 * note card sits inside two column scopes at once (the card content and the
 * screen column), which makes the scoped overload ambiguous. This wrapper has
 * no scope receiver, so it always resolves to the plain one.
 */
@Composable
private fun AnimatedVisibilityBox(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition,
    exit: ExitTransition,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = enter,
        exit = exit,
    ) {
        content()
    }
}

private fun shareNote(context: Context, text: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(sendIntent, null))
}

/**
 * Undo/feedback bars dismiss by swiping left or right instead of a
 * dedicated close button. A swipe settles exactly like the timeout does
 * (the pending op commits), while Undo stays tappable the whole time.
 */
@Composable
internal fun SwipeableSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
        snackbar = { data ->
            // Fresh swipe state per message: without the key a swiped-away
            // bar would leave the next message pre-dismissed.
            key(data) {
                val dismissState = rememberSwipeToDismissBoxState()
                val swipedAway = dismissState.currentValue != SwipeToDismissBoxValue.Settled
                LaunchedEffect(swipedAway) {
                    if (swipedAway) hostState.currentSnackbarData?.dismiss()
                }
                SwipeToDismissBox(
                    state = dismissState,
                    // Explicit directions so short swipes always clear
                    // the bar instead of snapping it back.
                    enableDismissFromStartToEnd = true,
                    enableDismissFromEndToStart = true,
                    backgroundContent = {},
                    content = {
                        AppSnackbar(data)
                    }
                )
            }
        }
    )
}
