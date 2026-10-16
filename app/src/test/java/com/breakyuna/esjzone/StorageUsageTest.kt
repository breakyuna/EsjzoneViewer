package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.page.directoryBytes
import com.breakyuna.esjzone.ui.page.classifyStorageUsage
import com.breakyuna.esjzone.ui.page.StorageCategory
import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageUsageTest {
    @Test
    fun storageDetailsPartitionFilesAndPreserveSystemTotal() {
        val root = Files.createTempDirectory("storage-details-").toFile()
        try {
            fun file(path: String, bytes: Int) = File(root, path).apply {
                parentFile!!.mkdirs()
                writeBytes(ByteArray(bytes))
            }
            file("files/downloaded_novels/book/chapter-a.json", 11)
            file("files/downloaded_novels/book/images/illustration.jpg", 13)
            file("files/downloaded_novels/book/cover.jpg", 17)
            file("files/downloaded_novels/book/manifest.json", 19)
            file("files/downloaded_novels/book/pending.tmp", 3)
            file("files/novel_page_cache/page.html", 23)
            file("cache/image_cache/image", 29)
            file("files/home_data_snapshot.json", 31)
            file("files/history_cache/history.json", 37)
            file("files/auto_backups/esjzone-auto-1.zip", 41)
            file("files/auto_backups/pending.zip.part", 43)
            file("files/bookshelf_covers/cover", 47)
            file("files/reader_fonts/font", 53)
            file("databases/app.db", 59)
            file("app_webview/data", 61)
            file("shared_prefs/settings.xml", 67)
            file("files/datastore/settings", 71)
            file("files/cache_analysis/cache-analysis.json", 73)
            Files.createSymbolicLink(File(root, "files/downloaded_novels/book/images/loop").toPath(), root.toPath())

            val usage = classifyStorageUsage(root, File(root, "files"), File(root, "cache"), 200L, 120L, 777L)
            assertEquals(StorageCategory.entries.toList(), usage.map { it.category })
            assertEquals(listOf(200L, 63L, 120L, 84L, 100L, 410L), usage.map { it.bytes })
            assertEquals(977L, usage.sumOf { it.bytes })
            val parts = usage.flatMap { it.parts }.associate { it.labelRes to it.bytes }
            assertEquals(11L, parts.getValue(R.string.storage_download_chapters))
            assertEquals(30L, parts.getValue(R.string.storage_download_images))
            assertEquals(22L, parts.getValue(R.string.storage_download_records))
            assertEquals(41L, parts.getValue(R.string.storage_backup_archives))
            assertEquals(43L, parts.getValue(R.string.storage_backup_pending))
            assertEquals(23L, parts.getValue(R.string.storage_page_cache))
            assertEquals(29L, parts.getValue(R.string.settings_image_cache))
            assertEquals(31L, parts.getValue(R.string.cache_layer_home))
            assertEquals(37L, parts.getValue(R.string.cache_layer_history))
            assertEquals(138L, parts.getValue(R.string.storage_settings))
            assertEquals(73L, parts.getValue(R.string.storage_analysis_log))
            assertEquals(79L, parts.getValue(R.string.storage_other_data))
        } finally {
            Files.deleteIfExists(File(root, "files/downloaded_novels/book/images/loop").toPath())
            root.deleteRecursively()
        }
    }

    @Test
    fun concurrentSystemAndFileSnapshotsDoNotCreateNegativeDetails() {
        val root = Files.createTempDirectory("storage-details-").toFile()
        try {
            File(root, "files/novel_page_cache").mkdirs()
            File(root, "files/novel_page_cache/page.html").writeBytes(ByteArray(17))
            val usage = classifyStorageUsage(root, File(root, "files"), File(root, "cache"), 200L, 300L, 0L)
            assertEquals(200L, usage.first { it.category == StorageCategory.APPLICATION }.bytes)
            assertEquals(0L, usage.first { it.category == StorageCategory.APP_DATA }.bytes)
            assertTrue(usage.flatMap { it.parts }.all { it.bytes >= 0L })
            assertEquals(217L, usage.sumOf { it.bytes })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun ignoresDirectoryRemovedBeforeListing() {
        val root = Files.createTempDirectory("storage-usage-").toFile()
        try {
            val disappearing = object : File(root.absolutePath) {
                override fun listFiles(): Array<File>? {
                    assertTrue(root.delete())
                    return super.listFiles()
                }
            }

            assertEquals(0L, directoryBytes(disappearing))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun reportsListingFailureForDirectoryThatStillExists() {
        val root = Files.createTempDirectory("storage-usage-").toFile()
        try {
            val unreadable = object : File(root.absolutePath) {
                override fun listFiles(): Array<File>? = null
            }

            assertThrows(IOException::class.java) { directoryBytes(unreadable) }
            assertTrue(root.isDirectory)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun countsNestedFilesWithoutFollowingSymbolicLinks() {
        val root = Files.createTempDirectory("storage-usage-").toFile()
        try {
            File(root, "chapter.json").writeBytes(ByteArray(123))
            val images = File(root, "images").apply { mkdirs() }
            File(images, "cover.jpg").writeBytes(ByteArray(456))
            Files.createSymbolicLink(File(root, "linked-images").toPath(), images.toPath())
            Files.createSymbolicLink(File(root, "linked-root").toPath(), root.toPath())

            assertEquals(579L, directoryBytes(root))
            assertEquals(0L, directoryBytes(File(root, "absent")))
        } finally {
            Files.deleteIfExists(File(root, "linked-images").toPath())
            Files.deleteIfExists(File(root, "linked-root").toPath())
            root.deleteRecursively()
        }
    }
}
