package com.breakyuna.esjzone

import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.breakyuna.esjzone.update.ReleaseApkAsset
import com.breakyuna.esjzone.update.ReleaseUpdate
import com.breakyuna.esjzone.update.ReleaseUpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
}
