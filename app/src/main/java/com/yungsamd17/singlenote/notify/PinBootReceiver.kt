package com.yungsamd17.singlenote.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Brings the pinned note back after a reboot or an app update:
 * notifications don't survive either, so without this the pin silently
 * disappears until the app is opened again. Plain BOOT_COMPLETED (not
 * direct-boot aware) is deliberate: it fires after unlock, when
 * credential-encrypted storage (DataStore/Room) is available.
 * MY_PACKAGE_REPLACED is delivered while the app is already unlocked,
 * so the same refresh applies. [PinNotification.refresh] no-ops unless
 * a note is pinned with content.
 */
class PinBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        val pendingResult = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                PinNotification.refresh(app)
            } catch (e: Exception) {
                Log.w(TAG, "re-posting pinned note after boot failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "PinBootReceiver"
    }
}
