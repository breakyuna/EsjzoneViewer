package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.page.directoryBytes
import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageUsageTest {
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
