package com.yungsamd17.singlenote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yungsamd17.singlenote.data.NotePreferences
import com.yungsamd17.singlenote.ui.ArchiveScreen
import com.yungsamd17.singlenote.ui.ArchiveViewModel
import com.yungsamd17.singlenote.ui.NoteScreen
import com.yungsamd17.singlenote.ui.NoteViewModel
import com.yungsamd17.singlenote.ui.SettingsScreen
import com.yungsamd17.singlenote.ui.SettingsViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as SinglenoteApplication).repository

        setContent {
            val themeMode by produceState(
                initialValue = NotePreferences.THEME_SYSTEM,
                producer = {
                    NotePreferences(applicationContext).themeMode.collect { value = it }
                }
            )
            val darkTheme = when (themeMode) {
                NotePreferences.THEME_DARK -> true
                NotePreferences.THEME_LIGHT -> false
                else -> isSystemInDarkTheme()
            }
            MaterialTheme(
                colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "note") {
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
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                    FirstRunTip()
                }
            }
        }
    }

    @Composable
    private fun FirstRunTip() {
        val snackbarHostState = remember { SnackbarHostState() }
        val prefs = getSharedPreferences(PREFS_TIPS, MODE_PRIVATE)
        LaunchedEffect(Unit) {
            if (!prefs.getBoolean(KEY_TIP_SHOWN, false)) {
                prefs.edit().putBoolean(KEY_TIP_SHOWN, true).apply()
                snackbarHostState.showSnackbar(getString(R.string.tip_first_run))
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    companion object {
        private const val PREFS_TIPS = "tips"
        private const val KEY_TIP_SHOWN = "first_run_tip_shown"
    }
}
