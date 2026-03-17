package app.justtalk.core.logging

import android.content.Context
import android.os.Build
import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal file logger for debugging real devices.
 *
 * File location on device:
 *   /data/data/app.justtalk/files/logs/justtalk.log
 *
 * Pull via adb:
 *   adb shell run-as app.justtalk cat files/logs/justtalk.log
 *   adb shell run-as app.justtalk cp files/logs/justtalk.log /sdcard/Download/justtalk.log
 */
object AppLog {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

    private lateinit var logDir: File
    private lateinit var logFile: File

    private val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        // Logging location strategy:
        // - Android <= 28: we can write to public Documents (with legacy storage permission).
        // - Android 29+: direct file writes to public Documents are restricted; use app-scoped external docs dir.
        val baseDir =
            if (Build.VERSION.SDK_INT <= 28) {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            } else {
                // Usually visible under Android/data/... (some file managers hide it), but reliable without extra perms.
                context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            }
        logDir = File(File(baseDir, "JustTalk"), "logs")
        logDir.mkdirs()
        logFile = File(logDir, "justtalk.log")

        // Keep log size under control (rotate at ~2MB).
        rotateIfNeeded()

        val header = buildString {
            appendLine("=== JustTalk log start ===")
            appendLine("time=${ts.format(Date())}")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("sdk=${Build.VERSION.SDK_INT}")
            appendLine("logDir=${runCatching { logDir.absolutePath }.getOrDefault("unknown")}")
            appendLine("==========================")
        }
        writeNow(header)

        // Crash handler that logs to file before process dies.
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                writeNow(format("CRASH", "Uncaught exception on thread=${t.name}", e))
            } catch (_: Exception) {
                // ignore
            } finally {
                prev?.uncaughtException(t, e)
            }
        }
    }

    fun currentLogPathOrNull(): String? =
        if (::logFile.isInitialized) runCatching { logFile.absolutePath }.getOrNull() else null

    fun exportToUri(context: Context, uri: android.net.Uri): Boolean {
        if (!::logFile.isInitialized) return false
        return runCatching {
            context.contentResolver.openOutputStream(uri, "w")!!.use { out ->
                logFile.inputStream().use { it.copyTo(out) }
            }
            true
        }.getOrDefault(false)
    }

    fun d(tag: String, msg: String) = enqueue(format("D/$tag", msg))
    fun i(tag: String, msg: String) = enqueue(format("I/$tag", msg))
    fun w(tag: String, msg: String, e: Throwable? = null) = enqueue(format("W/$tag", msg, e))
    fun e(tag: String, msg: String, e: Throwable? = null) = enqueue(format("E/$tag", msg, e))

    private fun enqueue(line: String) {
        if (!started.get()) return
        scope.launch {
            try {
                rotateIfNeeded()
                logFile.appendText(line)
            } catch (_: Exception) {
                // ignore file I/O issues
            }
        }
    }

    private fun writeNow(text: String) {
        runCatching {
            rotateIfNeeded()
            logFile.appendText(text)
        }
    }

    private fun rotateIfNeeded() {
        if (!::logFile.isInitialized) return
        val maxBytes = 2L * 1024L * 1024L
        if (logFile.exists() && logFile.length() > maxBytes) {
            val rotated = File(logDir, "justtalk-${System.currentTimeMillis()}.log")
            runCatching { logFile.renameTo(rotated) }
        }
    }

    private fun format(levelTag: String, msg: String, e: Throwable? = null): String {
        val base = "${ts.format(Date())} $levelTag: $msg"
        if (e == null) return base + "\n"
        val err = e.stackTraceToString()
        return base + "\n" + err + "\n"
    }
}

