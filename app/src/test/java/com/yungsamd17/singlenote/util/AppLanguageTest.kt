package com.yungsamd17.singlenote.util

import android.app.Activity
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the language override: the platform app-locales path on Android 13+
 * and the SharedPreferences fallback below it.
 */
@RunWith(RobolectricTestRunner::class)
class AppLanguageTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    @Config(sdk = [33])
    fun theLanguageTagFollowsTheAppLocales() {
        assertEquals("", AppLanguage.getLanguageTag(context))

        AppLanguage.setLanguage(context, "de")

        assertEquals("de", AppLanguage.getLanguageTag(context))
    }

    @Test
    @Config(sdk = [33])
    fun settingTheSameLanguageChangesNothing() {
        AppLanguage.setLanguage(context, "de")

        AppLanguage.setLanguage(context, "de")

        assertEquals("de", AppLanguage.getLanguageTag(context))
    }

    @Test
    @Config(sdk = [33])
    fun anEmptyTagClearsTheOverride() {
        AppLanguage.setLanguage(context, "de")

        AppLanguage.setLanguage(context, "")

        assertEquals("", AppLanguage.getLanguageTag(context))
    }

    @Test
    @Config(sdk = [30])
    fun theOverrideIsStoredBelowAndroid13() {
        assertEquals("", AppLanguage.getLanguageTag(context))

        AppLanguage.setLanguage(context, "de")

        assertEquals("de", AppLanguage.getLanguageTag(context))
    }

    @Test
    @Config(sdk = [30])
    fun anEmptyTagClearsTheStoredOverride() {
        AppLanguage.setLanguage(context, "de")

        AppLanguage.setLanguage(context, "")

        assertEquals("", AppLanguage.getLanguageTag(context))
    }

    @Test
    @Config(sdk = [30])
    fun wrapAppliesTheStoredLanguage() {
        AppLanguage.setLanguage(context, "de")

        val wrapped = AppLanguage.wrap(context)

        assertEquals("de", wrapped.resources.configuration.locales[0].language)
    }

    @Test
    @Config(sdk = [30])
    fun wrapKeepsTheContextWithoutAnOverride() {
        assertSame(context, AppLanguage.wrap(context))
    }

    @Test
    @Config(sdk = [33])
    fun wrapKeepsTheContextOnAndroid13() {
        AppLanguage.setLanguage(context, "de")

        assertSame(context, AppLanguage.wrap(context))
    }

    @Test
    @Config(sdk = [30])
    fun anActivityContextStoresTheOverrideToo() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        AppLanguage.setLanguage(activity, "de")

        assertEquals("de", AppLanguage.getLanguageTag(activity))
    }

    @Test
    @Config(sdk = [33])
    fun finishingWithoutASnapshotDoesNothing() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        // No transition was started, so there is nothing to finish.
        AppLanguage.finishTransition(activity)
    }
}
