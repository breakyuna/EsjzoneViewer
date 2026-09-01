package com.breakyuna.esjzone.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.breakyuna.esjzone.BuildConfig
import com.breakyuna.esjzone.GlobalSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

enum class LogLevel { DEBUG, INFO, WARN, ERROR, CRASH }

data class LogEntry(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String,
    val stackTrace: String? = null,
    val threadName: String = Thread.currentThread().name
) {
    fun formattedTime(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
    fun toFormattedString(): String = buildString {
        append("[${formattedTime()}] [${level.name}] [$threadName] [$tag]: $message")
        if (!stackTrace.isNullOrBlank()) append('\n').append(stackTrace)
    }
}

object AppLogger {
    private const val MAX_MEMORY_LOGS = 500
    private const val MAX_LOG_FILE_SIZE = 2L * 1024 * 1024
    private val stateLock = Any()
    private val fileLock = Any()
    private val fileExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "app-log-writer") }
    private val logList = mutableListOf<LogEntry>()
    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    private val _crashReportFlow = MutableStateFlow<String?>(null)
    val logsFlow = _logsFlow.asStateFlow()
    val crashReportFlow = _crashReportFlow.asStateFlow()
    private var logFile: File? = null
    private var crashFile: File? = null
    private var generation = 0L
    @Volatile private var initialized = false

    fun init(context: Context) = synchronized(fileLock) {
        if (initialized) return@synchronized
        try {
            val directory = context.applicationContext.filesDir.resolve("logs").also { it.mkdirs() }
            logFile = File(directory, "app_logs.log")
            crashFile = File(directory, "last_crash.log")
            initialized = true
            i("AppLogger", "Logger initialized. App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.APP_VERSION})")
        } catch (e: Exception) { Log.e("AppLogger", "Failed to init logger files: ${safeThrowable(e)}") }
    }

    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message, null)
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message, null)
    fun w(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.WARN, tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.ERROR, tag, message, throwable)

    private fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        val entry = LogEntry(level = level, tag = tag, message = sanitize(message), stackTrace = throwable?.let(::stackTrace))
        val output = entry.toFormattedString()
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, output)
            LogLevel.INFO -> Log.i(tag, output)
            LogLevel.WARN -> Log.w(tag, output)
            LogLevel.ERROR, LogLevel.CRASH -> Log.e(tag, output)
        }
        synchronized(stateLock) {
            addLogEntryLocked(entry)
            val commandGeneration = generation
            fileExecutor.execute { if (commandGeneration == synchronizedGeneration()) append(output) }
        }
    }

    fun crash(thread: Thread, throwable: Throwable) {
        val entry = LogEntry(
            level = LogLevel.CRASH,
            tag = "CRASH",
            message = sanitize("Uncaught Exception in thread [${thread.name}]: ${throwable.message ?: throwable.javaClass.simpleName}"),
            stackTrace = stackTrace(throwable),
            threadName = thread.name
        )
        Log.e("CRASH", entry.toFormattedString())
        val recent: List<LogEntry>
        synchronized(stateLock) {
            addLogEntryLocked(entry)
            recent = logList.takeLast(30)
        }
        synchronized(fileLock) { writeCrashReport(entry, recent) }
    }

    fun clearLogs() {
        synchronized(stateLock) {
            generation += 1
            logList.clear()
            _logsFlow.value = emptyList()
            _crashReportFlow.value = null
            val clearGeneration = generation
            fileExecutor.execute { if (clearGeneration == synchronizedGeneration()) clearFiles() }
        }
    }

    fun getLastCrashReport(): String? = synchronized(fileLock) {
        runCatching { crashFile?.takeIf { it.isFile && it.length() > 0 }?.readText() }.getOrNull()
    }
    fun refreshCrashReport() = synchronized(stateLock) {
        val requestGeneration = generation
        fileExecutor.execute {
            val report = getLastCrashReport()
            synchronized(stateLock) {
                if (requestGeneration == generation) _crashReportFlow.value = report
            }
        }
    }

    fun exportLogsText(): String = synchronized(stateLock) {
        buildString {
            appendLine("=== Esjzone System Logs Export ===")
            appendLine("Export Time: ${Date()}")
            appendLine("App Version: ${BuildConfig.VERSION_NAME}-${BuildConfig.APP_VERSION}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Domain: ${runCatching { GlobalSettings.domain.value }.getOrDefault("unknown")}")
            appendLine("Adult Content Enabled: ${runCatching { GlobalSettings.adult.value }.getOrDefault("unknown")}")
            appendLine("Total Entries: ${logList.size}")
            logList.forEach { appendLine(it.toFormattedString()) }
        }
    }

    private fun synchronizedGeneration(): Long = synchronized(stateLock) { generation }
    private fun addLogEntryLocked(entry: LogEntry) {
        logList.add(entry)
        while (logList.size > MAX_MEMORY_LOGS) logList.removeAt(0)
        _logsFlow.value = logList.toList()
    }
    private fun append(text: String) = synchronized(fileLock) {
        try {
            val file = logFile ?: return@synchronized
            val safeText = boundedUtf8LogRecord(text, MAX_LOG_FILE_SIZE.toInt() - 1)
            rotateIfNeeded(file, safeText.toByteArray(StandardCharsets.UTF_8).size.toLong() + 1)
            file.appendText(safeText + "\n")
        } catch (e: Exception) { Log.e("AppLogger", "Failed to write log: ${sanitize(e.message.orEmpty())}") }
    }
    private fun clearFiles() = synchronized(fileLock) {
        try {
            logFile?.writeText("")
            logFile?.parentFile?.resolve("app_logs_old.log")?.delete()
            crashFile?.delete()
        }
        catch (e: Exception) { Log.e("AppLogger", "Failed to clear logs: ${sanitize(e.message.orEmpty())}") }
    }
    private fun rotateIfNeeded(file: File, incomingBytes: Long) {
        if (file.length() + incomingBytes <= MAX_LOG_FILE_SIZE) return
        val old = File(file.parentFile, "app_logs_old.log")
        old.delete(); file.renameTo(old); file.createNewFile()
    }
    private fun writeCrashReport(entry: LogEntry, recent: List<LogEntry>) = try {
        val report = buildString {
            appendLine("==================== CRASH REPORT ====================")
            appendLine("Timestamp: ${entry.formattedTime()}")
            appendLine("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.APP_VERSION})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Android OS: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Current Domain: ${runCatching { GlobalSettings.domain.value }.getOrDefault("unknown")}")
            appendLine("Current Theme: ${runCatching { GlobalSettings.theme.value.name }.getOrDefault("unknown")}")
            appendLine("Adult Content Enabled: ${runCatching { GlobalSettings.adult.value }.getOrDefault("unknown")}")
            val runtime = Runtime.getRuntime()
            appendLine("Memory Usage: ${(runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024}MB / ${runtime.maxMemory() / 1024 / 1024}MB")
            appendLine("Thread: ${entry.threadName}")
            appendLine("Exception: ${entry.message}")
            appendLine("----------------- STACK TRACE -----------------")
            appendLine(entry.stackTrace ?: "No stack trace available")
            appendLine("---------------- RECENT ACTIVITY ----------------")
            recent.forEach { appendLine(it.toFormattedString()) }
            appendLine("======================================================")
        }
        val safeReport = boundedUtf8LogRecord(report, MAX_LOG_FILE_SIZE.toInt() - 1)
        crashFile?.writeText(safeReport)
        logFile?.let { rotateIfNeeded(it, safeReport.toByteArray(StandardCharsets.UTF_8).size.toLong() + 1); it.appendText(safeReport + "\n") }
        _crashReportFlow.value = safeReport
    } catch (e: Exception) { Log.e("AppLogger", "Failed to write crash report: ${sanitize(e.message.orEmpty())}") }

    private fun stackTrace(throwable: Throwable): String {
        val writer = StringWriter(); throwable.printStackTrace(PrintWriter(writer)); return sanitize(writer.toString())
    }
    private fun safeThrowable(throwable: Throwable): String = stackTrace(throwable)
    private fun sanitize(input: String): String = input.replace(
        Regex("(?i)(ews_key|ews_token|password|pwd)([=:])([^&;\\s,]+)"), "$1$2***"
    )
    fun sanitizeForDisplay(input: String): String = sanitize(input)
}
