package com.breakyuna.esjzone.backup

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object BackupFileNames {
    private val timestamp = DateTimeFormatter.ofPattern("uuuu-MM-dd_HH-mm-ss-SSS", Locale.ROOT)

    fun manual(time: Long = System.currentTimeMillis()): String = "esjzone-backup-${stamp(time)}.zip"
    fun automatic(time: Long): String = "esjzone-auto-${stamp(time)}.zip"

    private fun stamp(time: Long): String = timestamp.format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()))

    fun automaticTime(name: String): Long? {
        if (!name.startsWith("esjzone-auto-") || !name.endsWith(".zip")) return null
        val value = name.removePrefix("esjzone-auto-").removeSuffix(".zip")
        // Existing archives remain available for migration, restore and rotation.
        if (value.all(Char::isDigit)) return value.toLongOrNull()
        return runCatching {
            LocalDateTime.parse(value, timestamp).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
    }
}
