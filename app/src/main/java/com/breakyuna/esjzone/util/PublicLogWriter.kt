package com.breakyuna.esjzone.util

import com.breakyuna.esjzone.storage.StorageFile
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/** Used only on AppLogger's independent public-log thread. Receives already redacted records. */
internal class PublicLogWriter(
    private val maxBytes: Int = 2 * 1024 * 1024,
    private val maxPendingRecords: Int = 500,
    private val sessionTime: String = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.US).format(Date()),
    private val scheduleFlush: (() -> Unit)? = null
) {
    private var directory: StorageFile? = null
    private var directoryReady = false
    private val pending = ArrayDeque<String>()
    private var pendingBytes = 0
    private var pendingCrash: String? = null
    private var activeName: String? = null
    private var activeFile: StorageFile? = null
    private var activeFileBytes = -1L
    private var part = 1

    fun attach(directory: StorageFile?) {
        this.directory = directory
        directoryReady = false
        activeName = null
        activeFile = null
        activeFileBytes = -1L
        part = 1
        flush()
    }

    fun append(text: String) {
        val record = boundedUtf8LogRecord(text, maxBytes - 1) + "\n"
        pending.addLast(record)
        pendingBytes += record.toByteArray(Charsets.UTF_8).size
        while (pending.size > maxPendingRecords || pendingBytes > maxBytes) {
            pendingBytes -= pending.removeFirst().toByteArray(Charsets.UTF_8).size
        }
        if (scheduleFlush == null || pending.size >= BATCH_RECORDS || pendingBytes >= BATCH_BYTES) {
            flush()
        } else if (directory != null) {
            scheduleFlush.invoke()
        }
    }

    fun writeCrashReport(text: String) {
        val safeReport = boundedUtf8LogRecord(text, maxBytes - 1)
        pendingCrash = safeReport
        val record = "$safeReport\n"
        pending.addLast(record)
        pendingBytes += record.toByteArray(Charsets.UTF_8).size
        while (pending.size > maxPendingRecords || pendingBytes > maxBytes) {
            pendingBytes -= pending.removeFirst().toByteArray(Charsets.UTF_8).size
        }
        flush()
    }

    fun clear() {
        pending.clear()
        pendingBytes = 0
        pendingCrash = null
        directoryReady = false
        activeName = null
        activeFile = null
        activeFileBytes = -1L
        part = 1
        val target = directory ?: return
        if (!target.exists()) return
        for (entry in target.entries()) {
            val name = entry.file.name
            if (!entry.directory && (SESSION_LOG.matches(name) || LEGACY_LOG.matches(name) ||
                    name in listOf("app_logs.log", "app_logs_old.log", "last_crash.log"))) {
                if (!entry.file.delete()) throw IOException("Unable to clear public log")
            }
        }
    }

    fun flush() {
        val target = directory ?: return
        try {
            if (!directoryReady) {
                if (!target.mkdirs()) throw IOException("Unable to create public log directory")
                directoryReady = true
            }
            if (pendingCrash == null && pending.isEmpty()) return
            pendingCrash?.let { report ->
                StorageFile(target, "last_crash.log").writeText(report)
                pendingCrash = null
            }
            while (pending.isNotEmpty()) {
                var file = activeFile
                if (file == null) {
                    val name = activeName ?: nextName(target).also {
                        activeName = it
                        activeFileBytes = 0L
                    }
                    file = StorageFile(target, name)
                    if (activeFileBytes < 0L) activeFileBytes = file.length()
                    activeFile = file
                }
                val first = pending.first()
                val firstSize = first.toByteArray(Charsets.UTF_8).size
                if (activeFileBytes > 0L && activeFileBytes + firstSize > maxBytes) {
                    part++
                    activeName = nextName(target)
                    file = StorageFile(target, requireNotNull(activeName))
                    activeFile = file
                    activeFileBytes = 0L
                }
                val batch = StringBuilder()
                var batchBytes = 0
                var batchCount = 0
                for (next in pending) {
                    val nextSize = next.toByteArray(Charsets.UTF_8).size
                    if (batchCount > 0 && activeFileBytes + batchBytes + nextSize > maxBytes) break
                    batch.append(next)
                    batchBytes += nextSize
                    batchCount++
                }
                file.appendText(batch.toString())
                activeFileBytes += batchBytes
                repeat(batchCount) { pending.removeFirst() }
                pendingBytes -= batchBytes
            }
        } catch (error: Throwable) {
            directoryReady = false
            activeFile = null
            activeFileBytes = -1L
            throw error
        }
    }

    private fun nextName(target: StorageFile): String {
        while (true) {
            val suffix = if (part == 1) "" else "-part$part"
            val name = "app_${sessionTime}${suffix}.txt"
            if (!StorageFile(target, name).exists()) return name
            part++
        }
    }

    private companion object {
        const val BATCH_RECORDS = 16
        const val BATCH_BYTES = 8 * 1024
        val SESSION_LOG = Regex("app_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}-\\d{3}(?:-part\\d+)?\\.txt")
        val LEGACY_LOG = Regex("app_logs\\.log(?: \\(\\d+\\))?\\.txt")
    }
}
