package com.yungsamd17.singlenote.util

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Restrictive ROMs (Xiaomi, Oppo, Vivo, Huawei and kin) silently drop
 * notification-action broadcasts for apps that aren't allowed to start in
 * the background — the pin works while open and goes dead once swiped
 * away. No permission can grant this; only the user toggling Autostart (or
 * equivalent) in system settings fixes it, so this opens that page.
 * Every attempt is guarded: unknown ROMs and renamed OEM activities fall
 * back to the app-details settings page, never a crash.
 */
object AutostartSettings {

    fun isRestrictiveRom(): Boolean {
        val manufacturer = Build.MANUFACTURER
        return RESTRICTIVE_MANUFACTURERS.any { manufacturer.equals(it, ignoreCase = true) }
    }

    fun open(context: Context): Boolean {
        for (intent in autostartIntents()) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                // Renamed on this ROM version — try the next candidate.
            } catch (_: SecurityException) {
                break
            }
        }
        return try {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun autostartIntents(): List<Intent> {
        val manufacturer = Build.MANUFACTURER
        fun component(pkg: String, cls: String) =
            Intent().setComponent(ComponentName(pkg, cls))
        return when {
            manufacturer.equals("xiaomi", ignoreCase = true) ||
                manufacturer.equals("redmi", ignoreCase = true) ||
                manufacturer.equals("poco", ignoreCase = true) ->
                listOf(
                    component(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                )
            manufacturer.equals("oppo", ignoreCase = true) ||
                manufacturer.equals("realme", ignoreCase = true) ||
                manufacturer.equals("oneplus", ignoreCase = true) ->
                listOf(
                    component(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.startupapp.StartupAppListActivity"
                    )
                )
            manufacturer.equals("vivo", ignoreCase = true) ||
                manufacturer.equals("iqoo", ignoreCase = true) ->
                listOf(
                    component(
                        "com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                    )
                )
            manufacturer.equals("huawei", ignoreCase = true) ||
                manufacturer.equals("honor", ignoreCase = true) ->
                listOf(
                    component(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                )
            else -> emptyList()
        }
    }

    private val RESTRICTIVE_MANUFACTURERS = listOf(
        "xiaomi", "redmi", "poco",
        "oppo", "realme", "oneplus",
        "vivo", "iqoo",
        "huawei", "honor"
    )
}
