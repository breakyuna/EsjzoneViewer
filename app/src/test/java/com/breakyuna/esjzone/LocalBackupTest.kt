package com.breakyuna.esjzone

import com.breakyuna.esjzone.backup.LocalBackup
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalBackupTest {
    @Test fun backupPathsCannotEscapeOrOverwriteAnotherCategory() {
        val root = File("backup-test-root")
        listOf("../outside", "downloads/../../outside", "downloads/../backup.json", "/absolute", "settings.json", "downloads/a\\b").forEach { path ->
            assertThrows(IllegalArgumentException::class.java) { LocalBackup.safeEntry(root, path) }
        }
        assertEquals(File(root, "downloads/book/chapter.json").canonicalFile,
            LocalBackup.safeEntry(root, "downloads/book/chapter.json"))
    }

}
