package com.yungsamd17.singlenote.util

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.work.WorkManager
import com.yungsamd17.singlenote.BuildConfig
import com.yungsamd17.singlenote.data.NotePreferences
import com.yungsamd17.singlenote.widget.SinglenoteWidgetReceiver
import com.yungsamd17.singlenote.widget.WidgetStyleStore
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * On-device diagnostics for builds where nobody can attach a debugger.
 *
 * Release APKs have no usable logcat window, so widget failures and crashes are
 * recorded into the app's private storage and surfaced by the hidden diagnostics
 * dialog (About → tap the version line five times). Everything is local: nothing
 * is collected until a fault is logged, and nothing is ever uploaded.
 */
object Diagnostics {
    private const val EVENT_FILE = "diagnostics-events.log"
    private const val CRASH_FILE = "diagnostics-crash.log"

    /** Keep the event log small: it lives in the app's data dir, not a database. */
    private const val MAX_EVENT_BYTES = 48 * 1024

    private const val MAX_STACK_CHARS = 6_000
    private const val MAX_LOGCAT_LINES = 250

    @Volatile private var appContext: Context? = null

    /** Called once from Application so faults can be logged before any UI exists. */
    fun install(context: Context) {
        appContext = context.applicationContext
        val current = Thread.getDefaultUncaughtExceptionHandler()
        if (current is CrashRecorder) return
        Thread.setDefaultUncaughtExceptionHandler(CrashRecorder(current))
    }

    /** Records a normal milestone (widget updated, config saved, …). */
    fun event(tag: String, message: String) {
        append(EVENT_FILE, "I/$tag $message")
    }

    /** Records a caught failure with its stack trace. */
    fun failure(tag: String, error: Throwable) {
        append(EVENT_FILE, "E/$tag ${error.javaClass.name}: ${error.message}\n${stackTrace(error)}")
    }

    /**
     * Builds the text the diagnostics dialog shows: build identity, widget state,
     * the recorded event log, the last crash, and this app's own logcat tail.
     */
    suspend fun report(context: Context): String = withContext(Dispatchers.IO) {
        appContext = context.applicationContext
        buildString {
            appendLine("Singlenote diagnostics")
            appendLine("generated ${timestamp()}")
            appendLine()
            appendLine("[BUILD]")
            appendLine(buildSection(context))
            appendLine()
            appendLine("[WIDGET]")
            appendLine(widgetSection(context))
            appendLine()
            appendLine("[GLANCE WORK]")
            appendLine(glanceWorkSection(context))
            appendLine()
            appendLine("[SETTINGS]")
            appendLine(settingsSection(context))
            appendLine()
            appendLine("[EVENTS]")
            appendLine(readLog(context, EVENT_FILE))
            appendLine()
            appendLine("[LAST CRASH]")
            appendLine(readLog(context, CRASH_FILE))
            appendLine()
            appendLine("[LOGCAT] (this app only)")
            appendLine(logcat(context))
        }
    }

    private fun buildSection(context: Context): String = buildString {
        val info =
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }.getOrNull()
        appendLine("version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("package: ${context.packageName}")
        appendLine("build: ${BuildConfig.BUILD_TYPE}, debuggable=${BuildConfig.DEBUG}")
        appendLine("installed version code: ${info?.longVersionCode ?: -1}")
        appendLine(
            "sdk: device ${Build.VERSION.SDK_INT}, " +
                "target ${info?.applicationInfo?.targetSdkVersion ?: 0}"
        )
        appendLine(
            "device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}), " +
                "${Build.VERSION.RELEASE}, ${Build.VERSION.INCREMENTAL}"
        )
        appendLine("abi: ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine(
            "locale: ${Locale.getDefault()}, " +
                "resources: ${context.resources.configuration.locales[0]}"
        )
    }

    private fun widgetSection(context: Context): String = buildString {
        val manager = AppWidgetManager.getInstance(context)
        val ids =
            runCatching {
                manager.getAppWidgetIds(ComponentName(context, SinglenoteWidgetReceiver::class.java))
            }.getOrElse { intArrayOf() }
        appendLine("provider: ${SinglenoteWidgetReceiver::class.java.name}")
        appendLine("widgets: ${ids.size}")
        val remembered = WidgetStyleStore.remembered(context)
        ids.sorted().forEach { id ->
            val style = WidgetStyleStore.get(context, id)
            val stored = WidgetStyleStore.exists(context, id)
            val source =
                when {
                    stored -> "set"
                    remembered != null -> "remembered"
                    else -> "default"
                }
            appendLine(
                "  id $id (styles $source): " +
                    "theme=${style.theme} accent=${style.accent} size=${style.size} " +
                    "action=${style.action} title=${style.titleSize}sp " +
                    "showTitle=${style.showTitle} opacity=${style.opacity}"
            )
        }
    }

