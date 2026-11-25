package com.yungsamd17.singlenote

import android.app.Application
import android.content.Context
import com.yungsamd17.singlenote.data.AppDatabase
import com.yungsamd17.singlenote.data.NoteRepository
import com.yungsamd17.singlenote.notify.LockScreenReceiver
import com.yungsamd17.singlenote.util.AppLanguage
import com.yungsamd17.singlenote.util.Diagnostics
import com.yungsamd17.singlenote.widget.WidgetThemeWatcher

class SinglenoteApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        // Records crashes and widget faults for the hidden diagnostics dialog.
        Diagnostics.install(this)
        // HyperOS drops the manifest receiver for screen/lock broadcasts, so
        // keep a live one too while this process is around.
        LockScreenReceiver.registerDynamically(this)
        // Re-render "system" theme widgets when day/night actually flips.
        WidgetThemeWatcher.register(this)
    }

    val repository: NoteRepository by lazy {
        NoteRepository(AppDatabase.get(this).noteDao(), this)
    }
}
