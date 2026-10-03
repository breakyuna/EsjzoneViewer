package com.breakyuna.esjzone.ui.designsystem

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Export the complete image, independently of the viewer's zoom and crop. */
internal suspend fun saveImageToGallery(
    context: Context,
    loader: ImageLoader,
    request: ImageRequest
): Uri = withContext(Dispatchers.IO) {
    val result = loader.execute(request.newBuilder().size(Size.ORIGINAL).allowHardware(false).build())
    val bitmap = (result as? SuccessResult)?.image?.toBitmap()
        ?: throw IOException("Unable to load image")
    currentCoroutineContext().ensureActive()
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "Esjzone_${UUID.randomUUID()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Esjzone")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: throw IOException("Unable to create image")
    var published = false
    try {
        val output = resolver.openOutputStream(uri) ?: throw IOException("Unable to open image")
        output.use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        currentCoroutineContext().ensureActive()
        check(resolver.update(uri, ContentValues().apply {
            put(MediaStore.Images.Media.IS_PENDING, 0)
        }, null, null) == 1)
        published = true
        uri
    } finally {
        if (!published) resolver.delete(uri, null, null)
    }
}
