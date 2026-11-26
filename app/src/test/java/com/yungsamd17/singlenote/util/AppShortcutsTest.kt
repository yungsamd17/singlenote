package com.yungsamd17.singlenote.util

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.yungsamd17.singlenote.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppShortcutsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun publishPublishesEveryShortcutWithItsLabel() {
        AppShortcuts.publish(context)

        val shortcuts = ShortcutManagerCompat.getDynamicShortcuts(context).associateBy { it.id }
        assertEquals(
            setOf(
                AppShortcuts.TARGET_NEW_NOTE,
                AppShortcuts.TARGET_ARCHIVE,
                AppShortcuts.TARGET_SETTINGS,
            ),
            shortcuts.keys,
        )
        val expectedLabels = mapOf(
            AppShortcuts.TARGET_NEW_NOTE to R.string.notification_action_new_note,
            AppShortcuts.TARGET_ARCHIVE to R.string.archive_title,
            AppShortcuts.TARGET_SETTINGS to R.string.settings_title,
        )
        expectedLabels.forEach { (target, label) ->
            assertEquals(context.getString(label), shortcuts.getValue(target).shortLabel.toString())
        }
        assertEquals(
            listOf(0, 1, 2),
            listOf(
                shortcuts.getValue(AppShortcuts.TARGET_NEW_NOTE).rank,
                shortcuts.getValue(AppShortcuts.TARGET_ARCHIVE).rank,
                shortcuts.getValue(AppShortcuts.TARGET_SETTINGS).rank,
            ),
        )
    }

    @Test
    fun shortcutIntentsCarryAValidTokenAndTheirTarget() {
        AppShortcuts.publish(context)

        val shortcuts = ShortcutManagerCompat.getDynamicShortcuts(context)
        assertEquals(3, shortcuts.size)
        shortcuts.forEach { shortcut ->
            val intent = shortcut.intent
            assertEquals(shortcut.id, AppShortcuts.target(intent))
            assertTrue(
                ActionSecurity.valid(
                    context,
                    intent.getStringExtra(ActionSecurity.EXTRA_TOKEN),
                ),
            )
        }
    }

    @Test
    fun targetIgnoresMissingAndUnknownValues() {
        assertNull(AppShortcuts.target(null))
        assertNull(AppShortcuts.target(Intent()))
        assertNull(AppShortcuts.target(Intent().putExtra(AppShortcuts.EXTRA_TARGET, "elsewhere")))
        assertEquals(
            AppShortcuts.TARGET_ARCHIVE,
            AppShortcuts.target(
                Intent().putExtra(AppShortcuts.EXTRA_TARGET, AppShortcuts.TARGET_ARCHIVE),
            ),
        )
    }
}
