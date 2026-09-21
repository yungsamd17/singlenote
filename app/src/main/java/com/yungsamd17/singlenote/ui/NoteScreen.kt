package com.yungsamd17.singlenote.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PersistableBundle
import android.view.ViewTreeObserver
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.stringResource
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

// Post-gate entrance: fast and subtle, just enough to avoid a pop-in.
private const val APPEAR_MS = 150

// Remaining keyboard slide that starts the Done-to-actions morph: the bar
// flips in the final stretch so the FAB row settles right as the keyboard
// lands, instead of lagging a full morph behind it.
private val KeyboardSwapThreshold = 64.dp

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
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current

    // Tapjacking defense: drop touches that land while another visible
    // window obscures the app, so one-tap Archive/Delete/Pin can't fire
    // through an overlay. One flag on the Compose host view covers every
    // clickable in this hierarchy.
    SideEffect { view.filterTouchesWhenObscured = true }

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
    var lastLimitHintMs by remember { mutableLongStateOf(0L) }
    val pendingUndo by viewModel.pendingUndo.collectAsStateWithLifecycle()
    val undoArchiveLabel = stringResource(R.string.note_archived)
    val undoDeleteLabel = stringResource(R.string.note_deleted)
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
    // Editor capacity: one absolute character backstop (mirrors the
    // ViewModel) so unbounded pastes can't grow the field forever. Within
    // that budget the field scrolls — input is never capped to what
    // visibly fits, so large font scales keep content reachable instead of
    // clipping it. The card height below still scales with the font size so
    // smaller fonts show more lines at once.
    val noteMaxLength = NoteViewModel.MAX_LENGTH_SMALL
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

    // The editor card below is a fixed-size viewport with a scrolling
    // field inside: a tap puts the cursor exactly where it landed and the
    // field scrolls to follow it, so overflow stays reachable at any font
    // scale instead of being clipped or truncated.

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
    // On API 33+ the clip is marked sensitive so the system hides its
    // content from the clipboard preview; Share stays a plain-text
    // chooser to the app you pick (see the Privacy Policy for both
    // caveats, including keyboard history).
    fun copyNoteWithFeedback() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("note", text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)
        scope.launch {
            snackbarHostState.showSnackbar(context.getString(R.string.note_copied))
        }
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

    // A system-hide (back/gesture) keeps focus with Done shown, then
    // exits editing once the closed state settles: isImeVisible flickers
    // mid-animation under adjustNothing, so the settle delay lets any
    // transient edge self-cancel instead of nuking a just-regained focus
    // and killing a reopen. Any real open restarts the effect and cancels
    // the pending clear. Done needs none of this — it clears up front.
    val isKeyboardOpen = WindowInsets.isImeVisible
    LaunchedEffect(isKeyboardOpen) {
        if (!isKeyboardOpen) {
            delay(LANDING_SETTLE_MS)
            if (isEditing) finishEditing()
        }
    }

    // Guaranteed way out: with the keyboard already hidden, back ends
    // editing (with it open, the IME consumes the press instead).
    BackHandler(enabled = isEditing) {
        finishEditing()
    }

    // Done tap is the only place that ever tells the IME to hide: clear
    // focus first so there is no hide-while-focused race (a Done tap
    // during the open animation settles closed instead of flashing back
    // with focus held), then hide once. Taps that land mid-close regain
    // focus normally and reopen — intentional reopens win and nothing
    // observes the landing, so the reopen can't be killed by it.
    fun requestFinishEditing() {
        viewModel.flushSave()
        focusManager.clearFocus(force = true)
        keyboard?.hide()
    }

    // First frame waits for stored truth (see viewModel.ready): the whole
    // screen — toolbar included — appears together after one blank beat,
    // so nothing staggers in pieces and no spinner flashes.
    if (!ready) {
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    Scaffold(
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
            enter = fadeIn(tween(APPEAR_MS)) + slideInVertically(
                tween(APPEAR_MS)
            ) { it / 16 },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
            Card(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(noteCardHeight)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Redundant-show filter (see below): tapping an already-open
                // keyboard re-fires show() into Gboard, which rebinds and
                // flashes its fallback number-row layout for a beat.
                FieldKeyboardFilter(
                    keyboard = keyboard,
                    isEditing = isEditing
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
                    modifier = Modifier.fillMaxSize()
                )
                }
            }

            // Takes the slack so the bar sits at the bottom when the keyboard
            // is closed, and collapses to zero when it opens — the card above
            // never changes size, only the bar glides up.
            Spacer(modifier = Modifier.weight(1f))

            // Sole glider of this bar: the window is adjustNothing, so the
            // animated IME inset moves this stable container above the
            // keyboard with no lag. The content switch inside never changes
            // size and never touches the insets, so the morph can't shift or
            // stick at the end of the keyboard slide.
            // Read low in the tree: this scope already recomposes every
            // inset frame via imePadding, so nothing above pays for it.
            val density = LocalDensity.current
            val imeRemainingPx = WindowInsets.ime.getBottom(density)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // Head start on the landing: once only a sliver of keyboard
                // remains, start morphing back so the FAB row settles right
                // as the slide ends instead of lagging a full morph behind.
                // Done stays visible through the close glide (focus is
                // already gone, the open keyboard holds it) and while
                // focused-but-settled (system-hide/back with no tap yet).
                val keyboardSubstantiallyOpen =
                    imeRemainingPx.toFloat() >= with(density) {
                        KeyboardSwapThreshold.toPx()
                    }
                val barDone = isEditing || keyboardSubstantiallyOpen
                AnimatedContent(
                    targetState = barDone,
                    label = "bottomBar",
                    transitionSpec = {
                        // Fade-only swap: no scale, so the button itself
                        // never grows or shifts while the glide carries it
                        // — the swap dissolves in place, in the same
                        // accent-tinted container family both ways.
                        fadeIn(tween(200)) togetherWith fadeOut(tween(160))
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
                            // FloatingActionButton has no enabled parameter: the
                            // dimmed look plus disabled semantics carry the
                            // state while the click guard blocks the action.
                            FloatingActionButton(
                                onClick = { if (hasContent) viewModel.archiveCurrent() },
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .alpha(if (hasContent) 1f else 0.38f)
                                    .semantics { if (!hasContent) disabled() },
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
                                    .alpha(if (hasContent) 1f else 0.38f)
                                    .semantics { if (!hasContent) disabled() },
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

    // Accessible label: the visual hint overlay below is invisible to
    // TalkBack, so expose it as the text field's content description.
    val editorHint = stringResource(R.string.hint_write_one_thing)
    BasicTextField(
        value = fieldValue,
        onValueChange = { new ->
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
            fieldValue = new
            onTextChange(new.text)
        },
        interactionSource = interactionSource,
        modifier = modifier
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

/**
 * Drops redundant keyboard-show requests: tapping an already-focused field
 * re-fires show() into Gboard, which rebinds the input session and flashes
 * its fallback number-row layout for a beat. The show is dropped only while
 * the field holds focus and the keyboard sits open and steady — closed,
 * opening, or closing states always delegate, so reopens keep working and
 * cursor placement is untouched. Isolated in its own scope so the per-frame
 * inset reads recompose nothing above.
 *
 * The provided controller is a single stable instance: swapping the object
 * mid-session restarts the IME session and kills the keyboard, so all live
 * inputs are read at call time instead of captured at creation.
 */
@Composable
private fun FieldKeyboardFilter(
    keyboard: SoftwareKeyboardController?,
    isEditing: Boolean,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val imeNowPx = WindowInsets.ime.getBottom(density)
    val lastImePx = remember { mutableIntStateOf(-1) }
    val steadyOpen = isEditing && imeNowPx > 0 &&
        lastImePx.intValue >= 0 && imeNowPx >= lastImePx.intValue
    val keyboardRef = rememberUpdatedState(keyboard)
    val dropShow = remember { mutableStateOf(false) }
    SideEffect {
        lastImePx.intValue = imeNowPx
        dropShow.value = steadyOpen
    }
    val filtered = remember {
        object : SoftwareKeyboardController {
            override fun show() {
                if (!dropShow.value) keyboardRef.value?.show()
            }

            override fun hide() {
                keyboardRef.value?.hide()
            }
        }
    }
    CompositionLocalProvider(
        LocalSoftwareKeyboardController provides filtered,
        content = content
    )
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
                    content = { Snackbar(snackbarData = data) }
                )
            }
        }
    )
}
