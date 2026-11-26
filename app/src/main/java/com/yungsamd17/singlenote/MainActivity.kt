package com.yungsamd17.singlenote

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.Surface
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yungsamd17.singlenote.BuildConfig
import com.yungsamd17.singlenote.data.NotePreferences
import com.yungsamd17.singlenote.notify.PinNotification
import com.yungsamd17.singlenote.notify.PinnedNoteService
import com.yungsamd17.singlenote.ui.AboutScreen
import com.yungsamd17.singlenote.ui.AppMotion
import com.yungsamd17.singlenote.ui.ArchiveScreen
import com.yungsamd17.singlenote.ui.ArchiveViewModel
import com.yungsamd17.singlenote.ui.LicenseScreen
import com.yungsamd17.singlenote.ui.NoteScreen
import com.yungsamd17.singlenote.ui.NoteViewModel
import com.yungsamd17.singlenote.ui.PrivacyScreen
import com.yungsamd17.singlenote.ui.SettingsScreen
import com.yungsamd17.singlenote.ui.SettingsViewModel
import com.yungsamd17.singlenote.ui.TermsScreen
import com.yungsamd17.singlenote.ui.UpdateDialog
import com.yungsamd17.singlenote.ui.UpdatesScreen
import com.yungsamd17.singlenote.ui.accentScheme
import com.yungsamd17.singlenote.ui.motionSpec
import com.yungsamd17.singlenote.ui.motionOffsetSpec
import com.yungsamd17.singlenote.ui.toGray
import com.yungsamd17.singlenote.ui.toMonochrome
import com.yungsamd17.singlenote.util.AppLanguage
import com.yungsamd17.singlenote.util.AppShortcuts
import com.yungsamd17.singlenote.util.Diagnostics
import com.yungsamd17.singlenote.util.ActionSecurity
import com.yungsamd17.singlenote.util.GithubReleases
import com.yungsamd17.singlenote.widget.refreshSinglenoteWidgets
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    companion object {
        // Debounce (ms) before re-establishing the widget session on resume.
        private const val WIDGET_REFRESH_DEBOUNCE = 5_000L
        const val EXTRA_WIDGET_ACTION = "widget_action"
        /**
         * Quick capture from the notification shade: archive the note on
         * screen and start a new one, with the cursor in it.
         */
        const val EXTRA_QUICK_CAPTURE = "quick_capture"

        /** The pinned notification's Open action: always land on the note. */
        const val EXTRA_OPEN_FROM_NOTIFICATION = "open_from_notification"

        internal fun testRelease(context: Context) =
            GithubReleases.Release(
                tag = "v99.0.0",
                name = context.getString(R.string.update_test_name),
                body = context.getString(R.string.update_test_body),
                htmlUrl = "https://github.com/yungsamd17/singlenote/releases",
                apkUrl = null,
            )
    }
    private var widgetAction by mutableStateOf<String?>(null)
    private var quickCapture by mutableStateOf(false)
    private var updateRelease by mutableStateOf<GithubReleases.Release?>(null)
    private var openFromNotification by mutableStateOf(false)
    // Launcher shortcut route to open once the NavHost exists (cold start).
    private var pendingRoute by mutableStateOf<String?>(null)
    private var navControllerRef: NavController? = null
    private var testUpdateJob: Job? = null

    /**
     * Reads the actions our own widgets, tiles, notification and launcher
     * shortcuts can trigger. They all arrive as intents on this exported
     * activity, so the token decides which ones are ours.
     */
    private fun receiveExternalAction(intent: Intent?) {
        if (ActionSecurity.valid(this, intent?.getStringExtra(ActionSecurity.EXTRA_TOKEN))) {
            widgetAction = intent?.getStringExtra(EXTRA_WIDGET_ACTION)?.takeIf {
                it in setOf("open", "edit", "archive")
            }
            quickCapture = intent?.getBooleanExtra(EXTRA_QUICK_CAPTURE, false) == true
            when (AppShortcuts.target(intent)) {
                // Reuse quick capture: the note on screen is archived, never
                // deleted, and the new editor gets the cursor.
                AppShortcuts.TARGET_NEW_NOTE -> quickCapture = true
                AppShortcuts.TARGET_ARCHIVE -> openShortcutRoute("archive")
                AppShortcuts.TARGET_SETTINGS -> openShortcutRoute("settings")
            }
        }
        // Notification opens are navigation only: no token needed, and they
        // must reset the back stack to the editor even from recents.
        if (intent?.getBooleanExtra(EXTRA_OPEN_FROM_NOTIFICATION, false) == true) {
            // A warm start (singleTask onNewIntent) has a live NavController:
            // pop back to the note with the same transition as the system back
            // button before the next frame can show the previous screen.
            val controller = navControllerRef
            if (controller != null) {
                controller.popBackStack("note", inclusive = false)
            } else {
                // Cold start: the graph doesn't exist yet, handle it in Compose.
                openFromNotification = true
            }
        }
        intent?.removeExtra(EXTRA_WIDGET_ACTION)
        intent?.removeExtra(EXTRA_QUICK_CAPTURE)
        intent?.removeExtra(EXTRA_OPEN_FROM_NOTIFICATION)
        intent?.removeExtra(AppShortcuts.EXTRA_TARGET)
        intent?.removeExtra(ActionSecurity.EXTRA_TOKEN)
    }

    /**
     * Opens a launcher shortcut destination. A warm start navigates directly;
     * a cold start stores the route until the NavHost exists. Popping to the
     * note first makes Back from a shortcut return to the editor instead of
     * whatever page happened to be underneath.
     */
    private fun openShortcutRoute(route: String) {
        val controller = navControllerRef
        if (controller == null) {
            pendingRoute = route
            return
        }
        if (controller.currentDestination?.route == route) return
        controller.navigate(route) {
            popUpTo("note") { inclusive = false }
            launchSingleTop = true
        }
    }



    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLanguage.finishTransition(this)
        // Shortcut labels are snapshots: republish so a language switch also
        // updates what the launcher shows under the app icon.
        lifecycleScope.launch(Dispatchers.IO) { AppShortcuts.publish(this@MainActivity) }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiveExternalAction(intent)
        // Launcher shortcuts are published at runtime so their intents can
        // carry the per-install action token; republishing also refreshes the
        // labels after an app update. Off the main thread because the token
        // lives in SharedPreferences.
        lifecycleScope.launch(Dispatchers.IO) { AppShortcuts.publish(this@MainActivity) }
        // Match the launch window to the system theme before the first
        // frame. The stored theme needs a DataStore read, which must never
        // block the UI thread — so the launch background uses the system
        // value, and the async theme gate in setContent applies the stored
        // theme (and corrects the bars) as soon as it emits.
        val startDark = isSystemDark()
        lastDarkTheme = startDark
        val launchDynamic = getSharedPreferences("launch_theme", Context.MODE_PRIVATE).getBoolean("dynamic", false)
        val launchMonochrome = getSharedPreferences("launch_theme", Context.MODE_PRIVATE).getBoolean("monochrome", false)
        val launchColor = if (launchDynamic && Build.VERSION.SDK_INT >= 31) {
            val background = (if (startDark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)).background
            (if (launchMonochrome) background.toGray() else background).toArgb()
        } else ContextCompat.getColor(this, if (startDark) R.color.launch_background_dark else R.color.launch_background)
        window.setBackgroundDrawable(ColorDrawable(launchColor))
        enableEdgeToEdge()
        // Status/navigation icon colors must follow the app theme, not the
        // system: enableEdgeToEdge() defaults to the system, leaving
        // invisible icons whenever the two disagree (or after re-entry
        // from recents, which re-applies the system styling).
        applyBarAppearance(startDark)
        val repository = (application as SinglenoteApplication).repository

        // One on-demand GitHub read per launch, only while the user has the
        // update prompt enabled. Failures are silent — the manual check in
        // About still covers offline / rate-limited cases.
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (!NotePreferences.get(applicationContext).updateChecksEnabled.first()) {
                    return@launch
                }
                val latest = GithubReleases.fetch(limit = 5).firstOrNull() ?: return@launch
                if (GithubReleases.isNewerTag(latest.tag, BuildConfig.VERSION_NAME)) {
                    withContext(Dispatchers.Main) { updateRelease = latest }
                }
            } catch (_: Exception) {
                // Offline or GitHub unavailable: nothing to prompt about.
            }
        }

        setContent {
            val release = updateRelease
            val themeMode by produceState<String?>(
                initialValue = null,
                producer = {
                    NotePreferences.get(applicationContext).themeMode.collect { value = it }
                }
            )
            val accent by produceState<String?>(
                initialValue = null,
                producer = {
                    NotePreferences.get(applicationContext).accentColor.collect { value = it }
                }
            )
            val preferences = NotePreferences.get(applicationContext)
            val dynamic by produceState<Boolean?>(null) { preferences.dynamicColors.collect { value = it } }
            val amoled by produceState<Boolean?>(null) { preferences.amoled.collect { value = it } }
            val monochrome by produceState<Boolean?>(null) { preferences.monochrome.collect { value = it } }
            val appFont by produceState<String?>(null) { preferences.appFont.collect { value = it } }
            val appIcon by produceState<String?>(null) { preferences.appIcon.collect { value = it } }
            if (dynamic == null || amoled == null || monochrome == null || appFont == null || appIcon == null) return@setContent
            // Paint nothing until the stored theme arrives: falling back to
            // the system theme/default accent first would flash a wrong-theme
            // frame over the matched launch window (see onCreate) on every
            // cold start where the two disagree.
            if (themeMode == null || accent == null) return@setContent
            // Locals: delegated properties keep their nullable type (and
            // can't be smart-cast), so re-assert non-null here.
            val theme: String = themeMode ?: return@setContent
            val accentKey: String = accent ?: return@setContent
            var iconInitialized by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            LaunchedEffect(appIcon, accentKey) {
                val changed = com.yungsamd17.singlenote.util.AppIcon.apply(this@MainActivity, appIcon ?: "default", accentKey)
                if (iconInitialized && changed) finishAndRemoveTask()
                iconInitialized = true
            }
            val darkTheme = when (theme) {
                NotePreferences.THEME_DARK -> true
                NotePreferences.THEME_LIGHT -> false
                else -> isSystemInDarkTheme()
            }
            // Re-apply on every in-app theme change (e.g. the Settings
            // switch): the style set in onCreate/onResume would go stale.
            // Cached so onResume can re-apply synchronously without disk I/O.
            SideEffect {
                lastDarkTheme = darkTheme
                applyBarAppearance(darkTheme)
            }
            val baseScheme = if (dynamic == true && Build.VERSION.SDK_INT >= 31) {
                if (darkTheme) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
            } else accentScheme(accentKey, darkTheme)
            // Monochrome replaces every color role with a shade of gray, so
            // it wins over both the accent and the wallpaper colors.
            val colorScheme = if (monochrome == true) baseScheme.toMonochrome() else baseScheme
            MaterialTheme(
                typography = com.yungsamd17.singlenote.ui.appTypography(appFont == "flex"),
                colorScheme = if (amoled == true && darkTheme) colorScheme.copy(
                    background = androidx.compose.ui.graphics.Color.Black,
                    surface = androidx.compose.ui.graphics.Color.Black
                ) else colorScheme
            ) {
                // Dialogs must be composed inside MaterialTheme so they pick
                // up the stored theme, accent, monochrome/AMOLED mode and font.
                if (release != null) {
                    UpdateDialog(
                        release = release,
                        onDismiss = { updateRelease = null },
                        onDontShowAgain = {
                            updateRelease = null
                            lifecycleScope.launch(Dispatchers.IO) {
                                NotePreferences.get(applicationContext).setUpdateChecksEnabled(false)
                            }
                        },
                    )
                }
                val background = MaterialTheme.colorScheme.background.toArgb()
                SideEffect {
                    window.setBackgroundDrawable(ColorDrawable(background))
                    window.decorView.setBackgroundColor(background)
                }
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    SideEffect { navControllerRef = navController }
                    // A launcher shortcut that started the app before the
                    // NavHost existed: open its destination now.
                    LaunchedEffect(pendingRoute) {
                        val route = pendingRoute ?: return@LaunchedEffect
                        navController.navigate(route) {
                            popUpTo("note") { inclusive = false }
                            launchSingleTop = true
                        }
                        pendingRoute = null
                    }
                    // Rapid taps on a settings row must not stack duplicate
                    // destinations: singleTop reuses the existing top entry.
                    fun navigateOnce(route: String) {
                        navController.navigate(route) { launchSingleTop = true }
                    }
                    LaunchedEffect(widgetAction, quickCapture) {
                        if ((widgetAction != null || quickCapture) && navController.currentDestination?.route != "note") {
                            navController.navigate("note") { popUpTo("note") { inclusive = false }; launchSingleTop = true }
                        }
                    }
                    // Activity-scoped on purpose: entry-scoped ViewModels die
                    // on pop, cancelling a pending delayed undo write — a
                    // note deleted in the archive would resurrect when
                    // backing out and returning. Surviving in-app nav keeps
                    // the commit (and the Undo window) alive.
                    val noteViewModel: NoteViewModel =
                        viewModel(factory = NoteViewModel.factory(repository))
                    val archiveViewModel: ArchiveViewModel =
                        viewModel(factory = ArchiveViewModel.factory(repository))
                    val settingsViewModel: SettingsViewModel =
                        viewModel(factory = SettingsViewModel.factory(repository))
                    LaunchedEffect(openFromNotification) {
                        if (openFromNotification) {
                            // Pop rather than navigate: this plays the same
                            // pop transition as pressing system back and drops
                            // the stale screens from the back stack.
                            navController.popBackStack("note", inclusive = false)
                            openFromNotification = false
                        }
                    }
                    NavHost(
                        navController = navController,
                        startDestination = "note",
                        // Slide + fade page transitions, kept short on purpose:
                        // sliding full screens recomposes every frame, so a
                        // long slide janks on slower devices.
                        enterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = motionOffsetSpec(AppMotion.MEDIUM_MS)
                            ) + fadeIn(animationSpec = motionSpec(AppMotion.FAST_MS))
                        },
                        exitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { -it },
                                animationSpec = motionOffsetSpec(AppMotion.MEDIUM_MS)
                            ) + fadeOut(animationSpec = motionSpec(AppMotion.FAST_MS))
                        },
                        popEnterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { -it },
                                animationSpec = motionOffsetSpec(AppMotion.MEDIUM_MS)
                            ) + fadeIn(animationSpec = motionSpec(AppMotion.FAST_MS))
                        },
                        popExitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = motionOffsetSpec(AppMotion.MEDIUM_MS)
                            ) + fadeOut(animationSpec = motionSpec(AppMotion.FAST_MS))
                        }
                    ) {
                        composable(
                            "note",
                            // The archive is the one screen that comes from
                            // the left, so the note slides to the right
                            // while it opens. Every other page keeps the
                            // default leftwards exit.
                            exitTransition = {
                                if (targetState.destination.route == "archive") {
                                    slideOutHorizontally(
                                        targetOffsetX = { it },
                                        animationSpec = motionOffsetSpec(AppMotion.MEDIUM_MS)
                                    ) + fadeOut(animationSpec = motionSpec(AppMotion.FAST_MS))
                                } else {
                                    slideOutHorizontally(
                                        targetOffsetX = { -it },
                                        animationSpec = motionOffsetSpec(AppMotion.MEDIUM_MS)
                                    ) + fadeOut(animationSpec = motionSpec(AppMotion.FAST_MS))
                                }
                            },
                            // Closing the archive brings the note back from
                            // the right; popping back from any other page
                            // keeps the default leftwards entrance.
                            popEnterTransition = {
                                if (initialState.destination.route == "archive") {
                                    slideInHorizontally(
                                        initialOffsetX = { it },
                                        animationSpec = motionOffsetSpec(AppMotion.MEDIUM_MS)
                                    ) + fadeIn(animationSpec = motionSpec(AppMotion.FAST_MS))
                                } else {
                                    slideInHorizontally(
                                        initialOffsetX = { -it },
                                        animationSpec = motionOffsetSpec(AppMotion.MEDIUM_MS)
                                    ) + fadeIn(animationSpec = motionSpec(AppMotion.FAST_MS))
                                }
                            },
                        ) {
                            NoteScreen(
                                viewModel = noteViewModel,
                                widgetAction = widgetAction,
                                onWidgetActionHandled = { widgetAction = null },
                                quickCapture = quickCapture,
                                onQuickCaptureHandled = { quickCapture = false },
                                onOpenArchive = { navigateOnce("archive") },
                                onOpenSettings = { navigateOnce("settings") }
                            )
                        }
                        composable(
                            "archive",
                            // The archive slides in from the left and leaves
                            // to the left again, mirroring the note screen.
                            enterTransition = {
                                slideInHorizontally(
                                    initialOffsetX = { -it },
                                    animationSpec = motionOffsetSpec(AppMotion.MEDIUM_MS)
                                ) + fadeIn(animationSpec = motionSpec(AppMotion.FAST_MS))
                            },
                            popExitTransition = {
                                slideOutHorizontally(
                                    targetOffsetX = { -it },
                                    animationSpec = motionOffsetSpec(AppMotion.MEDIUM_MS)
                                ) + fadeOut(animationSpec = motionSpec(AppMotion.FAST_MS))
                            },
                        ) {
                            ArchiveScreen(
                                viewModel = archiveViewModel,
                                onBack = { navController.popBackStack() },
                                onRestored = {
                                    noteViewModel.onArchiveRestored(archiveViewModel.takeRestoreUndo())
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                onBack = { navController.popBackStack() },
                                onOpenScreen = { navigateOnce("settings/$it") }
                            )
                        }
                        listOf("appearance", "typography", "language", "notifications").forEach { screen ->
                            composable("settings/$screen") {
                                SettingsScreen(settingsViewModel, { navController.popBackStack() },
                                    { navigateOnce("settings/$it") }, screen)
                            }
                        }
                        composable("settings/updates") {
                            UpdatesScreen(
                                viewModel = settingsViewModel,
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable("settings/about") {
                            AboutScreen(
                                onBack = { navController.popBackStack() },
                                onOpenTerms = { navigateOnce("terms") },
                                onOpenPrivacy = { navigateOnce("privacy") },
                                onOpenLicense = { navigateOnce("license") },
                                onTestUpdate = {
                                    // Stay in the app: go to the note screen,
                                    // then show the simulated prompt 3s later.
                                    navController.navigate("note") {
                                        popUpTo("note") { inclusive = true }
                                        launchSingleTop = true
                                    }
                                    testUpdateJob?.cancel()
                                    testUpdateJob =
                                        lifecycleScope.launch {
                                            delay(3_000L)
                                            updateRelease = testRelease(applicationContext)
                                        }
                                }
                            )
                        }
                        composable("terms") {
                            TermsScreen(onBack = { navController.popBackStack() })
                        }
                        composable("privacy") {
                            PrivacyScreen(onBack = { navController.popBackStack() })
                        }
                        composable("license") {
                            LicenseScreen(onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }

    // Last theme applied from stored prefs (or the system fallback before
    // prefs emit). Lets onResume re-apply the bars without disk I/O.
    private var lastDarkTheme: Boolean? = null

    // Debounced so fast resume churn (rotation, re-entry from recents) doesn't
    // schedule a widget update per blip.
    private var widgetRefreshJob: Job? = null

    override fun onResume() {
        super.onResume()
        // HyperOS can resume the activity while the keyguard is still up, so
        // verify with a synchronous check before revealing anything.
        val locked = (getSystemService(KeyguardManager::class.java)?.isKeyguardLocked ?: false) ||
            !(getSystemService(PowerManager::class.java)?.isInteractive ?: true)
        PinNotification.setAppInForeground(!locked)
        if (locked) PinNotification.markConcealed(applicationContext)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                PinNotification.refresh(applicationContext, forceConcealed = locked)
                val prefs = NotePreferences.get(applicationContext)
                if (prefs.pinned.first() && prefs.notificationsEnabled.first()) {
                    PinnedNoteService.start(applicationContext)
                }
                Diagnostics.event("lifecycle", "onResume locked=$locked")
            } catch (e: Exception) {
                Diagnostics.failure("pin", e)
            }
        }
        // Glance renders each widget in a WorkManager worker that must find
        // the in-memory session of the process that enqueued the update. When
        // the OS reclaims that process first (aggressive background reaping,
        // e.g. MIUI), the worker sees no session and its retries no-op,
        // leaving the widget stuck on the loading layout forever. Re-establish
        // the session from this live foreground process so a stuck widget
        // heals itself on the next app open (or widget tap, which lands here).
        widgetRefreshJob?.cancel()
        widgetRefreshJob = lifecycleScope.launch(Dispatchers.IO) {
            delay(WIDGET_REFRESH_DEBOUNCE)
            try {
                refreshSinglenoteWidgets(applicationContext)
            } catch (e: Exception) {
                Diagnostics.failure("widget", e)
            }
        }
        // Re-apply on return (e.g. from recents): the system styling, not
        // the app theme, may have driven the bars while away. Synchronous
        // from the cache — never a blocking store read on the main thread.
        lastDarkTheme?.let { applyBarAppearance(it) }
        // Then converge on stored truth off the main thread, in case prefs
        // changed while away.
        lifecycleScope.launch {
            val themeMode = try {
                NotePreferences.get(applicationContext).themeMode.first()
            } catch (_: Exception) {
                NotePreferences.THEME_SYSTEM
            }
            val dark = when (themeMode) {
                NotePreferences.THEME_DARK -> true
                NotePreferences.THEME_LIGHT -> false
                else -> isSystemDark()
            }
            lastDarkTheme = dark
            applyBarAppearance(dark)
        }
    }

    /**
     * Keeps the pinned notification in sync when the activity leaves the
     * foreground. Both callbacks conceal only when the screen is really off or
     * the keyguard is up, so closing the app or pulling down the shade while
     * unlocked leaves the note visible. [PinnedNoteService] keeps the process
     * (and its live receiver) alive to handle the later lock/unlock events.
     */
    private fun syncNotificationOnBackground(where: String, alwaysConceal: Boolean) {
        val keyguard = getSystemService(KeyguardManager::class.java)
            ?.isKeyguardLocked ?: false
        val interactive = getSystemService(PowerManager::class.java)
            ?.isInteractive ?: true
        val shouldConceal = alwaysConceal || keyguard || !interactive
        Diagnostics.event("lifecycle", "$where keyguard=$keyguard interactive=$interactive")
        // Do this before the lock screen renders: the async refresh below has
        // to read prefs and Room, which is slow enough to show the note for a
        // fraction of a second otherwise.
        PinNotification.setAppInForeground(false)
        if (shouldConceal) PinNotification.markConcealed(applicationContext)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (shouldConceal) {
                    PinNotification.refresh(applicationContext, forceConcealed = true)
                }
            } catch (e: Exception) {
                Diagnostics.failure("pin", e)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Minimizing with the screen on and no keyguard leaves the note visible;
        // locking (screen off / keyguard up) conceals it. The SECRET visibility
        // on the full note covers any gap before SCREEN_OFF is delivered.
        syncNotificationOnBackground("onPause", alwaysConceal = false)
    }

    override fun onStop() {
        super.onStop()
        syncNotificationOnBackground("onStop", alwaysConceal = false)
    }

    // singleTask relaunches (launcher icon, notification Open action)
    // arrive here instead of a new onCreate: keep the latest intent so
    // getIntent() never serves a stale one.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveExternalAction(intent)
    }

    private fun applyBarAppearance(darkTheme: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).let {
            it.isAppearanceLightStatusBars = !darkTheme
            it.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    // Synchronous system-theme read for launch/resume, before any compose
    // state exists. Stored prefs resolve asynchronously in setContent and
    // onResume instead — never via a blocking read on the main thread.
    private fun isSystemDark(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
}
