package com.breakyuna.esjzone.ui.reader

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException

internal data class ReaderImageBackdrop(
    val painter: Painter? = null,
    val bitmap: android.graphics.Bitmap? = null,
    val loading: Boolean = false
) {
    val available: Boolean? get() = if (loading) null else painter != null
}

@Composable
internal fun rememberReaderImageBackdrop(background: ReaderImageBackground, enabled: Boolean): ReaderImageBackdrop {
    val context = LocalContext.current.applicationContext
    val source = remember(context, background.fileName) {
        ReaderImageBackgroundStore.file(context, background)
    }
    var result by remember(source, enabled, background.blur) {
        val cached = source?.takeIf { enabled }?.let { ReaderImageBackgroundStore.cachedAppearance(it, background.blur) }
        mutableStateOf(ReaderImageBackdrop(
            painter = cached?.let { BitmapPainter(it.asImageBitmap()) },
            bitmap = cached,
            loading = enabled && source != null && cached == null
        ))
    }
    LaunchedEffect(source, enabled, background.blur) {
        if (!enabled || source == null || result.painter != null) return@LaunchedEffect
        result = try {
            val bitmap = ReaderImageBackgroundStore.renderAppearance(source, background.blur)
            ReaderImageBackdrop(BitmapPainter(bitmap.asImageBitmap()), bitmap)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLogger.w("ReaderBackground", "Unable to load reader background", error)
            ReaderImageBackdrop()
        }
    }
    return result
}

internal fun Modifier.readerBackdrop(color: Color, painter: Painter?, transparency: Float = 0f): Modifier = background(color)
    .then(
        if (painter == null) Modifier
        else Modifier.clipToBounds().paint(
            painter, sizeToIntrinsics = false, contentScale = ContentScale.Crop,
            alpha = 1f - transparency.coerceIn(0f, 100f) / 100f
        )
    )
