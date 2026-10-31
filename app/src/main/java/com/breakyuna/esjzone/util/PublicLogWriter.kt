package com.breakyuna.esjzone.util

import com.breakyuna.esjzone.storage.StorageFile
import java.io.IOException
import java.util.ArrayDeque

/** Used only on AppLogger's independent public-log thread. Receives already redacted records. */
internal class PublicLogWriter(
    private val maxBytes: Int = 2 * 1024 * 1024,
    private val maxPendingRecords: Int = 500
) {
    private var directory: StorageFile? = null
    private val pending = ArrayDeque<String>()
    private var pendingBytes = 0
    private var pendingCrash: String? = null

    fun attach(directory: StorageFile?) {
        this.directory = directory
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
        for (name in listOf("app_logs.log", "app_logs_old.log", "last_crash.log")) {
            val file = StorageFile(target, name)
            if (file.exists() && !file.delete()) throw IOException("Unable to clear public log")
        }
    }

    private fun flush() {
        val target = directory ?: return
        if (!target.mkdirs()) throw IOException("Unable to create public log directory")
        pendingCrash?.let { report ->
            StorageFile(target, "last_crash.log").writeText(report)
            pendingCrash = null
        }
        val file = StorageFile(target, "app_logs.log")
        while (pending.isNotEmpty()) {
            val record = pending.first()
            val size = record.toByteArray(Charsets.UTF_8).size
            if (file.length() + size > maxBytes) {
                val old = StorageFile(target, "app_logs_old.log")
                if (old.exists() && !old.delete()) throw IOException("Unable to rotate public log")
                if (!file.renameTo(old)) throw IOException("Unable to rotate public log")
            }
            file.appendText(record)
            pending.removeFirst()
            pendingBytes -= size
        }
    }
}
