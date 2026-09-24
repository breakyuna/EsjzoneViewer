package com.breakyuna.esjzone.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.network.EsjzoneUrls
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.EnabledZoomGestures
import me.saket.telephoto.zoomable.ZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Image loading seam; feature code must not depend on a concrete image-loader API.
 *
 * For standard cards, lists, covers and avatars, [AsyncImage] is used to benefit
 * from layout prefetching and avoid subcomposition overhead during list scrolling.
 * Callers that supply custom dynamic slot composables will use [SubcomposeAsyncImage].
 */
@Composable
fun AppImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    loading: (@Composable () -> Unit)? = null,
    error: (@Composable () -> Unit)? = null,
    imageLoader: ImageLoader = PresentationAccess.imageLoader
) {
    val imageRequest = sharedMirrorImageRequest(model)
    if (loading != null || error != null) {
        SubcomposeAsyncImage(
            model = imageRequest,
            imageLoader = imageLoader,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            alignment = alignment,
            loading = { loading?.invoke() ?: AppImageLoading() },
            error = { error?.invoke() ?: AppImageError() }
        )
    } else {
        AsyncImage(
            model = imageRequest,
            imageLoader = imageLoader,
            contentDescription = contentDescription,
            error = painterResource(R.drawable.missing_cover),
            modifier = modifier,
            contentScale = contentScale,
            alignment = alignment
        )
    }
}

/** Shared cover-image entry point for cards, shelves, detail heroes and downloads. */
@Composable
fun AppCoverImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    loading: (@Composable () -> Unit)? = null,
    error: (@Composable () -> Unit)? = null
) {
    AppImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        loading = loading,
        error = error
    )
}

/**
 * Shared avatar entry point. Keeping avatars behind the same facade ensures
 * profile and comment images use the application cache just like other remote
 * images, while callers remain free to provide their own fallback content.
 */
@Composable
fun AppAvatarImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    loading: (@Composable () -> Unit)? = null,
    error: (@Composable () -> Unit)? = null
) {
    AppImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        loading = loading,
        error = error
    )
}

/**
 * Reader image entry point. Inline and full-screen reader images deliberately
 * share the same app-scoped loader and cache; only their content scale differs.
 */
@Composable
fun AppReaderImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.FillWidth,
    loading: (@Composable () -> Unit)? = null,
    error: (@Composable () -> Unit)? = null
) {
    val loader = readerImageLoader(model)
    AppImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        loading = loading,
        error = error,
        imageLoader = loader
    )
}

/**
 * Reader-only zooming facade. Telephoto owns gesture handling and large-image
 * subsampling while the application-scoped Coil loader remains the single
 * source of cache and network policy.
 *
 * The caller can disable gestures for reduced-motion environments and retain
 * the regular [AppReaderImage] as a static rendering fallback. Clicks are
 * exposed by Telephoto because its gesture detector intentionally consumes
 * ordinary clickable modifiers.
 */
@Composable
fun AppReaderZoomableImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    state: ZoomableImageState = rememberZoomableImageState(rememberZoomableState()),
    gestures: EnabledZoomGestures = EnabledZoomGestures.ZoomAndPan,
    onClick: ((Offset) -> Unit)? = null,
    onDoubleClick: DoubleClickToZoomListener = DoubleClickToZoomListener.cycle(),
    loading: @Composable () -> Unit = { AppImageLoading() }
) {
    val loader = readerImageLoader(model)
    val imageRequest = sharedMirrorImageRequest(model)
    Box(modifier = modifier) {
        ZoomableAsyncImage(
            model = imageRequest,
            imageLoader = loader,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            state = state,
            contentScale = contentScale,
            gestures = gestures,
            onClick = onClick,
            onDoubleClick = onDoubleClick
        )
        if (!state.isImageDisplayed) {
            loading()
        }
    }
}

@Composable
private fun sharedMirrorImageRequest(model: Any?): ImageRequest {
    val context = LocalContext.current
    val activeDomain = PresentationAccess.settings.domain.value
    return remember(model, context, activeDomain) {
        if (model is ImageRequest) model
        else {
            val rawUrl = model as? String
            val parsed = rawUrl?.toHttpUrlOrNull()
            val isMirrorImage = parsed?.host?.let(EsjzoneUrls::isEsjHost) == true
            val requestData = if (isMirrorImage) EsjzoneUrls.resolve(rawUrl.orEmpty()) else model
            ImageRequest.Builder(context)
                .data(requestData)
                .apply {
                    if (isMirrorImage) {
                        val key = EsjzoneUrls.canonicalCacheUrl(rawUrl.orEmpty())
                        memoryCacheKey(key)
                        diskCacheKey(key)
                    }
                }
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .build()
        }
    }
}

@Composable
private fun readerImageLoader(model: Any?): ImageLoader {
    val url = model as? String
    val parsed = remember(url) { url?.toHttpUrlOrNull() }
    return if (parsed?.isHttps == true && parsed.host == "www.wenku8.net" &&
        parsed.username.isEmpty() && parsed.password.isEmpty()) {
        PresentationAccess.wenkuImageLoader
    } else PresentationAccess.imageLoader
}

/**
 * Android's zero animation scale is the platform-level reduced-motion signal
 * available without an accessibility permission. It is intentionally read
 * once per composition: changing the developer/accessibility setting while
 * this screen is open takes effect on the next screen composition.
 */
@Composable
fun rememberReaderReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val resolver = context.contentResolver
        val animatorScale = android.provider.Settings.Global.getFloat(
            resolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        val transitionScale = android.provider.Settings.Global.getFloat(
            resolver,
            android.provider.Settings.Global.TRANSITION_ANIMATION_SCALE,
            1f
        )
        animatorScale == 0f || transitionScale == 0f
    }
}

@Composable
fun AppImageLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier,
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun AppImageError(
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Outlined.BrokenImage,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
