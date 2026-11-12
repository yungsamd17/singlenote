package com.yungsamd17.singlenote.notify

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the pinned-notification channel: the current channel is created with
 * the right importance and the legacy channels are removed, because their
 * settings would otherwise stick forever.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PinNotificationChannelTest {

    private lateinit var context: Context
    private lateinit var manager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = context.getSystemService(NotificationManager::class.java)
    }

    @Test
    fun ensureChannelCreatesTheCurrentChannel() {
        PinNotification.ensureChannel(context)

        val channel = manager.getNotificationChannel(PinNotification.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
    }

    @Test
    fun ensureChannelRemovesTheLegacyChannels() {
        listOf(
            PinNotification.LEGACY_CHANNEL_ID,
            PinNotification.LEGACY_CHANNEL_ID_V2,
            PinNotification.LEGACY_CHANNEL_ID_V3,
        ).forEach { manager.createNotificationChannel(android.app.NotificationChannel(it, it, NotificationManager.IMPORTANCE_LOW)) }

        PinNotification.ensureChannel(context)

        assertNull(manager.getNotificationChannel(PinNotification.LEGACY_CHANNEL_ID))
        assertNull(manager.getNotificationChannel(PinNotification.LEGACY_CHANNEL_ID_V2))
        assertNull(manager.getNotificationChannel(PinNotification.LEGACY_CHANNEL_ID_V3))
    }

    @Test
    fun ensureChannelKeepsAnExistingChannel() {
        PinNotification.ensureChannel(context)
        val channel = manager.getNotificationChannel(PinNotification.CHANNEL_ID)!!

        PinNotification.ensureChannel(context)

        val again = manager.getNotificationChannel(PinNotification.CHANNEL_ID)!!
        assertEquals(channel.name, again.name)
        assertEquals(channel.importance, again.importance)
    }
}
