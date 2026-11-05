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
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile

class ReaderImageBackgroundInstrumentedTest {
    @Test fun legacyBackgroundRetainsPhotoColorBlurAndTransparency() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storeName = "reader-legacy-photo-${UUID.randomUUID()}.preferences_pb"
        val photoName = "${UUID.randomUUID()}.jpg"
        val legacyJob = SupervisorJob()
        try {
            val legacy = PreferenceDataStoreFactory.create(scope = CoroutineScope(legacyJob + Dispatchers.IO),
                produceFile = { context.preferencesDataStoreFile(storeName) })
            legacy.edit {
                it[stringPreferencesKey("background")] = ReaderBackground.CUSTOM_IMAGE.name
                it[stringPreferencesKey("background_image_file")] = photoName
                it[longPreferencesKey("background_image_text_color")] = 0xFF202020L
                it[floatPreferencesKey("background_image_blur")] = 50f
                it[floatPreferencesKey("background_image_transparency")] = 90f
            }
        } finally { legacyJob.cancelAndJoin() }
        val job = SupervisorJob()
        try {
            val current = ReaderSettingsDataStore(context, CoroutineScope(job + Dispatchers.IO), storeName)
            val image = current.readImageBackground()
            assertEquals(photoName, image.fileName)
            assertEquals(0xFF202020L, image.textColor)
            assertEquals(50f, image.blur, 0f)
            assertEquals(90f, image.transparency, 0f)
            assertTrue(image.glowEnabled)
            assertEquals(70f, image.glowStrength, 0f)
            assertEquals(ReaderBackground.CUSTOM_IMAGE, current.settings.first { it.background == ReaderBackground.CUSTOM_IMAGE }.background)
        } finally { job.cancelAndJoin(); context.preferencesDataStoreFile(storeName).delete() }
    }

    @Test fun savedBackgroundReusesClearPreviewAndSurvivesEviction() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File.createTempFile("reader-cache-test-", ".jpg", context.cacheDir)
        val input = Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.RED)
        source.outputStream().use { input.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        input.recycle()
        var saved: File? = null
        try {
            val preview = ReaderImageBackgroundStore.render(source)
            val target = withContext(Dispatchers.IO) { ReaderImageBackgroundStore.persist(context, source, preview) }
            saved = target
            assertSame(preview, ReaderImageBackgroundStore.cached(target))
            ReaderImageBackgroundStore.render(source, maxSize = 80)
            ReaderImageBackgroundStore.render(source, maxSize = 40)
            assertNull(ReaderImageBackgroundStore.cached(target))
            val restored = ReaderImageBackgroundStore.render(target)
            assertNotSame(preview, restored)
            assertTrue(preview.sameAs(restored))
        } finally { source.delete(); saved?.delete() }
    }

    @Test fun blurredPreviewCacheIsReusedAndTransferredToSavedPhoto() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File.createTempFile("reader-blur-cache-", ".jpg", context.cacheDir)
        val input = Bitmap.createBitmap(100, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        source.outputStream().use { input.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        input.recycle()
        var saved: File? = null
        try {
            val original = ReaderImageBackgroundStore.render(source)
            val blurred = ReaderImageBackgroundStore.renderAppearance(source, 30f)
            assertSame(blurred, ReaderImageBackgroundStore.cachedAppearance(source, 30f))
            assertSame(blurred, ReaderImageBackgroundStore.renderAppearance(source, 30f))
            assertNull(ReaderImageBackgroundStore.cachedAppearance(source, 10f))
            val target = withContext(Dispatchers.IO) { ReaderImageBackgroundStore.persist(context, source, original) }
            saved = target
            assertSame(blurred, ReaderImageBackgroundStore.cachedAppearance(target, 30f))
            assertSame(original, ReaderImageBackgroundStore.cached(target))
        } finally { source.delete(); saved?.delete() }
    }

    @Test fun stalePreviewCannotBeAppliedAfterRenderingFailsOrResolutionChanges() {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        try {
            val ready = com.breakyuna.esjzone.ui.page.ReaderBackgroundPageModel.State(
                source = File("photo.jpg"), draft = ReaderImageBackground(blur = 20f),
                preview = bitmap, previewBlur = 20f, previewSize = 480, loading = false)
            assertTrue(ready.canApply)
            assertFalse(ready.copy(draft = ready.draft.copy(blur = 30f)).canApply)
            assertFalse(ready.copy(previewBlur = null, previewBusy = false).canApply)
            assertFalse(ready.copy(requestedSize = 1440).canApply)
            assertTrue(ready.copy(previewSize = 1440, requestedSize = 1440).canApply)
        } finally { bitmap.recycle() }
    }

    @Test fun applyingPhotoRetainsPendingReaderEditsAndSurvivesStoreRecreation() = runBlocking {
        withTimeout(5_000) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val fileName = "reader-image-${UUID.randomUUID()}.preferences_pb"
            val original = ReaderSettings(fontSizeSp = 24f, horizontalPaddingDp = 28f)
            val photo = ReaderImageBackground("${UUID.randomUUID()}.jpg", 0xFF111111L, glowEnabled = false, glowStrength = 37f)
            val firstJob = SupervisorJob()
            try {
                val first = ReaderSettingsDataStore(context, CoroutineScope(firstJob + Dispatchers.IO), fileName)
                assertEquals(70f, first.readImageBackground().glowStrength, 0f)
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
            val result = runCatching { ReaderImageBackgroundStore.render(corrupt) }
            assertTrue(result.isFailure)
        } finally {
            corrupt.delete()
        }
    }

    @Test fun importedPhotoRemainsClearAndIndependentOfItsSource() = runBlocking {
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
            val clear = ReaderImageBackgroundStore.render(copy)
            assertEquals(160, clear.width)
            assertEquals(100, clear.height)
            assertTrue(Color.red(clear.getPixel(78, 50)) < 20)
            assertSame(clear, ReaderImageBackgroundStore.render(copy))
        } finally {
            original.delete()
            imported?.delete()
        }
    }
}
