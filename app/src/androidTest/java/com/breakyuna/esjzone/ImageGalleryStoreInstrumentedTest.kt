package com.breakyuna.esjzone

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.ImageLoader
import coil3.request.ImageRequest
import com.breakyuna.esjzone.ui.designsystem.saveImageToGallery
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageGalleryStoreInstrumentedTest {
    @Test fun localImageIsPublishedAtFullSizeInGallery() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = ImageLoader.Builder(context).build()
        val source = File.createTempFile("gallery-source", ".png", context.cacheDir)
        var saved: Uri? = null
        try {
            val original = Bitmap.createBitmap(24, 40, Bitmap.Config.ARGB_8888)
            original.eraseColor(Color.RED)
            source.outputStream().use { original.compress(Bitmap.CompressFormat.PNG, 100, it) }
            original.recycle()
            val uri = saveImageToGallery(context, loader,
                ImageRequest.Builder(context).data(source.toURI().toString()).size(4, 4).build())
            saved = uri
            val resolver = context.contentResolver
            resolver.query(uri, arrayOf(MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.RELATIVE_PATH, MediaStore.Images.Media.IS_PENDING),
                null, null, null)!!.use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("image/png", cursor.getString(0))
                assertEquals("Pictures/Esjzone", cursor.getString(1).trimEnd('/'))
                assertEquals(0, cursor.getInt(2))
            }
            val restored = resolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }!!
            assertEquals(24, restored.width)
            assertEquals(40, restored.height)
            assertEquals(Color.RED, restored.getPixel(0, 0))
            restored.recycle()
        } finally {
            saved?.let { context.contentResolver.delete(it, null, null) }
            source.delete()
            loader.shutdown()
        }
    }

    @Test fun invalidImageFailsInsteadOfSavingPlaceholder() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = ImageLoader.Builder(context).build()
        val source = File.createTempFile("invalid-image", ".png", context.cacheDir)
        var saved: Uri? = null
        try {
            source.writeText("not an image")
            try {
                saved = saveImageToGallery(context, loader,
                    ImageRequest.Builder(context).data(source).build())
                fail("Invalid image must not be published")
            } catch (_: IOException) {
                // Loading failed before creating an entry in MediaStore.
            }
        } finally {
            saved?.let { context.contentResolver.delete(it, null, null) }
            source.delete()
            loader.shutdown()
        }
    }
}
