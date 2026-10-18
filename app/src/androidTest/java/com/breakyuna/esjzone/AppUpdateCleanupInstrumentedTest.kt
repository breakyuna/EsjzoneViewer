package com.breakyuna.esjzone

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.breakyuna.esjzone.update.deleteInstalledUpdateApk
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUpdateCleanupInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun installedAndOlderApksAreRemovedWithoutDeletingOtherFiles() = withUpdateCache { cachedContext, apk ->
        val partial = File(apk.parentFile, "update.apk.part").apply { writeText("partial") }
        val versionCode = requireNotNull(context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)).longVersionCode
        for (installedVersion in listOf(versionCode, versionCode + 1)) {
            if (!apk.exists()) File(context.applicationInfo.sourceDir).copyTo(apk)
            runBlocking { deleteInstalledUpdateApk(cachedContext, installedVersion) }
            assertFalse(apk.exists())
            assertEquals("partial", partial.readText())
        }
    }

    @Test
    fun newerApkIsKeptForLaterInstallation() = withUpdateCache { cachedContext, apk ->
        val versionCode = requireNotNull(context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)).longVersionCode
        val originalSize = apk.length()
        runBlocking { deleteInstalledUpdateApk(cachedContext, versionCode - 1) }
        assertTrue(apk.isFile)
        assertEquals(originalSize, apk.length())
    }

    @Test
    fun unparseableApkIsNotTreatedAsAnInstalledUpdate() = withUpdateCache { cachedContext, apk ->
        apk.writeText("invalid APK")
        runBlocking { deleteInstalledUpdateApk(cachedContext, Long.MAX_VALUE) }
        assertEquals("invalid APK", apk.readText())
    }

    private fun withUpdateCache(block: (Context, File) -> Unit) {
        val directory = File.createTempFile("update-cleanup-", ".tmp", context.cacheDir)
        check(directory.delete() && directory.mkdir())
        try {
            val cachedContext = object : ContextWrapper(context) {
                override fun getCacheDir(): File = directory
            }
            val apk = File(directory, "app_updates/update.apk")
            check(requireNotNull(apk.parentFile).mkdirs())
            File(context.applicationInfo.sourceDir).copyTo(apk)
            block(cachedContext, apk)
        } finally {
            directory.deleteRecursively()
        }
    }
}
