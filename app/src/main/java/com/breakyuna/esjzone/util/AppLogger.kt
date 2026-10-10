package com.breakyuna.esjzone.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.breakyuna.esjzone.BuildConfig
import com.breakyuna.esjzone.EsjzoneApplication
import com.breakyuna.esjzone.data.settings.SettingsDefaults
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
import java.util.concurrent.atomic.AtomicLong

enum class LogLevel { DEBUG, INFO, WARN, ERROR, CRASH }

private val logSequence = AtomicLong(0L)

data class LogEntry(
    val id: Long = logSequence.incrementAndGet(),
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String,
    val stackTrace: String? = null,
    val threadName: String = Thread.currentThread().name,
    val source: LogSource = logSourceForTag(tag)
) {
    fun formattedTime(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
    fun toFormattedString(): String = buildString {
        append("[${formattedTime()}] [${level.name}] [${source.label}] [$threadName] [$tag]: $message")
        if (!stackTrace.isNullOrBlank()) append('\n').append(stackTrace)
    }
}

object AppLogger {
    private const val MAX_MEMORY_LOGS = 500
    private const val MAX_LOG_FILE_SIZE = 2L * 1024 * 1024
    private const val DEBUG_MEMORY_LOGS = 2_000
    private const val DEBUG_LOG_FILE_SIZE = 8L * 1024 * 1024
    private const val ARCHIVE_COUNT = 3
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
    @Volatile var debugEnabled: Boolean = false
        private set
    private val diagnosticSequence = AtomicLong()
    private val traceContext = ThreadLocal<String?>()
    private val processId = java.util.UUID.randomUUID().toString().take(8)

    fun newTraceId(kind: String): String = "$processId-$kind-${diagnosticSequence.incrementAndGet()}"
    fun currentTraceId(): String? = traceContext.get()

    internal fun <T> withTrace(id: String, block: () -> T): T {
        val previous = traceContext.get()
        traceContext.set(id)
        return try { block() } finally { traceContext.set(previous) }
    }

    fun setDebugMode(enabled: Boolean) {
        if (debugEnabled == enabled) return
        debugEnabled = enabled
        val webViewVersion = runCatching { android.webkit.WebView.getCurrentWebViewPackage()?.versionName }
            .getOrNull() ?: "unavailable"
        i("AppLogger", "Debug logging ${if (enabled) "enabled" else "disabled"}; process=$processId, " +
            "commit=${BuildConfig.COMMIT_ID}, build=${BuildConfig.BUILD_DATE}, " +
            "device=${Build.MANUFACTURER} ${Build.MODEL}, api=${Build.VERSION.SDK_INT}, " +
            "webview=$webViewVersion")
    }

    /** Lazy, opt-in diagnostics; failures in diagnostic formatting cannot change app behavior. */
    fun trace(tag: String, source: LogSource = logSourceForTag(tag), message: () -> String) {
        if (!debugEnabled) return
        val text = try { message() } catch (error: Exception) {
            "Diagnostic formatting failed: type=${error.javaClass.simpleName}"
        }
        d(tag, "trace=${currentTraceId() ?: "none"}, $text", source)
    }

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

    fun d(tag: String, message: String, source: LogSource = logSourceForTag(tag)) = log(LogLevel.DEBUG, tag, message, null, source)
    fun i(tag: String, message: String, source: LogSource = logSourceForTag(tag)) = log(LogLevel.INFO, tag, message, null, source)
    fun w(tag: String, message: String, throwable: Throwable? = null, source: LogSource = logSourceForTag(tag)) = log(LogLevel.WARN, tag, message, throwable, source)
    fun e(tag: String, message: String, throwable: Throwable? = null, source: LogSource = logSourceForTag(tag)) = log(LogLevel.ERROR, tag, message, throwable, source)

    private fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?, source: LogSource) {
        val entry = LogEntry(level = level, tag = tag, message = boundedUtf8LogRecord(sanitize(message), 32 * 1024),
            stackTrace = throwable?.let { boundedUtf8LogRecord(stackTrace(it), 64 * 1024) }, source = source)
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
            recent = logList.takeLast(if (debugEnabled) 200 else 30)
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
        runCatching {
            crashFile?.takeIf { it.isFile && it.length() > 0 }?.readText()?.let(::sanitize)
        }.getOrNull()
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

    /** Clipboard-sized current window. Full retained history is exported as a file. */
    fun exportLogsText(): String = synchronized(stateLock) {
        buildString {
            appendLine("=== Esjzone System Logs Export ===")
            appendLine("Export Time: ${Date()}")
            appendLine("App Version: ${BuildConfig.VERSION_NAME}-${BuildConfig.APP_VERSION}")
            appendLine("Commit: ${BuildConfig.COMMIT_ID}; Build: ${BuildConfig.BUILD_DATE}; Process: $processId; Debug: $debugEnabled")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Domain: ${currentDomain()}")
            appendLine("Adult Content Enabled: ${currentAdultContentEnabled()}")
            appendLine("Total Entries: ${logList.size}")
            appendLine("Clipboard window, newest first. Share logs to export the full retained history.")
            var characters = 0
            for (entry in logList.asReversed()) {
                val record = boundedUtf8LogRecord(entry.toFormattedString(), 16 * 1024)
                if (characters + record.length > 48 * 1024) break
                appendLine(record)
                characters += record.length
            }
        }.let(::sanitize).let { boundedUtf8LogRecord(it, 192 * 1024) }
    }

    /** Called on IO. Queuing behind the writer includes all previously accepted records. */
    fun exportLogsFile(cacheDir: File): File = fileExecutor.submit<File> {
        synchronized(fileLock) {
            val directory = File(cacheDir, "log_exports").apply { mkdirs() }
            directory.listFiles()?.filter { it.name.startsWith("esjzone-logs-") }
                ?.sortedByDescending(File::lastModified)?.drop(1)?.forEach(File::delete)
            val output = File(directory, "esjzone-logs-${System.currentTimeMillis()}.txt")
            output.bufferedWriter().use { writer ->
                writer.appendLine("=== Esjzone retained diagnostics ===")
                writer.appendLine("Export: ${Date()}; Version: ${BuildConfig.VERSION_NAME}-${BuildConfig.APP_VERSION}")
                writer.appendLine("Commit: ${BuildConfig.COMMIT_ID}; Build: ${BuildConfig.BUILD_DATE}; Debug: $debugEnabled")
                writer.appendLine("Oldest retained file first. Files rotate; this is not unlimited history.")
                val current = logFile
                if (current != null) {
                    ((ARCHIVE_COUNT downTo 1).map { archiveFile(current, it) } + current + listOfNotNull(crashFile)).filter(File::exists)
                        .forEach { file ->
                            writer.appendLine("--- ${file.name} ---")
                            // Records were sanitized before persistence; stream without loading the archive into RAM.
                            file.bufferedReader().useLines { lines -> lines.forEach { writer.appendLine(sanitize(it)) } }
                        }
                }
            }
            output
        }
    }.get()

    private fun synchronizedGeneration(): Long = synchronized(stateLock) { generation }
    private fun addLogEntryLocked(entry: LogEntry) {
        logList.add(entry)
        while (logList.size > if (debugEnabled) DEBUG_MEMORY_LOGS else MAX_MEMORY_LOGS) logList.removeAt(0)
        _logsFlow.value = logList.toList()
    }
    private fun append(text: String) = synchronized(fileLock) {
        try {
            val file = logFile ?: return@synchronized
            val safeText = boundedUtf8LogRecord(text, logFileLimit().toInt() - 1)
            rotateIfNeeded(file, safeText.toByteArray(StandardCharsets.UTF_8).size.toLong() + 1)
            file.appendText(safeText + "\n")
        } catch (e: Exception) { Log.e("AppLogger", "Failed to write log: ${sanitize(e.message.orEmpty())}") }
    }
    private fun clearFiles() = synchronized(fileLock) {
        try {
            logFile?.writeText("")
            logFile?.let { file -> (1..ARCHIVE_COUNT).forEach { archiveFile(file, it).delete() } }
            crashFile?.delete()
        }
        catch (e: Exception) { Log.e("AppLogger", "Failed to clear logs: ${sanitize(e.message.orEmpty())}") }
    }
    private fun rotateIfNeeded(file: File, incomingBytes: Long) {
        if (file.length() + incomingBytes <= logFileLimit()) return
        archiveFile(file, ARCHIVE_COUNT).delete()
        for (index in ARCHIVE_COUNT - 1 downTo 1) {
            val previous = archiveFile(file, index)
            if (previous.exists()) check(previous.renameTo(archiveFile(file, index + 1))) { "Log archive rotation failed" }
        }
        check(file.renameTo(archiveFile(file, 1))) { "Log rotation failed" }
        file.createNewFile()
    }
    private fun logFileLimit(): Long = if (debugEnabled) DEBUG_LOG_FILE_SIZE else MAX_LOG_FILE_SIZE
    private fun archiveFile(file: File, index: Int): File = File(file.parentFile,
        if (index == 1) "app_logs_old.log" else "app_logs_old_$index.log")
    private fun writeCrashReport(entry: LogEntry, recent: List<LogEntry>) = try {
        val report = buildString {
            appendLine("==================== CRASH REPORT ====================")
            appendLine("Timestamp: ${entry.formattedTime()}")
            appendLine("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.APP_VERSION})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Android OS: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Current Domain: ${currentDomain()}")
            appendLine("Current Theme: Monochrome/System")
            appendLine("Adult Content Enabled: ${currentAdultContentEnabled()}")
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
        val safeReport = boundedUtf8LogRecord(report, logFileLimit().toInt() - 1)
        crashFile?.writeText(safeReport)
        logFile?.let { rotateIfNeeded(it, safeReport.toByteArray(StandardCharsets.UTF_8).size.toLong() + 1); it.appendText(safeReport + "\n") }
        _crashReportFlow.value = safeReport
    } catch (e: Exception) { Log.e("AppLogger", "Failed to write crash report: ${sanitize(e.message.orEmpty())}") }

    private fun currentDomain(): String = runCatching {
        EsjzoneApplication.instance.container.settings.domain.value
    }.getOrDefault(SettingsDefaults.DOMAINS.first())

    private fun currentAdultContentEnabled(): Boolean = runCatching {
        EsjzoneApplication.instance.container.settings.adult.value
    }.getOrDefault(true)

    private fun stackTrace(throwable: Throwable): String {
        val writer = StringWriter(); throwable.printStackTrace(PrintWriter(writer)); return sanitize(writer.toString())
    }
    private fun safeThrowable(throwable: Throwable): String = stackTrace(throwable)
    private fun sanitize(input: String): String {
        var sanitized = input
            // Header values are untrusted and may contain several cookies/tokens.
            .replace(AUTHORIZATION_HEADER_PATTERN) { "${it.groupValues[1]}***" }
            .replace(COOKIE_HEADER_PATTERN) { "${it.groupValues[1]}***" }
            // Also cover JSON/form fields whose quoted values may contain spaces.
            .replace(STRUCTURED_SECRET_PATTERN) { match ->
                val value = match.groupValues[2]
                val replacement = if (value.length >= 2 &&
                    value.first() == value.last() &&
                    (value.first() == '"' || value.first() == '\'')
                ) {
                    "${value.first()}***${value.last()}"
                } else {
                    "***"
                }
                "${match.groupValues[1]}$replacement"
            }
            .replace(STRUCTURED_HEADER_PATTERN) { match ->
                "${match.groupValues[1]}***"
            }
            // Finally catch URL-encoded/query and ordinary key=value forms.
            .replace(PLAIN_SECRET_PATTERN) { "${it.groupValues[1]}***" }

        sanitized = sanitized.replace(EMAIL_PATTERN, "<redacted-email>")
        sanitized = sanitized.replace(URL_PATTERN) { match ->
            // Keep log/sentence delimiters outside the URL so a second sanitization
            // does not turn an already-safe /login.php, into an unknown path.
            val url = match.value.trimEnd(',', ';', ')', ']', '}', '.')
            diagnosticUrl(url) + match.value.substring(url.length)
        }
        return sanitized
    }

    private val AUTHORIZATION_HEADER_PATTERN = Regex(
        "(?im)(\\bAuthorization\\s*:\\s*)(?:Bearer\\s+)?[^\\r\\n,;]+"
    )
    private val COOKIE_HEADER_PATTERN = Regex(
        "(?im)(\\b(?:Set-)?Cookie\\s*:\\s*)[^\\r\\n]+"
    )
    private val STRUCTURED_SECRET_PATTERN = Regex(
        "(?i)([\\\"']?(?:ews_key|ews_token|password|passwd|pwd|email|username|token|access_token|refresh_token|api_key|apikey|secret|sessionid)[\\\"']?\\s*[:=]\\s*)(\\\"(?:\\\\.|[^\\\"])*\\\"|'(?:\\\\.|[^'])*'|[^,}\\]\\s&;]+)"
    )
    private val PLAIN_SECRET_PATTERN = Regex(
        "(?i)(\\b(?:ews_key|ews_token|password|passwd|pwd|email|username|token|access_token|refresh_token|api_key|apikey|secret|sessionid)\\b\\s*[=:]\\s*)([^&;\\s,}\\]]+)"
    )
    private val STRUCTURED_HEADER_PATTERN = Regex(
        """(?i)([\"']?(?:authorization|cookie|set-cookie)[\"']?\s*[:=]\s*)(\"[^\"]*\"|'[^']*'|[^,}\]\s]+)"""
    )
    private val EMAIL_PATTERN = Regex(
        "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
        RegexOption.IGNORE_CASE
    )
    private val URL_PATTERN = Regex("https?://[^\\s<>\\\"']+", RegexOption.IGNORE_CASE)

    fun sanitizeForDisplay(input: String): String = sanitize(input)
}
