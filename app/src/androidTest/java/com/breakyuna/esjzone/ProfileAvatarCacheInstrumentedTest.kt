package com.breakyuna.esjzone

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.Image
import coil3.ImageLoader
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.breakyuna.esjzone.database.GeneralDatabase
import com.breakyuna.esjzone.database.dao.put
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.ui.designsystem.loadProfileAvatar
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileAvatarCacheInstrumentedTest {
    @Test fun restoresLegacyRelativeAvatarWhenReplacementIsEmptyOrFails() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, GeneralDatabase::class.java).build()
        val loader = ImageLoader.Builder(context).build()
        val old = avatarFile(context, Color.RED)
        val invalid = File.createTempFile("invalid-avatar", ".png", context.cacheDir)
        val relativeUrl = "/uploads/fixture-avatar.png"
        val resolvedUrl = EsjzoneUrls.resolve(relativeUrl)
        val imageKey = EsjzoneUrls.canonicalCacheUrl(resolvedUrl)
        val dao = database.cacheDao()
        try {
            invalid.writeText("not an image")
            val cached = loader.execute(ImageRequest.Builder(context)
                .data(old.toURI().toString()).allowHardware(false).build()) as SuccessResult
            loader.memoryCache!![MemoryCache.Key(imageKey)] = MemoryCache.Value(cached.image)
            old.delete()

            listOf("", invalid.toURI().toString()).forEachIndexed { index, replacement ->
                val key = "profile:fixture:legacy:$index:avatar"
                dao.put(key, relativeUrl)
                var displayed: Image? = null
                loadProfileAvatar(ImageRequest.Builder(context).data(replacement)
                    .allowHardware(false).build(), key, loader, dao, true) { displayed = it }
                assertEquals(Color.RED, displayed!!.toBitmap().getPixel(0, 0))
                assertEquals(resolvedUrl, dao.findByKey("$key-loaded")?.value)
            }
        } finally {
            old.delete()
            invalid.delete()
            loader.shutdown()
            database.close()
        }
    }

    @Test fun restoresLastSuccessfulAvatarAndOnlyReplacesItAfterSuccess() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, GeneralDatabase::class.java).build()
        val loader = ImageLoader.Builder(context).build()
        val old = avatarFile(context, Color.RED)
        val fresh = avatarFile(context, Color.BLUE)
        val invalid = File.createTempFile("invalid-avatar", ".png", context.cacheDir)
        val key = "profile:fixture:avatar"
        val oldUrl = old.toURI().toString()
        val freshUrl = fresh.toURI().toString()
        val dao = database.cacheDao()
        var displayed: Image? = null
        fun request(url: String) = ImageRequest.Builder(context).data(url).allowHardware(false).build()
        try {
            invalid.writeText("not an image")
            // Bootstrap the successful-image pointer from an existing profile snapshot.
            dao.put(key, oldUrl)
            loadProfileAvatar(request(oldUrl), key, loader, dao, true) { displayed = it }
            assertEquals(Color.RED, displayed!!.toBitmap().getPixel(0, 0))
            assertEquals(oldUrl, dao.findByKey("$key-loaded")?.value)

            // A newer metadata snapshot can point to a broken image. Reopening must
            // restore the last usable image, and the failed replacement must retain it.
            dao.put(key, invalid.toURI().toString())
            displayed = null
            loadProfileAvatar(request(invalid.toURI().toString()), key, loader, dao, true) {
                displayed = it
            }
            assertEquals(Color.RED, displayed!!.toBitmap().getPixel(0, 0))
            assertEquals(oldUrl, dao.findByKey("$key-loaded")?.value)

            val replacements = mutableListOf<Int>()
            loadProfileAvatar(request(freshUrl), key, loader, dao, true) {
                replacements += it.toBitmap().getPixel(0, 0)
                displayed = it
            }
            assertEquals(listOf(Color.RED, Color.BLUE), replacements)
            assertEquals(freshUrl, dao.findByKey("$key-loaded")?.value)

            loadProfileAvatar(request(""), key, loader, dao, false) { displayed = it }
            assertEquals(Color.BLUE, displayed!!.toBitmap().getPixel(0, 0))
            assertEquals(freshUrl, dao.findByKey("$key-loaded")?.value)
        } finally {
            old.delete()
            fresh.delete()
            invalid.delete()
            loader.shutdown()
            database.close()
        }
    }

    private fun avatarFile(context: Context, color: Int): File {
        val file = File.createTempFile("profile-avatar", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
        return file
    }
}
