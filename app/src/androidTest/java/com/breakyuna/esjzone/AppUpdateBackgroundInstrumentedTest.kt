package com.breakyuna.esjzone

import android.content.Intent
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.breakyuna.esjzone.update.ReleaseApkAsset
import com.breakyuna.esjzone.update.ReleaseDownloadState
import com.breakyuna.esjzone.update.ReleaseUpdate
import com.breakyuna.esjzone.update.ReleaseUpdateChecker
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUpdateBackgroundInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val tag = "v999.0.0"
    private val asset = ReleaseApkAsset("EsjzoneViewer-$tag.apk",
        "https://github.com/breakyuna/EsjzoneViewer/releases/download/$tag/EsjzoneViewer-$tag.apk", 3)
    private fun release() = ReleaseUpdate(tag,
        "https://github.com/breakyuna/EsjzoneViewer/releases/tag/$tag", "notes".repeat(10_000), asset)

    @Test
    fun hidingBeforePermissionKeepsTheReleaseAndManualCheckReopensTheActiveTask() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ReleaseUpdateChecker.openDownload(context, ReleaseUpdateChecker.downloadIntent(context, release()))
            val current = requireNotNull(ReleaseUpdateChecker.update.value)
            try {
                // Clicking download closes the prompt before the permission callback starts the service.
                ReleaseUpdateChecker.hideDialog()
                assertFalse(ReleaseUpdateChecker.dialogVisible.value)
                assertSame(current, ReleaseUpdateChecker.update.value)
                assertEquals(ReleaseDownloadState.Idle, current.downloader.state.value)
                current.downloader.markStarting(asset)
                ReleaseUpdateChecker.dismiss()
                assertFalse(ReleaseUpdateChecker.dialogVisible.value)
                assertSame(current, ReleaseUpdateChecker.update.value)
                assertTrue(current.downloader.state.value is ReleaseDownloadState.Downloading)

                ReleaseUpdateChecker.checkNow(context)
                assertTrue(ReleaseUpdateChecker.dialogVisible.value)
                assertSame(current, ReleaseUpdateChecker.update.value)
                assertTrue(current.downloader.state.value is ReleaseDownloadState.Downloading)
            } finally {
                current.downloader.cancel()
                ReleaseUpdateChecker.dismiss()
            }
            assertNull(ReleaseUpdateChecker.update.value)
        }
    }

    @Test
    fun notificationPreservesDownloadMetadataWithoutPuttingLongNotesInTheBinderPayload() {
        val intent = ReleaseUpdateChecker.downloadIntent(context, release())
        val restored = requireNotNull(ReleaseUpdateChecker.releaseFromDownloadIntent(intent))
        assertEquals(tag, restored.version)
        assertEquals(asset, restored.apk)
        assertEquals("", restored.description)
        val parcel = Parcel.obtain()
        try {
            intent.writeToParcel(parcel, 0)
            assertTrue(parcel.dataSize() < 10_000)
        } finally { parcel.recycle() }
    }

    @Test
    fun exportedActivityDoesNotAcceptUnrelatedDownloadOrReleaseLinks() {
        val intent = ReleaseUpdateChecker.downloadIntent(context, release())
        assertNull(ReleaseUpdateChecker.releaseFromDownloadIntent(Intent(intent)
            .putExtra("update_apk_url", asset.url.replace("breakyuna/", "other/"))))
        assertNull(ReleaseUpdateChecker.releaseFromDownloadIntent(Intent(intent)
            .putExtra("update_page", "https://example.org/releases/tag/$tag")))
    }
}
