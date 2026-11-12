package com.yungsamd17.singlenote.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the on-device diagnostics: the event log, the crash log and the
 * report the hidden dialog shows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiagnosticsTest {

    private lateinit var context: Context
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // The crash recorder replaces the JVM-wide handler: keep the original
        // around and put it back afterwards.
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(null)
        File(context.filesDir, "diagnostics-events.log").delete()
        File(context.filesDir, "diagnostics-crash.log").delete()
    }

    @After
    fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
    }

    private fun eventLog(): String = File(context.filesDir, "diagnostics-events.log").readText()

    private fun crashLog(): String = File(context.filesDir, "diagnostics-crash.log").readText()

    @Test
    fun eventsAreWrittenToTheLog() {
        Diagnostics.install(context)

        Diagnostics.event("widget", "updated")

        assertTrue(eventLog().contains("I/widget updated"))
    }

    @Test
    fun failuresAreWrittenWithTheirStackTrace() {
        Diagnostics.install(context)

        Diagnostics.failure("widget", IllegalStateException("boom"))

        val log = eventLog()
        assertTrue(log.contains("E/widget java.lang.IllegalStateException: boom"))
        assertTrue(log.contains("IllegalStateException"))
    }

    @Test
    fun theCrashHandlerRecordsUncaughtExceptions() {
        Diagnostics.install(context)

        Thread.getDefaultUncaughtExceptionHandler()
            ?.uncaughtException(Thread.currentThread(), RuntimeException("crash!"))

        val log = crashLog()
        assertTrue(log.contains("uncaught in"))
        assertTrue(log.contains("crash!"))
    }

    @Test
    fun theReportHasEverySection() = runBlocking {
        Diagnostics.install(context)
        Diagnostics.event("widget", "updated")

        val report = Diagnostics.report(context)

        listOf("[BUILD]", "[WIDGET]", "[GLANCE WORK]", "[SETTINGS]", "[EVENTS]", "[LAST CRASH]", "[LOGCAT]")
            .forEach { assertTrue("missing $it", report.contains(it)) }
        assertTrue(report.contains("package: "))
        assertTrue(report.contains("I/widget updated"))
    }

    @Test
    fun theReportWorksWithoutAnyLoggedEvent() = runBlocking {
        Diagnostics.install(context)

        val report = Diagnostics.report(context)

        // An empty log reads as "none" instead of an empty section.
        assertTrue(report.contains("[EVENTS]"))
        assertTrue(report.contains("none"))
    }
}
