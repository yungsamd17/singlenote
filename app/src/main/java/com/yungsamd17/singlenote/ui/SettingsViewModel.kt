package com.yungsamd17.singlenote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yungsamd17.singlenote.data.NoteStore
import com.yungsamd17.singlenote.data.NotePreferences.Companion.FONT_DEFAULT
import com.yungsamd17.singlenote.data.NotePreferences.Companion.SIZE_MEDIUM
import com.yungsamd17.singlenote.data.NotePreferences.Companion.THEME_SYSTEM
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val store: NoteStore) : ViewModel() {

    val themeMode: StateFlow<String> = store.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), THEME_SYSTEM)

    val fontFamily: StateFlow<String> = store.fontFamily
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FONT_DEFAULT)

    val textSize: StateFlow<String> = store.textSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SIZE_MEDIUM)

    val notificationsEnabled: StateFlow<Boolean> = store.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setThemeMode(value: String) {
        viewModelScope.launch { store.setThemeMode(value) }
    }

    fun setFontFamily(value: String) {
        viewModelScope.launch { store.setFontFamily(value) }
    }

    fun setTextSize(value: String) {
        viewModelScope.launch { store.setTextSize(value) }
    }

    fun setNotificationsEnabled(value: Boolean) {
        viewModelScope.launch { store.setNotificationsEnabled(value) }
    }

    companion object {
        fun factory(store: NoteStore) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(store) as T
        }
    }
}
