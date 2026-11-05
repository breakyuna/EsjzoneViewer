package com.breakyuna.esjzone

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.breakyuna.esjzone.data.settings.ReaderSettingsDataStore
import com.breakyuna.esjzone.ui.reader.ReaderBackground
import com.breakyuna.esjzone.ui.reader.ReaderImageBackground
import com.breakyuna.esjzone.ui.reader.ReaderImageBackgroundStore
import com.breakyuna.esjzone.ui.reader.ReaderSettings
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class ReaderImageBackgroundInstrumentedTest {
    @Test fun applyingPhotoRetainsPendingReaderEditsAndSurvivesStoreRecreation() = runBlocking {
        withTimeout(5_000) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val fileName = "reader-image-${UUID.randomUUID()}.preferences_pb"
            val original = ReaderSettings(fontSizeSp = 24f, horizontalPaddingDp = 28f)
            val photo = ReaderImageBackground("${UUID.randomUUID()}.jpg", 45f, 0xFFF4ECD8L, transparency = 37f)
            val firstJob = SupervisorJob()
            try {
                val first = ReaderSettingsDataStore(context, CoroutineScope(firstJob + Dispatchers.IO), fileName)
                assertEquals(0f, first.readImageBackground().transparency, 0f)
                first.saveDebounced(original)
                first.saveImageBackground(photo)
                val applied = first.settings.first { it.background == ReaderBackground.CUSTOM_IMAGE }
                assertEquals(original.copy(background = ReaderBackground.CUSTOM_IMAGE), applied)
                first.save(applied.copy(background = ReaderBackground.SYSTEM))
                assertEquals(photo, first.readImageBackground())
            } finally { firstJob.cancelAndJoin() }
            val secondJob = SupervisorJob()
            try {
                val second = ReaderSettingsDataStore(context, CoroutineScope(secondJob + Dispatchers.IO), fileName)
                assertEquals(photo, second.readImageBackground())
                assertEquals(original, second.settings.first { it.fontSizeSp == 24f })
            } finally { secondJob.cancelAndJoin() }
        }
    }

    @Test fun anExistingCorruptImageCannotBecomeAReaderBackground() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val corrupt = File.createTempFile("reader-photo-corrupt-", ".jpg", context.cacheDir)
        try {
            corrupt.writeText("invalid image")
            val result = runCatching { ReaderImageBackgroundStore.render(corrupt, 20f) }
            assertTrue(result.isFailure)
        } finally {
            corrupt.delete()
        }
    }

    @Test fun importedPhotoIsIndependentOfItsSourceAndBlurPreservesSize() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val original = File.createTempFile("reader-photo-test-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888)
        for (y in 0 until 100) for (x in 0 until 160) {
            bitmap.setPixel(x, y, if (x < 80) Color.BLACK else Color.WHITE)
        }
        original.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        var imported: File? = null
        try {
            val copy = ReaderImageBackgroundStore.importImage(context, Uri.fromFile(original))
            imported = copy
            original.delete()
            val clear = ReaderImageBackgroundStore.render(copy, 0f)
            val blurred = ReaderImageBackgroundStore.render(copy, 50f)
            assertEquals(clear.width, blurred.width)
            assertEquals(clear.height, blurred.height)
            assertEquals(160, clear.width)
            assertTrue(Color.red(clear.getPixel(78, 50)) < 20)
            assertTrue(Color.red(blurred.getPixel(78, 50)) > 20)
            assertSame(blurred, ReaderImageBackgroundStore.render(copy, 100f))
            assertTrue(Color.red(clear.getPixel(78, 50)) < 20)
        } finally {
            original.delete()
            imported?.delete()
        }
    }
}
