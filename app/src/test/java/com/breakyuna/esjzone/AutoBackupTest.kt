package com.breakyuna.esjzone

import com.breakyuna.esjzone.backup.AutoBackup
import java.io.File
import java.nio.file.Files
import com.breakyuna.esjzone.storage.StorageFile
import org.junit.Assert.*
import org.junit.Test

class AutoBackupTest {
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
