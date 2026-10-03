package com.breakyuna.esjzone

import com.breakyuna.esjzone.backup.AutoBackup
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class AutoBackupTest {
    @Test fun rotationKeepsThreeNewestCompletedArchivesAndNeverDeletesOtherFiles() {
        val archives = (1..5).map { File("esjzone-auto-170000000000$it.zip") }
        val files = archives.reversed() + File("pending.zip.part") + File("manual.zip")
        assertEquals(archives.take(2).toSet(), AutoBackup.obsoleteArchives(files).toSet())
        assertTrue(AutoBackup.obsoleteArchives(archives.take(3)).isEmpty())
    }
}
