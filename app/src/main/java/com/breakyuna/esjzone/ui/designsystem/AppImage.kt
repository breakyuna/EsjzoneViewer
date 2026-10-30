package com.breakyuna.esjzone.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.IconButton
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.CoverLoadingPolicy
import com.breakyuna.esjzone.app.LocalCoverPage
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.novellibrary.novel.CoverFailure
import com.breakyuna.esjzone.novellibrary.novel.CoverSource
import com.breakyuna.esjzone.novellibrary.novel.CoverState
import com.breakyuna.esjzone.novellibrary.novel.coverFailure
import coil3.compose.AsyncImage
import coil3.compose.asPainter
import coil3.compose.SubcomposeAsyncImage
import coil3.ImageLoader
import coil3.Image as CoilImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.ErrorResult
import coil3.network.HttpException
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.EnabledZoomGestures
import me.saket.telephoto.zoomable.ZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** An already decoded image that can be handed to another page without a new request. */
class AppLoadedImage internal constructor(internal val image: CoilImage)

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
    imageLoader: ImageLoader = PresentationAccess.imageLoader,
    onLoading: (() -> Unit)? = null,
    onSuccess: (() -> Unit)? = null,
    onError: ((Throwable) -> Unit)? = null,
    errorResource: Int? = null,
    onImageLoaded: ((AppLoadedImage) -> Unit)? = null
) {
    val imageRequest = sharedMirrorImageRequest(model)
    var errorState by remember(imageRequest) { mutableStateOf<CoverState>(CoverState.Missing) }
    val fallbackResource = errorResource ?: coverFallbackResource(errorState)
    val handleError: (Throwable) -> Unit = { failure ->
        errorState = CoverState.Failed(failure.imageFailure())
        onError?.invoke(failure)
    }
    if (loading != null || error != null) {
        SubcomposeAsyncImage(
            model = imageRequest,
            imageLoader = imageLoader,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            alignment = alignment,
            loading = { loading?.invoke() ?: AppImageLoading() },
            error = { error?.invoke() ?: AppImageError() },
            onLoading = { errorState = CoverState.Missing; onLoading?.invoke() },
            onSuccess = { onImageLoaded?.invoke(AppLoadedImage(it.result.image)); onSuccess?.invoke() },
            onError = { handleError(it.result.throwable) }
        )
    } else {
        AsyncImage(
            model = imageRequest,
            imageLoader = imageLoader,
            contentDescription = contentDescription,
            error = painterResource(fallbackResource),
            modifier = modifier,
            contentScale = contentScale,
            alignment = alignment,
            onLoading = { errorState = CoverState.Missing; onLoading?.invoke() },
            onSuccess = { onImageLoaded?.invoke(AppLoadedImage(it.result.image)); onSuccess?.invoke() },
            onError = { handleError(it.result.throwable) }
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
    error: (@Composable () -> Unit)? = null,
    sourceState: CoverState? = null,
    cachedModel: Any? = null,
    initialImage: AppLoadedImage? = null,
    onImageLoaded: ((AppLoadedImage) -> Unit)? = null
) {
    val policy by CoverLoadingPolicy.state.collectAsState()
    val page = LocalCoverPage.current
    val allowed = page.isBlank() || policy.allowNetwork(page)
    val source = sourceState ?: when (model) {
        null -> CoverState.Unknown
        is String -> CoverSource.fromUrl(model).state
        else -> CoverState.Loading
    }
    val hasImage = source == CoverState.Loading || source == CoverState.Loaded
    val request = sharedMirrorImageRequest(if (hasImage) model else null, allowed)
    if (cachedModel != null || initialImage != null) {
        CachedCoverImage(
            request = request,
            cachedRequest = sharedMirrorImageRequest(cachedModel ?: model, allowNetwork = false),
            initialImage = initialImage,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
        return
    }
    var imageState by remember(request, source) { mutableStateOf(source) }
    var retained by remember(contentDescription) { mutableStateOf<AppLoadedImage?>(null) }
    var retry by remember(model) { mutableStateOf(0) }
    val context = LocalContext.current
    Box(modifier) {
        if (retained == null) {
            when {
                imageState == CoverState.Loading && loading != null -> loading()
                (imageState is CoverState.Failed || !hasImage) && error != null -> error()
                else -> AppCoverPlaceholder(if (hasImage) imageState else source)
            }
        }
        if (hasImage) key(retry) {
            AsyncImage(model = request, imageLoader = PresentationAccess.imageLoader,
                placeholder = retained?.image?.asPainter(context), error = retained?.image?.asPainter(context),
                contentDescription = contentDescription, modifier = Modifier.fillMaxSize(), contentScale = contentScale,
                onLoading = { imageState = CoverState.Loading },
                onSuccess = {
                    imageState = CoverState.Loaded
                    retained = AppLoadedImage(it.result.image)
                    onImageLoaded?.invoke(retained!!)
                },
                onError = { imageState = CoverState.Failed(if (allowed) it.result.throwable.imageFailure() else CoverFailure.UNAVAILABLE) })
        }
        if (!hasImage) retained?.let {
            Image(it.image.asPainter(context), contentDescription, Modifier.fillMaxSize(), contentScale = contentScale)
        }
        if (retained == null && imageState is CoverState.Failed && hasImage && allowed && error == null) {
            AppCoverPlaceholder(imageState, onRetry = { retry++ })
        }
    }

}

/** Keep the entry cover visible until a replacement has actually decoded. */
@Composable
internal fun CachedCoverImage(
    request: ImageRequest,
    cachedRequest: ImageRequest,
    initialImage: AppLoadedImage? = null,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    imageLoader: ImageLoader = PresentationAccess.imageLoader
) {
    val context = LocalContext.current
    // Match the durable shelf thumbnail bound rather than decode a full-size hero.
    val sizedRequest = remember(request) { request.newBuilder().size(720).build() }
    val sizedCachedRequest = remember(cachedRequest) { cachedRequest.newBuilder().size(720).build() }
    var image by remember(cachedRequest.data) { mutableStateOf(initialImage?.image) }
    var failure by remember(request) { mutableStateOf<CoverState>(CoverState.Loading) }
    var retry by remember(request) { mutableStateOf(0) }
    LaunchedEffect(sizedRequest, sizedCachedRequest, retry) {
        failure = CoverState.Loading
        loadCachedCover(sizedRequest, sizedCachedRequest, imageLoader, restoreCached = image == null,
            onImage = { image = it },
            onError = { failure = CoverState.Failed(it.imageFailure()) })
    }
    Box(modifier) {
        if (image != null) Image(
            painter = image!!.asPainter(context), contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(), contentScale = contentScale
        ) else AppCoverPlaceholder(failure, onRetry = if (failure is CoverState.Failed && request.data != null) ({ retry++ }) else null)
    }

}

@Composable
private fun AppCoverPlaceholder(state: CoverState, onRetry: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow), contentAlignment = Alignment.Center) {
        if (onRetry != null) IconButton(onClick = onRetry) {
            Icon(Icons.Outlined.Refresh, globalStringResource(R.string.cover_retry),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else Icon(if (state is CoverState.Failed) Icons.Outlined.BrokenImage else Icons.Outlined.MenuBook,
            contentDescription = globalStringResource(if (state is CoverState.Failed) R.string.cover_unavailable else R.string.cover_placeholder),
            modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f))
    }
}

internal suspend fun loadCachedCover(
    request: ImageRequest,
    cachedRequest: ImageRequest,
    imageLoader: ImageLoader,
    restoreCached: Boolean,
    onImage: (CoilImage) -> Unit,
    onError: (Throwable) -> Unit
) {
    if (restoreCached) {
        val cached = imageLoader.execute(cachedRequest.newBuilder()
            .networkCachePolicy(CachePolicy.DISABLED).build()) as? SuccessResult
        if (cached != null) onImage(cached.image)
    }
    when (val fresh = imageLoader.execute(request)) {
        is SuccessResult -> onImage(fresh.image)
        is ErrorResult -> onError(fresh.throwable)
    }
}

/** Only confirmed network failures use the network-error artwork. */
private fun coverFallbackResource(state: CoverState): Int = when (state) {
    is CoverState.Failed -> when (state.reason) {
        CoverFailure.TIMEOUT, CoverFailure.NETWORK -> R.drawable.cover_network_error
        CoverFailure.UNAVAILABLE, CoverFailure.OTHER -> R.drawable.cover_missing
    }
    else -> R.drawable.cover_missing
}

/** Coil's HTTP errors are known request failures, unlike generic file/decoder IO errors. */
private fun Throwable.imageFailure(): CoverFailure {
    var current: Throwable? = this
    val seen = mutableSetOf<Throwable>()
    while (current != null && seen.add(current)) {
        if (current is HttpException) return CoverFailure.NETWORK
        current = current.cause
    }
    return coverFailure()
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
    onLongClick: ((Offset) -> Unit)? = null,
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
            onLongClick = onLongClick,
            onDoubleClick = onDoubleClick
        )
        if (!state.isImageDisplayed) {
            loading()
        }
    }
}

@Composable
internal fun sharedMirrorImageRequest(model: Any?, allowNetwork: Boolean = true): ImageRequest {
    val context = LocalContext.current
    val activeDomain = PresentationAccess.settings.domain.value
    return remember(model, context, activeDomain, allowNetwork) {
        if (model is ImageRequest) {
            if (allowNetwork) model else model.newBuilder()
                .networkCachePolicy(CachePolicy.DISABLED).build()
        }
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
                .networkCachePolicy(if (allowNetwork) CachePolicy.ENABLED else CachePolicy.DISABLED)
                .build()
        }
    }
}

@Composable
internal fun readerImageLoader(model: Any?): ImageLoader {
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
