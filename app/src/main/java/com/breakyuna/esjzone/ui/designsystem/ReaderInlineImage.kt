package com.breakyuna.esjzone.ui.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.compose.asPainter
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Dimension
import coil3.size.Precision
import coil3.size.Scale
import coil3.size.Size

/** Width-sized illustrations share Coil's bounded cache across lazy-item disposal. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReaderInlineImage(
    request: ImageRequest,
    imageLoader: ImageLoader,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    loading: (@Composable () -> Unit)? = null,
    error: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth.coerceAtLeast(1)
            else context.resources.displayMetrics.widthPixels.coerceAtLeast(1)
        val sizedRequest = remember(request, width) {
            request.newBuilder()
                .size(Size(Dimension(width), Dimension.Undefined))
                .scale(Scale.FIT)
                .precision(Precision.EXACT)
                // Inline previews must not overwrite the full-screen image's cache entry.
                .memoryCacheKey("reader-inline:$width:${request.memoryCacheKey ?: request.data}")
                .build()
        }
        var image by remember(sizedRequest, imageLoader) {
            mutableStateOf(imageLoader.memoryCache?.get(MemoryCache.Key(
                requireNotNull(sizedRequest.memoryCacheKey), sizedRequest.memoryCacheKeyExtras
            ))?.image)
        }
        var failed by remember(sizedRequest, imageLoader) { mutableStateOf(false) }
        LaunchedEffect(sizedRequest, imageLoader) {
            if (image == null) {
                when (val result = imageLoader.execute(sizedRequest)) {
                    is SuccessResult -> image = result.image
                    else -> failed = true
                }
            }
        }
        val loaded = image
        when {
            loaded != null -> Image(
                painter = loaded.asPainter(context),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
            failed -> error?.invoke() ?: AppImageError()
            else -> loading?.invoke() ?: AppImageLoading()
        }
    }
}
