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
    private val sessionTime: String = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.US).format(Date())
) {
    private var directory: StorageFile? = null
    private val pending = ArrayDeque<String>()
    private var pendingBytes = 0
    private var pendingCrash: String? = null
    private var activeName: String? = null
    private var part = 1

    fun attach(directory: StorageFile?) {
        this.directory = directory
        activeName = null
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
        flush()
    }

    fun writeCrashReport(text: String) {
        pendingCrash = boundedUtf8LogRecord(text, maxBytes - 1)
        append(requireNotNull(pendingCrash))
    }

    fun clear() {
        pending.clear()
        pendingBytes = 0
        pendingCrash = null
        val target = directory ?: return
        if (!target.exists()) return
        for (entry in target.entries()) {
            val name = entry.file.name
            if (!entry.directory && (SESSION_LOG.matches(name) || LEGACY_LOG.matches(name) ||
                    name in listOf("app_logs.log", "app_logs_old.log", "last_crash.log"))) {
                if (!entry.file.delete()) throw IOException("Unable to clear public log")
            }
        }
        activeName = null
        part = 1
    }

    private fun flush() {
        val target = directory ?: return
        if (!target.mkdirs()) throw IOException("Unable to create public log directory")
        pendingCrash?.let { report ->
            StorageFile(target, "last_crash.log").writeText(report)
            pendingCrash = null
        }
        while (pending.isNotEmpty()) {
            val record = pending.first()
            val size = record.toByteArray(Charsets.UTF_8).size
            var file = StorageFile(target, activeName ?: nextName(target).also { activeName = it })
            if (file.length() + size > maxBytes) {
                part++
                activeName = nextName(target)
                file = StorageFile(target, requireNotNull(activeName))
            }
            file.appendText(record)
            pending.removeFirst()
            pendingBytes -= size
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
        val SESSION_LOG = Regex("app_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}-\\d{3}(?:-part\\d+)?\\.txt")
        val LEGACY_LOG = Regex("app_logs\\.log(?: \\(\\d+\\))?\\.txt")
    }
}
