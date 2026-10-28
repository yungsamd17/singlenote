package com.yungsamd17.singlenote

import android.app.Application
import android.content.Context
import com.yungsamd17.singlenote.data.AppDatabase
import com.yungsamd17.singlenote.data.NoteRepository
import com.yungsamd17.singlenote.util.AppLanguage

class SinglenoteApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }

    val repository: NoteRepository by lazy {
        NoteRepository(AppDatabase.get(this).noteDao(), this)
    }
}
