package com.yungsamd17.singlenote

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.yungsamd17.singlenote.util.ActionSecurity

/**
 * System share-sheet target: "Save as note".
 *
 * This is the only exported entry point other apps are meant to call, so it
 * does not accept any of the privileged widget/shortcut actions. It reads the
 * shared text and forwards just that to [MainActivity] with our own token;
 * MainActivity then runs the same quick-capture path as the notification and
 * the tile (archive the current note, keep its pin, start a new note).
 */
class ShareNoteActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
            finish()
            return
        }
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_SHARE_TEXT, text)
                putExtra(ActionSecurity.EXTRA_TOKEN, ActionSecurity.token(this@ShareNoteActivity))
            },
        )
        finish()
    }
}