    /**
     * The state of the workers Glance queued for the widgets.
     *
     * Glance renders every widget in a WorkManager worker named
     * "appWidget-<id>". A worker that stays enqueued never renders, and a
     * worker that failed lost its in-memory session, so this is what tells
     * the two apart.
     */
    private suspend fun glanceWorkSection(context: Context): String = withContext(Dispatchers.IO) {
        val ids =
            runCatching {
                AppWidgetManager.getInstance(context)
                    .getAppWidgetIds(ComponentName(context, SinglenoteWidgetReceiver::class.java))
            }.getOrElse { intArrayOf() }
        if (ids.isEmpty()) return@withContext "no widgets"
        buildString {
            ids.sorted().forEach { id ->
                val states =
                    runCatching {
                        WorkManager.getInstance(context)
                            .getWorkInfosForUniqueWork("appWidget-$id")
                            .get(5, TimeUnit.SECONDS)
                            .joinToString(", ") { "${it.state} attempt=${it.runAttemptCount}" }
                            .ifEmpty { "no work" }
                    }.getOrElse { "unavailable (${it.javaClass.simpleName})" }
                appendLine("  id $id: $states")
            }
        }
    }

    /** DataStore reads are guarded: a broken store must not hang the report. */
    private suspend fun settingsSection(context: Context): String {
        val prefs = NotePreferences.get(context)
        suspend fun <T> read(flow: kotlinx.coroutines.flow.Flow<T>): T? =
            runCatching { withTimeoutOrNull(1_500) { flow.first() } }.getOrNull()
        return buildString {
            appendLine("accent: ${read(prefs.accentColor)?.toString() ?: "unavailable"}")
            appendLine("dynamic colors: ${read(prefs.dynamicColors)?.toString() ?: "unavailable"}")
            appendLine("amoled: ${read(prefs.amoled)?.toString() ?: "unavailable"}")
            appendLine("theme: ${read(prefs.themeMode)?.toString() ?: "unavailable"}")
            appendLine("language override: ${AppLanguage.getLanguageTag(context).ifBlank { "system" }}")
        }
    }

    private fun logcat(context: Context): String {
        val output =
            try {
                val process =
                    ProcessBuilder("logcat", "-d", "-v", "time", "-t", "1500")
                        .redirectErrorStream(true)
                        .start()
                val text = process.inputStream.bufferedReader().use { it.readText() }
                process.waitFor(2, TimeUnit.SECONDS)
                text
            } catch (error: Throwable) {
                return "unavailable (${error.javaClass.simpleName}: ${error.message})"
            }
        val packageName = context.packageName
        val lines =
            output.lineSequence()
                // The widget renders in a WorkManager worker, so the worker and
                // job scheduler logs decide whether it ever runs. The input
                // event spam is dropped: it fills the report without saying
                // anything about the widget.
                .filter {
                    packageName in it ||
                        "GlanceAppWidget" in it ||
                        "AndroidRuntime" in it ||
                        "GlanceSession" in it ||
                        "GlanceAppWidgetSession" in it ||
                        "WM-" in it ||
                        "JobScheduler" in it ||
                        "WorkManager" in it
                }
                .filterNot { "MIUIInput" in it || "InputDispatcher" in it }
                .toList()
        if (lines.isEmpty()) return "no entries for $packageName"
        return lines.takeLast(MAX_LOGCAT_LINES).joinToString("\n")
    }

    private fun readLog(context: Context, file: String): String {
        val target = File(context.filesDir, file)
        if (!target.exists()) return "none"
        return runCatching { target.readText().trim().ifEmpty { "none" } }
            .getOrElse { "unreadable (${it.javaClass.simpleName})" }
    }

    private fun append(file: String, line: String) {
        val context = appContext ?: return
        synchronized(this) {
            runCatching {
                val target = File(context.filesDir, file)
                if (target.length() > MAX_EVENT_BYTES) target.delete()
                target.appendText("${timestamp()} $line\n")
            }
        }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

    private fun stackTrace(error: Throwable): String {
        val writer = StringWriter()
        error.printStackTrace(PrintWriter(writer))
        val text = writer.toString()
        return if (text.length <= MAX_STACK_CHARS) text else text.take(MAX_STACK_CHARS) + "…"
    }

    private class CrashRecorder(
        private val previous: Thread.UncaughtExceptionHandler?
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, error: Throwable) {
            val context = appContext
            if (context != null) {
                runCatching {
                    File(context.filesDir, CRASH_FILE)
                        .writeText(
                            buildString {
                                appendLine("${timestamp()} uncaught in ${thread.name}")
                                appendLine(
                                    "version ${BuildConfig.VERSION_NAME} " +
                                        "(${BuildConfig.VERSION_CODE}), ${BuildConfig.BUILD_TYPE}, " +
                                        "${Build.MODEL}, API ${Build.VERSION.SDK_INT}"
                                )
                                appendLine(stackTrace(error))
                            }
                        )
                    append(EVENT_FILE, "E/crash ${error.javaClass.name}: ${error.message}")
                }
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
