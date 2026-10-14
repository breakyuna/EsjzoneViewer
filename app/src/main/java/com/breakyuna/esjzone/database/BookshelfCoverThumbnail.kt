package com.breakyuna.esjzone.database

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.math.roundToInt

/** Generates bounded shelf artwork on IO; full-size viewers keep using the remote cover URL. */
internal fun writeBookshelfThumbnail(source: File, target: File, beforeCommit: () -> Unit = {}) {
    val directory = requireNotNull(target.parentFile)
    val webp = File.createTempFile("shelf-cover-", ".webp.tmp", directory)
    val jpeg = File.createTempFile("shelf-cover-", ".jpg.tmp", directory)
    val pending = File.createTempFile("shelf-cover-", ".img.tmp", directory)
    var bitmap: Bitmap? = null
    try {
        val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(source)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val scale = minOf(1.0, 720.0 / maxOf(info.size.width, info.size.height))
            decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1),
                (info.size.height * scale).roundToInt().coerceAtLeast(1))
        }
        bitmap = (drawable as? BitmapDrawable)?.bitmap
        val staticBitmap = bitmap
        val candidates = mutableListOf(source)
        // Keep animated covers intact instead of silently turning them into a still image.
        if (staticBitmap != null) {
            @Suppress("DEPRECATION")
            val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            webp.outputStream().use { if (staticBitmap.compress(format, 82, it)) candidates += webp }
            if (!staticBitmap.hasAlpha()) {
                jpeg.outputStream().use { if (staticBitmap.compress(Bitmap.CompressFormat.JPEG, 85, it)) candidates += jpeg }
            }
        }
        val smallest = requireNotNull(candidates.filter { it.length() > 0L }.minByOrNull(File::length))
        smallest.copyTo(pending, overwrite = true)
        beforeCommit()
        try {
            Files.move(pending.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(pending.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        bitmap?.recycle()
        webp.delete()
        jpeg.delete()
        pending.delete()
    }
}
