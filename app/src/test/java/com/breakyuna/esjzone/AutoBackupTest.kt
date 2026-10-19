package com.breakyuna.esjzone

import com.breakyuna.esjzone.backup.AutoBackup
import com.breakyuna.esjzone.backup.BackupFileNames
import java.io.File
import java.nio.file.Files
import com.breakyuna.esjzone.storage.StorageFile
import org.junit.Assert.*
import org.junit.Test

class AutoBackupTest {
    @Test fun intervalDefaultsToTwelveHoursAndRejectsInvalidPersistedValues() {
        assertEquals(12, AutoBackup.normalizedInterval(null))
        assertEquals(12, AutoBackup.normalizedInterval(-1))
        assertEquals(12, AutoBackup.normalizedInterval(0))
        assertEquals(24, AutoBackup.normalizedInterval(24))
    }

    @Test fun readableNamesRoundTripAndMixedArchivesRotateByTime() {
        val time = java.time.LocalDateTime.of(2026, 10, 19, 20, 30, 0, 123_000_000)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals("esjzone-backup-2026-10-19_20-30-00-123.zip", BackupFileNames.manual(time))
        assertEquals(time, requireNotNull(BackupFileNames.automaticTime(BackupFileNames.automatic(time))))
        assertEquals(time, requireNotNull(BackupFileNames.automaticTime("esjzone-auto-$time.zip")))
        assertNull(BackupFileNames.automaticTime("pending.zip.part"))
        assertNull(BackupFileNames.automaticTime("esjzone-auto-invalid.zip"))
        val oldest = StorageFile(File("esjzone-auto-${time - 1000}.zip"))
        val newer = (0..2).map { StorageFile(File(BackupFileNames.automatic(time + it * 1000))) }
        assertEquals(listOf(oldest), AutoBackup.obsoleteArchives(newer + oldest))
    }

    @Test fun rotationKeepsThreeNewestCompletedArchivesAndNeverDeletesOtherFiles() {
        val archives = (1..5).map { StorageFile(File("esjzone-auto-170000000000$it.zip")) }
        val files = archives.reversed() + StorageFile(File("pending.zip.part")) + StorageFile(File("manual.zip"))
        assertEquals(archives.take(2).toSet(), AutoBackup.obsoleteArchives(files).toSet())
        assertTrue(AutoBackup.obsoleteArchives(archives.take(3)).isEmpty())
    }

    @Test fun failedArchiveCleanupKeepsThePublishedBackupAndContinuesRemovingOtherOldCopies() {
        val directory = Files.createTempDirectory("backup-cleanup-").toFile()
        try {
            val archives = (1..5).map { File(directory, "esjzone-auto-170000000000$it.zip") }
            // A nonempty directory makes File.delete() fail without depending on device permissions.
            check(archives.first().mkdir())
            File(archives.first(), "fixture").writeText("keep")
            archives.drop(1).forEach { it.writeText("archive") }
            val other = File(directory, "manual.zip").apply { writeText("manual") }

            assertFalse(AutoBackup.pruneArchives((archives + other).map(::StorageFile)))
            assertTrue(archives.last().isFile)
            assertTrue(archives.first().exists())
            assertFalse(archives[1].exists())
            assertTrue(archives.takeLast(3).all(File::isFile))
            assertEquals("manual", other.readText())
        } finally { directory.deleteRecursively() }
    }
}
