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
 * and the SharedPreferences fallback below it. The modern path runs on SDK 34
 * like the rest of the suite, so the tests share one Robolectric sandbox; the
 * fallback needs its own pre-Android-13 sandbox and keeps SDK 30.
 */
@RunWith(RobolectricTestRunner::class)
class AppLanguageTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    @Config(sdk = [34])
    fun theLanguageTagFollowsTheAppLocales() {
        assertEquals("", AppLanguage.getLanguageTag(context))

        AppLanguage.setLanguage(context, "de")

        assertEquals("de", AppLanguage.getLanguageTag(context))
    }

    @Test
    @Config(sdk = [34])
    fun settingTheSameLanguageChangesNothing() {
        AppLanguage.setLanguage(context, "de")

        AppLanguage.setLanguage(context, "de")

        assertEquals("de", AppLanguage.getLanguageTag(context))
    }

    @Test
    @Config(sdk = [34])
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
    @Config(sdk = [34])
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
}
