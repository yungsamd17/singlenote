package com.yungsamd17.singlenote.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.yungsamd17.singlenote.util.Diagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Keeps the pinned notification in sync with the keyguard.
 *
 * The note is hidden whenever the lock screen is up (see
 * [pinnedNotificationContent]), but Android does not broadcast a "keyguard
 * shown" event. Screen off / on and USER_PRESENT cover the transitions:
 * both screen events conceal, and only USER_PRESENT — a positive signal that
 * the keyguard is gone — reveals the full note. The API's `isKeyguardLocked`
 * is logged for diagnosis but deliberately not trusted to reveal: on HyperOS
 * it reports false while the lock screen is still shown, and with "show
 * notification content" enabled that leaks the note text.
 */
class LockScreenReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON,
            Intent.ACTION_SCREEN_OFF,
            Intent.ACTION_USER_PRESENT -> Unit
            else -> return
        }
        // Screen on is not proof of unlock: on HyperOS the lock screen can
        // still be up (fingerprint not used yet) while the keyguard API says
        // false, so keep the note hidden until USER_PRESENT explicitly says
        // the user is past the lock screen.
        val forceConcealed = intent.action != Intent.ACTION_USER_PRESENT
        // Flip the flag first, synchronously: every later refresh must keep
        // the placeholder until a genuine unlock arrives.
        if (forceConcealed) {
            PinNotification.markConcealed(context)
        } else {
            PinNotification.markRevealed()
        }
        val keyguardLocked = context.getSystemService(android.app.KeyguardManager::class.java)
            ?.isKeyguardLocked ?: false
        Diagnostics.event(
            "lockscreen",
            "action=${intent.action} keyguard=$keyguardLocked forceConcealed=$forceConcealed"
        )
        val pendingResult = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                PinNotification.refresh(app, forceConcealed = forceConcealed)
            } catch (e: Exception) {
                Log.w(TAG, "updating pinned note for the lock screen failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "LockScreenReceiver"

        /**
         * Some OEM ROMs (HyperOS confirmed) never deliver these broadcasts to
         * manifest-registered receivers. Register a live receiver as well: while
         * the process is alive it gets the events directly, which is exactly
         * when the pinned note was last posted and needs updating.
         */
        fun registerDynamically(context: Context) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            @Suppress("UnspecifiedRegisterReceiverFlag")
            if (Build.VERSION.SDK_INT >= 33) {
                context.applicationContext.registerReceiver(
                    LockScreenReceiver(),
                    filter,
                    Context.RECEIVER_NOT_EXPORTED,
                )
            } else {
                context.applicationContext.registerReceiver(LockScreenReceiver(), filter)
            }
            Diagnostics.event("lockscreen", "dynamic receiver registered")
        }
    }
}
