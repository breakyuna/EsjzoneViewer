package com.breakyuna.esjzone

import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUpdateFileProviderInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun updateApkHasAReadableContentUriFromANonExportedProvider() {
        val authority = "${context.packageName}.app-updates"
        val provider = requireNotNull(context.packageManager.resolveContentProvider(authority, 0))
        assertFalse(provider.exported)
        assertTrue(provider.grantUriPermissions)
        val directory = File(context.cacheDir, "app_updates").apply { mkdirs() }
        val file = File.createTempFile("provider-", ".apk", directory)
        try {
            val bytes = byteArrayOf(1, 2, 3)
            file.writeBytes(bytes)
            val uri = FileProvider.getUriForFile(context, authority, file)
            assertEquals("content", uri.scheme)
            assertEquals(authority, uri.authority)
            val actual = requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytes() }
            assertArrayEquals(bytes, actual)
        } finally { file.delete() }
    }

    @Test
    fun otherPrivateCacheFilesCannotBeSharedByTheUpdateProvider() {
        val file = File.createTempFile("unrelated-", ".txt", context.cacheDir)
        try {
            assertThrows(IllegalArgumentException::class.java) {
                FileProvider.getUriForFile(context, "${context.packageName}.app-updates", file)
            }
        } finally { file.delete() }
    }
}
