package com.breakyuna.esjzone

import com.breakyuna.esjzone.offline.DownloadedImageIndex
import java.io.File
import com.breakyuna.esjzone.storage.StorageFile
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
                assertEquals(StorageFile(first), index.find(StorageFile(root), firstKey))
                assertNull(index.find(StorageFile(root), nextKey))
            }
            val next = File(images, "image-$nextKey.webp").apply { writeText("committed") }
            index.committed(StorageFile(root), StorageFile(next))
            assertEquals(StorageFile(next), index.find(StorageFile(root), nextKey))
            assertEquals(1, scans)
            first.delete()
            assertNull(index.find(StorageFile(root), firstKey))
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
            assertEquals(StorageFile(first), index.find(StorageFile(root), key))
            index.remove(StorageFile(root))
            first.delete()
            val imported = File(images, "image-$key.jpg").apply { writeText("imported") }
            assertEquals(StorageFile(imported), index.find(StorageFile(root), key))
            index.clear()
            imported.writeText("")
            assertNull(index.find(StorageFile(root), key))
        } finally { root.deleteRecursively() }
    }
}
