package com.yungsamd17.singlenote

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.Surface
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yungsamd17.singlenote.data.NotePreferences
import com.yungsamd17.singlenote.ui.AboutScreen
import com.yungsamd17.singlenote.ui.ArchiveScreen
import com.yungsamd17.singlenote.ui.ArchiveViewModel
import com.yungsamd17.singlenote.ui.LicenseScreen
import com.yungsamd17.singlenote.ui.NoteScreen
import com.yungsamd17.singlenote.ui.NoteViewModel
import com.yungsamd17.singlenote.ui.PrivacyScreen
import com.yungsamd17.singlenote.ui.SettingsScreen
import com.yungsamd17.singlenote.ui.SettingsViewModel
import com.yungsamd17.singlenote.ui.TermsScreen
import com.yungsamd17.singlenote.ui.accentScheme
import com.yungsamd17.singlenote.util.AppLanguage
import com.yungsamd17.singlenote.widget.WidgetActionSecurity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object { const val EXTRA_WIDGET_ACTION = "widget_action" }
    private var widgetAction by mutableStateOf<String?>(null)

    private fun receiveWidgetAction(intent: Intent?) {
        if (WidgetActionSecurity.valid(this, intent?.getStringExtra(WidgetActionSecurity.EXTRA_TOKEN))) {
            widgetAction = intent?.getStringExtra(EXTRA_WIDGET_ACTION)?.takeIf {
                it in setOf("open", "edit", "archive")
            }
        }
        intent?.removeExtra(EXTRA_WIDGET_ACTION)
        intent?.removeExtra(WidgetActionSecurity.EXTRA_TOKEN)
    }


    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLanguage.finishTransition(this)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiveWidgetAction(intent)
        // Match the launch window to the system theme before the first
        // frame. The stored theme needs a DataStore read, which must never
        // block the UI thread — so the launch background uses the system
        // value, and the async theme gate in setContent applies the stored
        // theme (and corrects the bars) as soon as it emits.
        val startDark = isSystemDark()
        lastDarkTheme = startDark
        window.setBackgroundDrawable(
            ColorDrawable(
                ContextCompat.getColor(
                    this,
                    if (startDark) R.color.launch_background_dark else R.color.launch_background
                )
            )
        )
        enableEdgeToEdge()
        // Status/navigation icon colors must follow the app theme, not the
        // system: enableEdgeToEdge() defaults to the system, leaving
        // invisible icons whenever the two disagree (or after re-entry
        // from recents, which re-applies the system styling).
        applyBarAppearance(startDark)
        val repository = (application as SinglenoteApplication).repository

        setContent {
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
            // Paint nothing until the stored theme arrives: falling back to
            // the system theme/default accent first would flash a wrong-theme
            // frame over the matched launch window (see onCreate) on every
            // cold start where the two disagree.
            if (themeMode == null || accent == null) return@setContent
            // Locals: delegated properties keep their nullable type (and
            // can't be smart-cast), so re-assert non-null here.
            val theme: String = themeMode ?: return@setContent
            val accentKey: String = accent ?: return@setContent
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
            MaterialTheme(
                // Material You follows the wallpaper on Android 12+; older
                // releases (and every tuned accent) use the app's schemes.
                colorScheme = if (accentKey == NotePreferences.ACCENT_SYSTEM &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ) {
                    if (darkTheme) dynamicDarkColorScheme(this)
                    else dynamicLightColorScheme(this)
                } else {
                    accentScheme(accentKey, darkTheme)
                }
            ) {
                val background = MaterialTheme.colorScheme.background.toArgb()
                SideEffect {
                    window.setBackgroundDrawable(ColorDrawable(background))
                    window.decorView.setBackgroundColor(background)
                }
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    LaunchedEffect(widgetAction) {
                        if (widgetAction != null && navController.currentDestination?.route != "note") {
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
                    NavHost(
                        navController = navController,
                        startDestination = "note",
                        // Slide + fade page transitions, kept short on purpose:
                        // sliding full screens recomposes every frame, so a
                        // long slide janks on slower devices.
                        enterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = tween(250, easing = FastOutSlowInEasing)
                            ) + fadeIn(animationSpec = tween(200))
                        },
                        exitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { -it },
                                animationSpec = tween(250, easing = FastOutSlowInEasing)
                            ) + fadeOut(animationSpec = tween(200))
                        },
                        popEnterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { -it },
                                animationSpec = tween(250, easing = FastOutSlowInEasing)
                            ) + fadeIn(animationSpec = tween(200))
                        },
                        popExitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(250, easing = FastOutSlowInEasing)
                            ) + fadeOut(animationSpec = tween(200))
                        }
                    ) {
                        composable("note") {
                            NoteScreen(
                                viewModel = noteViewModel,
                                widgetAction = widgetAction,
                                onWidgetActionHandled = { widgetAction = null },
                                onOpenArchive = { navController.navigate("archive") },
                                onOpenSettings = { navController.navigate("settings") }
                            )
                        }
                        composable("archive") {
                            ArchiveScreen(
                                viewModel = archiveViewModel,
                                onBack = { navController.popBackStack() },
                                onRestored = {
                                    noteViewModel.consumePendingUndo()
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                onBack = { navController.popBackStack() },
                                onOpenAbout = { navController.navigate("about") }
                            )
                        }
                        composable("about") {
                            AboutScreen(
                                onBack = { navController.popBackStack() },
                                onOpenTerms = { navController.navigate("terms") },
                                onOpenPrivacy = { navController.navigate("privacy") },
                                onOpenLicense = { navController.navigate("license") }
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

    override fun onResume() {
        super.onResume()
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

    // singleTask relaunches (launcher icon, notification Open action)
    // arrive here instead of a new onCreate: keep the latest intent so
    // getIntent() never serves a stale one.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveWidgetAction(intent)
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
