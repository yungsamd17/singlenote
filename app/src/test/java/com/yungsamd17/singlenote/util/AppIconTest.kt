package com.yungsamd17.singlenote.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the launcher icon switch: only one alias stays enabled, and asking
 * for the icon that is already active changes nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppIconTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun alias(name: String) =
        ComponentName(context, "com.yungsamd17.singlenote.launcher.$name")

    private fun enabledState(name: String): Int =
        context.packageManager.getComponentEnabledSetting(alias(name))

    @Test
    fun applyingTheDefaultIconWhenItIsActiveDoesNothing() {
        assertFalse(AppIcon.apply(context, "default", "default"))
        assertEquals(
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            enabledState("Default"),
        )
    }

    @Test
    fun applyingAnAccentIconEnablesItAndDisablesTheOthers() {
        assertTrue(AppIcon.apply(context, "accent", "blue"))

        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, enabledState("Blue"))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, enabledState("Default"))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, enabledState("Teal"))
    }

    @Test
    fun applyingTheSameIconTwiceChangesNothingTheSecondTime() {
        assertTrue(AppIcon.apply(context, "accent", "green"))

        assertFalse(AppIcon.apply(context, "accent", "green"))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, enabledState("Green"))
    }

    @Test
    fun theDynamicIconHasItsOwnAlias() {
        assertTrue(AppIcon.apply(context, "dynamic", "default"))

        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, enabledState("Dynamic"))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, enabledState("Default"))
    }
}
