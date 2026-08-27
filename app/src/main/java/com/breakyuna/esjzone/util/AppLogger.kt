package com.breakyuna.esjzone.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.breakyuna.esjzone.BuildConfig
import com.breakyuna.esjzone.GlobalSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
    CRASH
}

data class LogEntry(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String,
    val stackTrace: String? = null,
    val threadName: String = Thread.currentThread().name
) {
    fun formattedTime(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
    }

    fun toFormattedString(): String {
        val sb = StringBuilder()
        sb.append("[${formattedTime()}] [${level.name}] [${threadName}] [$tag]: $message")
        if (!stackTrace.isNullOrBlank()) {
            sb.append("\n").append(stackTrace)
        }
        return sb.toString()
    }
}

object AppLogger {

    private const val MAX_MEMORY_LOGS = 500
    private const val MAX_LOG_FILE_SIZE = 2 * 1024 * 1024 // 2MB

    private val logList = CopyOnWriteArrayList<LogEntry>()
    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()

    private var logDir: File? = null
    private var logFile: File? = null
    private var crashFile: File? = null

    private val ioScope = CoroutineScope(Dispatchers.IO)
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        try {
            val baseDir = context.filesDir.resolve("logs")
            if (!baseDir.exists()) {
                baseDir.mkdirs()
            }
            logDir = baseDir
            logFile = File(baseDir, "app_logs.log")
            crashFile = File(baseDir, "last_crash.log")

            // Rotate if file is too large
            if (logFile?.exists() == true && (logFile?.length() ?: 0) > MAX_LOG_FILE_SIZE) {
                val backupFile = File(baseDir, "app_logs_old.log")
                if (backupFile.exists()) backupFile.delete()
                logFile?.renameTo(backupFile)
                logFile = File(baseDir, "app_logs.log")
            }

            isInitialized = true
            i("AppLogger", "Logger initialized. App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.APP_VERSION})")
            i("AppLogger", "Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        } catch (e: Exception) {
            Log.e("AppLogger", "Failed to init logger files", e)
        }
    }

    fun d(tag: String, message: String) {
        log(LogLevel.DEBUG, tag, message, null)
    }

