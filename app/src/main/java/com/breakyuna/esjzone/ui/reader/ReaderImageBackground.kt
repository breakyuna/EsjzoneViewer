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

enum class ReaderGlowMode { SAME_HUE, BLACK_WHITE, CUSTOM }

@Immutable
data class ReaderImageBackground(
    val fileName: String = "",
    val textColor: Long = 0xFFFFFFFFL,
    val glowEnabled: Boolean = true,
    val glowStrength: Float = 70f,
    val blur: Float = 0f,
    val transparency: Float = 0f,
    val glowMode: ReaderGlowMode = ReaderGlowMode.SAME_HUE,
    val lightTextGlowL: Float = 18f,
    val darkTextGlowL: Float = 96f,
    val glowChromaRetention: Float = 40f,
    val outerGlowAlpha: Float = 0.9f,
    val customGlowColor: Long? = null,
    val autoClampLightness: Boolean = true
) {
    fun sanitized() = copy(
        lightTextGlowL = lightTextGlowL.takeIf { it.isFinite() }?.coerceIn(0f, 40f) ?: 18f,
        darkTextGlowL = darkTextGlowL.takeIf { it.isFinite() }?.coerceIn(80f, 100f) ?: 96f,
        glowChromaRetention = glowChromaRetention.takeIf { it.isFinite() }?.coerceIn(0f, 100f) ?: 40f,
        outerGlowAlpha = if (outerGlowAlpha == 0.7f) 0.7f else 0.9f,
        customGlowColor = customGlowColor?.let { (it and 0xFFFFFFL) or 0xFF000000L },
        fileName = fileName.takeIf { it.matches(Regex("[a-f0-9-]{36}\\.jpg")) }.orEmpty(),
        textColor = (textColor and 0xFFFFFFL) or 0xFF000000L,
        blur = blur.takeIf { it.isFinite() }?.coerceIn(0f, 50f) ?: 0f,
        transparency = transparency.takeIf { it.isFinite() }?.coerceIn(0f, 100f) ?: 0f,
        glowStrength = glowStrength.takeIf { it.isFinite() }?.coerceIn(0f, 100f) ?: 70f
    )
}

/** Immutable private JPEG sources; decoded images are shared by preview and reader. */
internal object ReaderImageBackgroundStore {
    private val renderMutex = Mutex()
    private val appearanceMutex = Mutex()
    private data class AppearanceKey(val path: String, val size: Int, val blur: Int)
    private val appearances = LruCache<AppearanceKey, Rendered>(2)
    private data class RenderKey(val path: String, val size: Int)
    private data class Rendered(val length: Long, val modified: Long, val bitmap: Bitmap)
    private val rendered = LruCache<RenderKey, Rendered>(2)

    fun cached(source: File, maxSize: Int = 1440): Bitmap? =
        rendered.get(RenderKey(source.absolutePath, maxSize))?.bitmap

    fun cachedAppearance(source: File, blur: Float, maxSize: Int = 1440): Bitmap? {
        val strength = blur.roundToInt().coerceIn(0, 50)
        if (strength == 0) return cached(source, maxSize)
        return appearances.get(AppearanceKey(source.absolutePath, maxSize, strength))?.takeIf {
            it.length == source.length() && it.modified == source.lastModified()
        }?.bitmap
    }

    private fun publish(source: File, maxSize: Int, bitmap: Bitmap): Bitmap {
        bitmap.prepareToDraw()
        rendered.put(RenderKey(source.absolutePath, maxSize), Rendered(source.length(), source.lastModified(), bitmap))
        return bitmap
    }

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

    /** Called on IO before publishing settings; reuse the preview's prepared texture. */
    fun persist(context: Context, source: File, preview: Bitmap): File {
        if (!source.isFile || source.length() == 0L) throw IOException("Background image is unavailable")
        val directory = File(context.filesDir, "reader_backgrounds").apply { mkdirs() }
        val target = if (source.parentFile == directory) source else File(directory, "${UUID.randomUUID()}.jpg")
        try {
            if (target != source) source.copyTo(target)
            publish(target, 1440, preview)
            // A newly copied photo gets a new path; retain its prepared reader texture too.
            for ((key, value) in appearances.snapshot()) {
                if (key.path == source.absolutePath) appearances.put(key.copy(path = target.absolutePath),
                    Rendered(target.length(), target.lastModified(), value.bitmap))
            }
            return target
        } catch (error: Exception) {
            if (target != source) target.delete()
            throw error
        }
    }

    fun removeUnused(retained: File) {
        retained.parentFile?.listFiles()?.filter { it != retained }?.forEach { it.delete() }
    }

    suspend fun render(source: File, maxSize: Int = 1440): Bitmap = withContext(Dispatchers.IO) {
        renderMutex.withLock {
            val length = source.length()
            if (!source.isFile || length == 0L) throw IOException("Background image is unavailable")
            rendered.get(RenderKey(source.absolutePath, maxSize))?.takeIf {
                it.length == length && it.modified == source.lastModified()
            }?.let { it.bitmap.prepareToDraw(); return@withLock it.bitmap }
            publish(source, maxSize, decode(source, maxSize))
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

    suspend fun renderAppearance(source: File, blur: Float, maxSize: Int = 1440): Bitmap {
        cachedAppearance(source, blur, maxSize)?.let { return it }
        val original = render(source, maxSize)
        val strength = blur.roundToInt().coerceIn(0, 50)
        if (strength == 0) return original
        return withContext(Dispatchers.Default) {
            appearanceMutex.withLock {
                cachedAppearance(source, strength.toFloat(), maxSize)?.let { return@withLock it }
                val width = original.width
                val height = original.height
                val pixels = IntArray(width * height)
                val temporary = IntArray(pixels.size)
                original.getPixels(pixels, 0, width, 0, 0, width, height)
                val radius = (strength / 100f * 24f * maxOf(width, height) / 720f).roundToInt().coerceAtLeast(1)
                val job = currentCoroutineContext()
                repeat(3) {
                    blurPass(pixels, temporary, width, height, 1, width, radius) { job.ensureActive() }
                    blurPass(temporary, pixels, height, width, width, 1, radius) { job.ensureActive() }
                }
                job.ensureActive()
                val result = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
                result.prepareToDraw()
                appearances.put(AppearanceKey(source.absolutePath, maxSize, strength),
                    Rendered(source.length(), source.lastModified(), result))
                result
            }
        }
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
