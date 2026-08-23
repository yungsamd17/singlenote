package com.yungsamd17.singlenote.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class NotePreferences(private val context: Context) {

    val pinned: Flow<Boolean> = context.settingsDataStore.data.map { it[KEY_PINNED] ?: false }

    suspend fun setPinned(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_PINNED] = value }
    }

    companion object {
        val KEY_PINNED = booleanPreferencesKey("pin_current_note")
    }
}
