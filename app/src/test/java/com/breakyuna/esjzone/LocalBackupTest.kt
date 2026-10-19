package com.breakyuna.esjzone

import com.breakyuna.esjzone.backup.LocalBackup
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
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

    @Test fun mixedDownloadFilesRoundTripWithoutRecompressingGzipOrImages() {
        val root = java.nio.file.Files.createTempDirectory("backup-compression-").toFile()
        try {
            val json = "{\"text\":\"${"正文".repeat(10_000)}\"}".toByteArray(Charsets.UTF_8)
            val plain = File(root, "plain.json").apply { writeBytes(json) }
            val gzip = File(root, "gzip.json").apply {
                java.util.zip.GZIPOutputStream(outputStream()).use { it.write(json) }
            }
            val image = File(root, "image.png").apply { writeBytes(ByteArray(32 * 1024) { 42 }) }
            val archive = File(root, "backup.zip")
            val files = linkedMapOf("backup.json" to plain, "downloads/book/chapter.json" to gzip,
                "downloads/book/images/image.png" to image, "downloads/book/legacy.json" to plain)
            java.util.zip.ZipOutputStream(archive.outputStream()).use { zip ->
                files.forEach { (name, file) -> LocalBackup.appendZipFile(zip, file, name) }
            }
            java.util.zip.ZipFile(archive).use { zip ->
                files.forEach { (name, file) ->
                    val entry = zip.getEntry(name)
                    assertArrayEquals(file.readBytes(), zip.getInputStream(entry).use { it.readBytes() })
                    if (file == gzip || file == image) assertTrue(entry.compressedSize >= entry.size)
                    else assertTrue(entry.compressedSize < entry.size)
                }
            }
        } finally { root.deleteRecursively() }
    }

}
