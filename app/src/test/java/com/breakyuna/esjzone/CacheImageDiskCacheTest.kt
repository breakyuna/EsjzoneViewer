package com.breakyuna.esjzone

import coil3.disk.DiskCache
import com.breakyuna.esjzone.network.CacheImageDiskCache
import java.io.File
import java.io.IOException
import java.nio.file.Files
import okio.ByteString.Companion.encodeUtf8
import okio.Path.Companion.toOkioPath
import org.junit.Assert.*
import org.junit.Test

class CacheImageDiskCacheTest {
    private fun delegate(directory: File) = DiskCache.Builder()
        .directory(directory.toOkioPath()).maxSizeBytes(Long.MAX_VALUE).build()

    private fun write(cache: DiskCache, key: String, value: String) {
        val editor = requireNotNull(cache.openEditor(key))
        File(editor.metadata.toString()).writeText("metadata")
        File(editor.data.toString()).writeText(value)
        editor.commit()
    }

    @Test
    fun imageCommitsAppendBoundedRecordsAndPeriodicallyCompact() {
        val directory = Files.createTempDirectory("image-index-append-").toFile()
        val cache = CacheImageDiskCache(delegate(directory))
        val index = File(directory, "cache-keys-v1")
        try {
            write(cache, "one", "first")
            write(cache, "two", "second")
            val previous = index.readText()
            write(cache, "one", "replacement")
            val appended = index.readText()
            assertTrue(appended.startsWith(previous))
            assertTrue(appended.length - previous.length in 1..100)
            repeat(150) { write(cache, "one", "replacement-$it") }
            assertTrue(index.readLines().size <= 64)
            assertEquals(2, cache.entryCount)
            requireNotNull(cache.openSnapshot("one")).use {
                assertEquals("replacement-149", File(it.data.toString()).readText())
            }
        } finally { cache.shutdown(); directory.deleteRecursively() }
    }

    @Test
    fun restartReplaysReplacementAndUnflushedEvictionWithoutLosingOtherImages() {
        val directory = Files.createTempDirectory("image-index-replay-").toFile()
        var disk = delegate(directory)
        var cache = CacheImageDiskCache(disk)
        try {
            write(cache, "one", "first")
            write(cache, "two", "second")
            write(cache, "one", "replacement")
            requireNotNull(cache.openSnapshot("one")).use {
                assertEquals(1, cache.storageEntries().count { entry -> entry.remove() })
            }
            // Model interruption after a key was registered but its data was never committed.
            val pending = requireNotNull(cache.openEditor("pending"))
            File(pending.data.toString()).writeText("unfinished")
            File(directory, "cache-keys-v1").appendText("${"pending".encodeUtf8().sha256().hex()} 7\ntruncated")
            disk.shutdown() // Deliberately skip the wrapper's index compaction.
            disk = delegate(directory)
            cache = CacheImageDiskCache(disk)
            assertNull(cache.openSnapshot("two"))
            assertNull(cache.openSnapshot("pending"))
            requireNotNull(cache.openSnapshot("one")).use {
                assertEquals("replacement", File(it.data.toString()).readText())
            }
            assertEquals(1, cache.entryCount)
            assertEquals(disk.size, cache.storageEntries().sumOf { it.sizeBytes })
        } finally { cache.shutdown(); directory.deleteRecursively() }
    }

    @Test
    fun interruptedCommitAcknowledgmentRetainsTheNewImageAfterRestart() {
        val directory = Files.createTempDirectory("image-index-commit-interrupted-").toFile()
        var disk = delegate(directory)
        val interrupted = object : DiskCache by disk {
            override fun openEditor(key: String): DiskCache.Editor? = disk.openEditor(key)?.let { editor ->
                object : DiskCache.Editor by editor {
                    override fun commitAndOpenSnapshot(): DiskCache.Snapshot? {
                        editor.commitAndOpenSnapshot()?.close()
                        throw IOException("Commit acknowledgment interrupted")
                    }
                }
            }
        }
        var cache = CacheImageDiskCache(interrupted)
        try {
            try {
                write(cache, "new-image", "committed")
                fail("Expected interrupted acknowledgment")
            } catch (_: IOException) { }
            disk.shutdown()
            disk = delegate(directory)
            cache = CacheImageDiskCache(disk)
            requireNotNull(cache.openSnapshot("new-image")).use {
                assertEquals("committed", File(it.data.toString()).readText())
            }
            assertEquals(1, cache.entryCount)
        } finally { cache.shutdown(); directory.deleteRecursively() }
    }

    @Test
    fun accessedTimesSurviveShutdownAndClearStillDiscardsPendingWrites() {
        val directory = Files.createTempDirectory("image-index-access-").toFile()
        var cache = CacheImageDiskCache(delegate(directory))
        val index = File(directory, "cache-keys-v1")
        try {
            write(cache, "one", "first")
            cache.touch("one")
            val accessedAt = cache.storageEntries().single().accessedAt
            cache.shutdown()
            cache = CacheImageDiskCache(delegate(directory))
            assertEquals(accessedAt, cache.storageEntries().single().accessedAt)
            val pending = requireNotNull(cache.openEditor("pending"))
            File(pending.data.toString()).writeText("unfinished")
            cache.clear()
            pending.abort()
            assertEquals(0, cache.entryCount)
            assertTrue(index.readText().isEmpty())
            cache.shutdown()
            cache = CacheImageDiskCache(delegate(directory))
            assertNull(cache.openSnapshot("one"))
            assertNull(cache.openSnapshot("pending"))
        } finally { cache.shutdown(); directory.deleteRecursively() }
    }
}
