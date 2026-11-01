package com.yungsamd17.singlenote.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** Launcher backgrounds are packaged resources; component changes keep the running task alive. */
object AppIcon {
    private val aliases =
        listOf("Default", "AccentDefault", "Blue", "Teal", "Green", "Orange", "Pink", "Dynamic")

    fun apply(context: Context, icon: String, accent: String): Boolean {
        val selected =
            if (icon == "dynamic") "Dynamic"
            else if (icon != "accent") "Default"
            else
                when (accent) {
                    "blue" -> "Blue"
                    "teal" -> "Teal"
                    "green" -> "Green"
                    "orange" -> "Orange"
                    "pink" -> "Pink"
                    else -> "AccentDefault"
                }
        val manager = context.packageManager
        val destination = ComponentName(context, "com.yungsamd17.singlenote.launcher.$selected")
        val current = manager.getComponentEnabledSetting(destination)
        val alreadySelected = current == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || (selected == "Default" && current == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        if (alreadySelected) return false
        // Enable the destination first so a launcher entry always exists.
        (listOf(selected) + aliases.filter { it != selected }).forEach { name ->
            val component = ComponentName(context, "com.yungsamd17.singlenote.launcher.$name")
            val target =
                if (name == selected) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            if (manager.getComponentEnabledSetting(component) != target) {
                manager.setComponentEnabledSetting(component, target, PackageManager.DONT_KILL_APP)
            }
        }
        return true
    }
}
