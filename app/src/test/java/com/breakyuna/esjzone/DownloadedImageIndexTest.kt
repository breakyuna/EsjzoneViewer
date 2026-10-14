package com.breakyuna.esjzone

import com.breakyuna.esjzone.offline.DownloadedImageIndex
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class DownloadedImageIndexTest {
    @Test
    fun repeatedHitsAndMissesScanOnlyOnceAndCommittedFilesBecomeVisible() {
        val root = Files.createTempDirectory("image-index-").toFile()
        try {
            val images = File(root, "images").apply { mkdirs() }
            val firstKey = "a".repeat(64)
            val nextKey = "b".repeat(64)
            val first = File(images, "image-$firstKey.png").apply { writeText("image") }
            File(images, "dl_image-$nextKey.tmp").writeText("uncommitted")
            var scans = 0
            val index = DownloadedImageIndex { scans++; it.listFiles() }
            repeat(20) {
                assertEquals(first, index.find(root, firstKey))
                assertNull(index.find(root, nextKey))
            }
            val next = File(images, "image-$nextKey.webp").apply { writeText("committed") }
            index.committed(root, next)
            assertEquals(next, index.find(root, nextKey))
            assertEquals(1, scans)
            first.delete()
            assertNull(index.find(root, firstKey))
        } finally { root.deleteRecursively() }
    }

    @Test
    fun deletionImportAndStorageResetCannotReuseOldEntries() {
        val root = Files.createTempDirectory("image-index-reset-").toFile()
        try {
            val images = File(root, "images").apply { mkdirs() }
            val key = "c".repeat(64)
            val first = File(images, "image-$key.png").apply { writeText("old") }
            val index = DownloadedImageIndex()
            assertEquals(first, index.find(root, key))
            index.remove(root)
            first.delete()
            val imported = File(images, "image-$key.jpg").apply { writeText("imported") }
            assertEquals(imported, index.find(root, key))
            index.clear()
            imported.writeText("")
            assertNull(index.find(root, key))
        } finally { root.deleteRecursively() }
    }
}