    fun i(tag: String, message: String) {
        log(LogLevel.INFO, tag, message, null)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.WARN, tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.ERROR, tag, message, throwable)
    }

    fun crash(thread: Thread, throwable: Throwable) {
        val stackTrace = getStackTraceString(throwable)
        val sanitizedMessage = sanitizeSensitiveInfo("Uncaught Exception in thread [${thread.name}]: ${throwable.message ?: throwable.javaClass.simpleName}")
        val entry = LogEntry(
            level = LogLevel.CRASH,
            tag = "CRASH",
            message = sanitizedMessage,
            stackTrace = stackTrace,
            threadName = thread.name
        )

        // Android logcat
        Log.e("CRASH", entry.toFormattedString(), throwable)

        // Add to memory
        addLogEntry(entry)

        // Synchronously write to files
        writeCrashReportSync(entry, throwable)
    }

    private fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        val sanitizedMsg = sanitizeSensitiveInfo(message)
        val stackTrace = throwable?.let { getStackTraceString(it) }

        // Android logcat
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, sanitizedMsg)
            LogLevel.INFO -> Log.i(tag, sanitizedMsg)
            LogLevel.WARN -> Log.w(tag, sanitizedMsg, throwable)
            LogLevel.ERROR -> Log.e(tag, sanitizedMsg, throwable)
            LogLevel.CRASH -> Log.e(tag, sanitizedMsg, throwable)
        }

        val entry = LogEntry(
            level = level,
            tag = tag,
            message = sanitizedMsg,
            stackTrace = stackTrace,
            threadName = Thread.currentThread().name
        )

        addLogEntry(entry)

        // Write to file asynchronously
        ioScope.launch {
            writeToFile(entry)
        }
    }

    private fun addLogEntry(entry: LogEntry) {
        logList.add(entry)
        while (logList.size > MAX_MEMORY_LOGS) {
            logList.removeAt(0)
        }
        _logsFlow.value = logList.toList()
    }

    private fun writeToFile(entry: LogEntry) {
        val file = logFile ?: return
        try {
            FileWriter(file, true).use { writer ->
                writer.appendLine(entry.toFormattedString())
            }
        } catch (e: Exception) {
            Log.e("AppLogger", "Failed to write log to file", e)
        }
    }

    private fun writeCrashReportSync(entry: LogEntry, throwable: Throwable) {
        val report = buildString {
            appendLine("==================== CRASH REPORT ====================")
            appendLine("Timestamp: ${entry.formattedTime()}")
            appendLine("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.APP_VERSION})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Android OS: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Current Domain: ${try { GlobalSettings.domain.value } catch (_: Exception) { "unknown" }}")
            appendLine("Current Theme: ${try { GlobalSettings.theme.value.name } catch (_: Exception) { "unknown" }}")
            appendLine("Adult Content Enabled: ${try { GlobalSettings.adult.value } catch (_: Exception) { "unknown" }}")
            val runtime = Runtime.getRuntime()
            val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
            val maxMem = runtime.maxMemory() / 1024 / 1024
            appendLine("Memory Usage: ${usedMem}MB / ${maxMem}MB")
            appendLine("Thread: ${entry.threadName}")
            appendLine("Exception: ${throwable.javaClass.name}: ${throwable.message}")
            appendLine("----------------- STACK TRACE -----------------")
            appendLine(entry.stackTrace ?: "No stack trace available")
            appendLine("---------------- RECENT ACTIVITY ----------------")
            val recentLogs = logList.takeLast(30)
            for (recent in recentLogs) {
                appendLine(recent.toFormattedString())
            }
            appendLine("======================================================")
        }

        try {
            // Write to crash file
            crashFile?.let { file ->
                FileWriter(file, false).use { writer ->
                    writer.write(report)
                }
            }
            // Append to general log file
            logFile?.let { file ->
                FileWriter(file, true).use { writer ->
                    writer.appendLine(report)
                }
            }
        } catch (e: Exception) {
            Log.e("AppLogger", "Failed to write sync crash report", e)
        }
    }

    fun clearLogs() {
        logList.clear()
        _logsFlow.value = emptyList()
        ioScope.launch {
            try {
                logFile?.delete()
                logFile?.createNewFile()
                crashFile?.delete()
            } catch (e: Exception) {
                Log.e("AppLogger", "Failed to clear log files", e)
            }
        }
    }

    fun getLastCrashReport(): String? {
        return try {
            if (crashFile?.exists() == true && (crashFile?.length() ?: 0) > 0) {
                crashFile?.readText()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun exportLogsText(): String {
        return buildString {
            appendLine("=== Esjzone System Logs Export ===")
            appendLine("Export Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine("App Version: ${BuildConfig.VERSION_NAME}-${BuildConfig.APP_VERSION}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Domain: ${try { GlobalSettings.domain.value } catch (_: Exception) { "unknown" }}")
            appendLine("Total Entries: ${logList.size}")
            appendLine("--------------------------------------------------")
            for (entry in logList) {
                appendLine(entry.toFormattedString())
            }
            appendLine("==================================================")
        }
    }

    private fun getStackTraceString(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        pw.flush()
        return sanitizeSensitiveInfo(sw.toString())
    }

    /**
     * Sanitizes credentials according to security constraints (Rule 4: Never log real passwords, ews_key, ews_token).
     */
    private fun sanitizeSensitiveInfo(input: String): String {
        var result = input
        result = result.replace(Regex("ews_key=([^&;\\s,]+)", RegexOption.IGNORE_CASE), "ews_key=***")
        result = result.replace(Regex("ews_token=([^&;\\s,]+)", RegexOption.IGNORE_CASE), "ews_token=***")
        result = result.replace(Regex("pwd=([^&;\\s,]+)", RegexOption.IGNORE_CASE), "pwd=***")
        result = result.replace(Regex("password=([^&;\\s,]+)", RegexOption.IGNORE_CASE), "password=***")
        return result
    }
}
