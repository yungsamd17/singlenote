package com.yungsamd17.singlenote

import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                colorScheme = accentScheme(accentKey, darkTheme)
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
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
                                viewModel = viewModel(factory = NoteViewModel.factory(repository)),
                                onOpenArchive = { navController.navigate("archive") },
                                onOpenSettings = { navController.navigate("settings") }
                            )
                        }
                        composable("archive") {
                            ArchiveScreen(
                                viewModel = viewModel(factory = ArchiveViewModel.factory(repository)),
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                viewModel = viewModel(factory = SettingsViewModel.factory(repository)),
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
