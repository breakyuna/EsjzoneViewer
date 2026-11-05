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
    var result by remember(source, background.blur, enabled) {
        mutableStateOf(ReaderImageBackdrop(loading = enabled && source != null))
    }
    LaunchedEffect(source, background.blur, enabled) {
        if (!enabled || source == null) return@LaunchedEffect
        result = try {
            val bitmap = ReaderImageBackgroundStore.render(source, background.blur)
            ReaderImageBackdrop(BitmapPainter(bitmap.asImageBitmap()))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLogger.w("ReaderBackground", "Unable to load reader background", error)
            ReaderImageBackdrop()
        }
    }
    return result
}

internal fun Modifier.readerBackdrop(color: Color, painter: Painter?, imageAlpha: Float = 1f): Modifier = background(color)
    .then(
        if (painter == null) Modifier
        else Modifier.clipToBounds().paint(
            painter, sizeToIntrinsics = false, contentScale = ContentScale.Crop, alpha = imageAlpha
        )
    )
