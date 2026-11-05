package com.breakyuna.esjzone.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Immutable
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Immutable
data class ReaderImageBackground(
    val fileName: String = "",
    val blur: Float = 20f,
    val textColor: Long = 0xFF202020L
) {
    fun sanitized() = copy(
        fileName = fileName.takeIf { it.matches(Regex("[a-f0-9-]{36}\\.jpg")) }.orEmpty(),
        blur = blur.takeIf { it.isFinite() }?.coerceIn(0f, 100f) ?: 20f,
        textColor = (textColor and 0x00FFFFFFL) or 0xFF000000L
    )
}

/** Private, bounded copies keep photo-provider permissions out of the reading path. */
internal object ReaderImageBackgroundStore {
    private val renderMutex = Mutex()
    private val rendered = LruCache<String, Bitmap>(2)

    fun file(context: Context, background: ReaderImageBackground): File? =
        background.sanitized().fileName.takeIf { it.isNotBlank() }
            ?.let { File(context.filesDir, "reader_backgrounds/$it") }

    private fun draftDirectory(context: Context) = File(context.cacheDir, "reader_background_drafts")

    fun deleteDraft(context: Context, source: File?) {
        if (source != null && source.parentFile == draftDirectory(context)) source.delete()
    }

    suspend fun importImage(context: Context, uri: Uri): File {
        val directory = draftDirectory(context)
        val output = File(directory, "${UUID.randomUUID()}.jpg")
        try {
            return withContext(Dispatchers.IO) {
                directory.mkdirs()
                val input = File.createTempFile("source-", ".tmp", directory)
                val job = currentCoroutineContext()
                try {
                    val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("Image is unavailable")
                    stream.use { source ->
                        input.outputStream().use { target ->
                            val buffer = ByteArray(32 * 1024)
                            var total = 0L
                            while (true) {
                                job.ensureActive()
                                val count = source.read(buffer)
                                if (count < 0) break
                                total += count
                                if (total > 32L * 1024 * 1024) throw IOException("Image exceeds import limit")
                                target.write(buffer, 0, count)
                            }
                        }
                    }
                    val decoded = decode(input, 2048)
                    try {
                        val opaque = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
                        try {
                            Canvas(opaque).apply {
                                drawColor(android.graphics.Color.WHITE)
                                drawBitmap(decoded, 0f, 0f, null)
                            }
                            job.ensureActive()
                            output.outputStream().use {
                                if (!opaque.compress(Bitmap.CompressFormat.JPEG, 90, it)) throw IOException("Image encoding failed")
                            }
                        } finally { opaque.recycle() }
                    } finally { decoded.recycle() }
                    job.ensureActive()
                    output
                } finally {
                    input.delete()
                }
            }
        } catch (error: Throwable) {
            // Also clean up if cancellation discards the result on the return to the caller.
            withContext(NonCancellable + Dispatchers.IO) { deleteDraft(context, output) }
            throw error
        }
    }

    /** Called on IO. The settings reference is published only after the copy is complete. */
    fun persist(context: Context, source: File): File {
        if (!source.isFile || source.length() == 0L) throw IOException("Background image is unavailable")
        val directory = File(context.filesDir, "reader_backgrounds").apply { mkdirs() }
        if (source.parentFile == directory) return source
        val target = File(directory, "${UUID.randomUUID()}.jpg")
        try {
            source.copyTo(target)
            return target
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    fun removeUnused(retained: File) {
        retained.parentFile?.listFiles()?.filter { it != retained }?.forEach { it.delete() }
    }

    suspend fun render(source: File, blur: Float, maxSize: Int = 1440): Bitmap = withContext(Dispatchers.Default) {
        renderMutex.withLock {
            val strength = blur.roundToInt().coerceIn(0, 100)
            val length = source.length()
            if (!source.isFile || length == 0L) throw IOException("Background image is unavailable")
            val key = "${source.absolutePath}|${source.lastModified()}|$length|$strength|$maxSize"
            rendered.get(key)?.let { return@withLock it }
            val bitmap = decode(source, maxSize)
            try {
                if (strength == 0) {
                    rendered.put(key, bitmap)
                    return@withLock bitmap
                }
                val width = bitmap.width
                val height = bitmap.height
                val pixels = IntArray(width * height)
                val temporary = IntArray(pixels.size)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                val radius = (strength / 100f * 24f * maxOf(width, height) / 720f).roundToInt().coerceAtLeast(1)
                val job = currentCoroutineContext()
                repeat(3) {
                    blurPass(pixels, temporary, width, height, 1, width, radius) { job.ensureActive() }
                    blurPass(temporary, pixels, height, width, width, 1, radius) { job.ensureActive() }
                }
                val blurred = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
                rendered.put(key, blurred)
                blurred
            } finally {
                // Published images belong to Compose/the cache and must not be recycled here.
                if (strength != 0) bitmap.recycle()
            }
        }
    }

    private fun decode(source: File, maxSize: Int): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetColorSpace(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB))
            val scale = minOf(1.0, maxSize.toDouble() / maxOf(info.size.width, info.size.height))
            decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1),
                (info.size.height * scale).roundToInt().coerceAtLeast(1))
        }

    /** Three separable box passes approximate a Gaussian blur on every supported Android version. */
    private inline fun blurPass(
        source: IntArray, target: IntArray, length: Int, lines: Int,
        stride: Int, lineStride: Int, radius: Int, checkActive: () -> Unit
    ) {
        val divisor = radius * 2 + 1
        repeat(lines) { line ->
            checkActive()
            val start = line * lineStride
            var red = 0
            var green = 0
            var blue = 0
            for (offset in -radius..radius) {
                val color = source[start + offset.coerceIn(0, length - 1) * stride]
                red += color shr 16 and 255
                green += color shr 8 and 255
                blue += color and 255
            }
            repeat(length) { position ->
                target[start + position * stride] = (0xFF shl 24) or
                    ((red / divisor) shl 16) or ((green / divisor) shl 8) or (blue / divisor)
                val removed = source[start + (position - radius).coerceAtLeast(0) * stride]
                val added = source[start + (position + radius + 1).coerceAtMost(length - 1) * stride]
                red += (added shr 16 and 255) - (removed shr 16 and 255)
                green += (added shr 8 and 255) - (removed shr 8 and 255)
                blue += (added and 255) - (removed and 255)
            }
        }
    }
}
