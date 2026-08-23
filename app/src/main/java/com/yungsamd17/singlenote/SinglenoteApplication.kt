package com.yungsamd17.singlenote

import android.app.Application
import com.yungsamd17.singlenote.data.AppDatabase
import com.yungsamd17.singlenote.data.NoteRepository

class SinglenoteApplication : Application() {

    val repository: NoteRepository by lazy {
        NoteRepository(AppDatabase.get(this).noteDao(), this)
    }
}
