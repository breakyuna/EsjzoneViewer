package com.breakyuna.esjzone

import com.breakyuna.esjzone.update.ReleaseApkAsset
import com.breakyuna.esjzone.update.saveReleaseApk
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class ReleaseApkDownloadTest {
    private val tag = "v0.2.4"
    private fun asset(name: String = "EsjzoneViewer-$tag.apk") = ReleaseApkAsset(
        name, "https://github.com/breakyuna/EsjzoneViewer/releases/download/$tag/$name", 3
    )

    @Test
    fun selectsPublishedWorkflowApkAmongOtherAttachments() {
        val apk = asset()
        assertEquals(apk, ReleaseApkAsset.select(tag, listOf(asset("other.apk"), asset("SHA256SUMS.txt"), apk)))
        assertEquals(asset("universal.apk"), ReleaseApkAsset.select(tag, listOf(asset("universal.apk"))))
        assertNull(ReleaseApkAsset.select(tag, listOf(asset("arm64.apk"), asset("x86.apk"))))
    }

    @Test
    fun rejectsDownloadsOutsideTheSelectedReleaseAndInvalidMetadata() {
        val apk = asset()
        val invalid = listOf(
            apk.copy(url = apk.url.replace("https:", "http:")),
            apk.copy(url = apk.url.replace("github.com", "github.com.example.org")),
            apk.copy(url = apk.url.replace("breakyuna/", "other/")),
            apk.copy(url = apk.url.replace(tag, "v0.2.3")),
            apk.copy(url = apk.url.replace(apk.name, "other.apk")),
            apk.copy(url = apk.url.replace("github.com/", "github.com:8443/")),
            apk.copy(url = apk.url.replace("github.com/", "user@github.com/")),
            apk.copy(size = 0),
            apk.copy(digest = "sha256:invalid")
        )
        invalid.forEach { assertNull(it.toString(), ReleaseApkAsset.select(tag, listOf(it))) }
    }

    @Test
    fun completeVerifiedDownloadIsCommittedAndReportsProgress() = withDirectory { directory ->
        val bytes = byteArrayOf(1, 2, 3)
        val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val progress = mutableListOf<Long>()
        val file = saveReleaseApk(bytes.inputStream(), directory, asset().copy(digest = "sha256:$sha256")) { progress += it }
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(listOf(3L), progress)
        assertEquals(listOf("update.apk"), directory.listFiles()!!.map { it.name })
    }

    @Test
    fun releaseWithoutDigestStillRequiresTheCompleteAttachment() = withDirectory { directory ->
        assertArrayEquals(byteArrayOf(1, 2, 3),
            saveReleaseApk(byteArrayOf(1, 2, 3).inputStream(), directory, asset()).readBytes())
    }

    @Test
    fun truncatedOversizedAndCorruptDownloadsAreNotCommitted() {
        listOf(byteArrayOf(1, 2), byteArrayOf(1, 2, 3, 4), byteArrayOf(1, 2, 3)).forEach { bytes ->
            withDirectory { directory ->
                val metadata = asset().copy(digest = "sha256:" + "0".repeat(64))
                assertThrows(IOException::class.java) { saveReleaseApk(bytes.inputStream(), directory, metadata) }
                assertTrue(directory.listFiles()!!.isEmpty())
            }
        }
    }

    @Test
    fun interruptedTransferRemovesThePartialApk() = withDirectory { directory ->
        val input = object : InputStream() {
            override fun read(): Int = throw IOException("Interrupted transfer")
        }
        assertThrows(IOException::class.java) { saveReleaseApk(input, directory, asset()) }
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test
    fun cancelledTransferDoesNotLeaveAnInstallableFile() = withDirectory { directory ->
        assertThrows(kotlinx.coroutines.CancellationException::class.java) {
            saveReleaseApk(byteArrayOf(1, 2, 3).inputStream(), directory, asset()) {
                throw kotlinx.coroutines.CancellationException("Cancelled")
            }
        }
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    private fun withDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("release-apk-").toFile()
        try { block(directory) } finally { directory.deleteRecursively() }
    }
}
