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
        val encoded = if (staticBitmap != null) {
            @Suppress("DEPRECATION")
            val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            pending.outputStream().use { staticBitmap.compress(format, 82, it) } && pending.length() > 0L
        } else {
            false
        }
        // Keep animated covers intact instead of silently turning them into a still image.
        if (!encoded) {
            source.copyTo(pending, overwrite = true)
        }
        require(pending.length() > 0L) { "Empty bookshelf thumbnail" }
        beforeCommit()
        try {
            Files.move(pending.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(pending.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        bitmap?.recycle()
        pending.delete()
    }
}
