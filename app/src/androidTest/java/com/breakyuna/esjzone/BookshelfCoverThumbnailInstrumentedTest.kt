package com.breakyuna.esjzone

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.database.writeBookshelfThumbnail
import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookshelfCoverThumbnailInstrumentedTest {
    private fun withDirectory(block: (File) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "shelf-thumbnail-").toFile()
        try { block(directory) } finally { directory.deleteRecursively() }
    }

    @Test
    fun largeCoverBecomesSmallerAndKeepsItsAspectRatio() = withDirectory { directory ->
        val source = File(directory, "source.png")
        val bitmap = Bitmap.createBitmap(960, 1440, Bitmap.Config.ARGB_8888)
        bitmap.setHasAlpha(false)
        val pixels = IntArray(960 * 1440) { index ->
            Color.rgb(index * 31 % 256, index / 7 % 256, index * 17 % 256)
        }
        bitmap.setPixels(pixels, 0, 960, 0, 0, 960, 1440)
        source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        val target = File(directory, "thumbnail.img")
        writeBookshelfThumbnail(source, target)
        val decoded = requireNotNull(BitmapFactory.decodeFile(target.path))
        assertEquals(480, decoded.width)
        assertEquals(720, decoded.height)
        assertTrue(target.length() < source.length())
        decoded.recycle()
    }

    @Test
    fun smallTransparentCoverDoesNotGrowOrLoseTransparency() = withDirectory { directory ->
        val source = File(directory, "source.png")
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        bitmap.setPixel(16, 16, Color.RED)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val target = File(directory, "thumbnail.img")
        writeBookshelfThumbnail(source, target)
        assertTrue(target.length() <= source.length())
        val decoded = requireNotNull(BitmapFactory.decodeFile(target.path))
        assertEquals(0, Color.alpha(decoded.getPixel(0, 0)))
        decoded.recycle()
    }

    @Test
    fun cancelledCommitKeepsThePreviousFileAndRemovesTemporaryFiles() = withDirectory { directory ->
        val source = File(directory, "source.png")
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val target = File(directory, "thumbnail.img").apply { writeText("previous") }
        try {
            writeBookshelfThumbnail(source, target) { throw IOException("Cancelled fixture commit") }
            fail("Commit must stop")
        } catch (_: IOException) { }
        assertEquals("previous", target.readText())
        assertEquals(setOf(source.name, target.name), directory.listFiles()!!.map { it.name }.toSet())
    }
}
