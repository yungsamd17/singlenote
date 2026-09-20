package com.yungsamd17.singlenote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yungsamd17.singlenote.data.NoteStore
import com.yungsamd17.singlenote.data.NotePreferences.Companion.ACCENT_DEFAULT
import com.yungsamd17.singlenote.data.NotePreferences.Companion.FONT_DEFAULT
import com.yungsamd17.singlenote.data.NotePreferences.Companion.SIZE_MEDIUM
import com.yungsamd17.singlenote.data.NotePreferences.Companion.THEME_SYSTEM
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val store: NoteStore) : ViewModel() {

    // False until the stored prefs have emitted their disk truth. The
    // settings screen waits for it so rows show the saved values on the
    // first frame instead of flashing the display defaults (e.g. "Medium"
    // when Large is stored).
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    val themeMode: StateFlow<String> = store.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), THEME_SYSTEM)

    val fontFamily: StateFlow<String> = store.fontFamily
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FONT_DEFAULT)

    val textSize: StateFlow<String> = store.textSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SIZE_MEDIUM)

    val accentColor: StateFlow<String> = store.accentColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ACCENT_DEFAULT)

    val notificationsEnabled: StateFlow<Boolean> = store.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // Initial matches the DataStore default (on): the toggle's own flow
    // starts cold when the rows first compose, so a mismatched initial
    // would flash the wrong state for a frame on fresh launch.
    val lockscreenVisible: StateFlow<Boolean> = store.lockscreenVisible
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    init {
        viewModelScope.launch {
            // Raw store flows, not the stateIn'd ones above (those start
            // from display defaults): first emissions are the stored truth.
            combine(
                store.themeMode,
                store.fontFamily,
                store.textSize,
                store.accentColor,
                store.notificationsEnabled
            ) { _, _, _, _, _ -> Unit }.first()
            // Separate first: the typed combine overloads stop at five
            // flows, so the new pref is awaited on its own.
            store.lockscreenVisible.first()
            _ready.value = true
        }
    }

    fun setThemeMode(value: String) {
        viewModelScope.launch { store.setThemeMode(value) }
    }

    fun setFontFamily(value: String) {
        viewModelScope.launch { store.setFontFamily(value) }
    }

    fun setTextSize(value: String) {
        viewModelScope.launch { store.setTextSize(value) }
    }

    fun setAccentColor(value: String) {
        viewModelScope.launch { store.setAccentColor(value) }
    }

    fun setNotificationsEnabled(value: Boolean) {
        viewModelScope.launch { store.setNotificationsEnabled(value) }
    }

    fun setLockscreenVisible(value: Boolean) {
        viewModelScope.launch { store.setLockscreenVisible(value) }
    }

    companion object {
        fun factory(store: NoteStore) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(store) as T
        }
    }
}
