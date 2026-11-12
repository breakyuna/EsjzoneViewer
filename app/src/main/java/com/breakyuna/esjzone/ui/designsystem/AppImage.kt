package com.breakyuna.esjzone.ui.designsystem

import coil3.request.crossfade
import androidx.compose.foundation.layout.Box
import android.content.Context
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
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
import coil3.memory.MemoryCache
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
    val imageRequest = sharedMirrorImageRequest(model, animateLoad = true)
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
    onImageLoaded: ((AppLoadedImage) -> Unit)? = null,
    replacementEnabled: Boolean = true,
    refreshFromNetwork: Boolean = false,
    onRefreshSuccess: (() -> Unit)? = null
) {
    val policy by CoverLoadingPolicy.state.collectAsStateWithLifecycle()
    val page = LocalCoverPage.current
    val allowed = page.isBlank() || policy.allowNetwork(page)
    val source = sourceState ?: when (model) {
        null -> CoverState.Unknown
        is String -> CoverSource.fromUrl(model).state
        else -> CoverState.Loading
    }
    val hasImage = source == CoverState.Loading || source == CoverState.Loaded
    val baseRequest = sharedMirrorImageRequest(if (hasImage) model else coverFallbackResource(source), allowed)
    val refreshing = refreshFromNetwork && allowed && hasImage &&
        (baseRequest.data as? String)?.toHttpUrlOrNull() != null
    val request = remember(baseRequest, refreshing) {
        if (refreshing) baseRequest.newBuilder()
            .memoryCachePolicy(CachePolicy.WRITE_ONLY)
            .diskCachePolicy(CachePolicy.WRITE_ONLY)
            .build() else baseRequest
    }
    if (cachedModel != null || initialImage != null || refreshFromNetwork || !replacementEnabled) {
        // Fallback artwork is a display state, not a decoded cover replacement.
        CachedCoverImage(
            request = request.takeIf { hasImage },
            cachedRequest = sharedMirrorImageRequest(cachedModel ?: model, allowNetwork = false)
                .takeIf { cachedModel != null || hasImage },
            initialImage = initialImage,
            onImageLoaded = onImageLoaded,
            onRefreshSuccess = onRefreshSuccess.takeIf { refreshing },
            loadEnabled = source != CoverState.Resolving && replacementEnabled,
            fallbackState = source.takeIf { !hasImage && it != CoverState.Resolving },
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
        return
    }
    var imageState by remember(request, source) { mutableStateOf(source) }
    val state = if (hasImage) imageState else source
    AppImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        loading = loading,
        error = error,
        onLoading = { if (hasImage) imageState = CoverState.Loading },
        onSuccess = { if (hasImage) imageState = CoverState.Loaded },
        onError = { failure ->
            if (hasImage) imageState = CoverState.Failed(
                if (allowed) failure.imageFailure() else CoverFailure.UNAVAILABLE
            )
        },
        errorResource = coverFallbackResource(state),
        onImageLoaded = { if (hasImage) onImageLoaded?.invoke(it) }
    )
}

/** Keep the entry cover visible until a replacement has actually decoded. */
@Composable
internal fun CachedCoverImage(
    request: ImageRequest?,
    cachedRequest: ImageRequest?,
    initialImage: AppLoadedImage? = null,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    imageLoader: ImageLoader = PresentationAccess.imageLoader,
    onImageLoaded: ((AppLoadedImage) -> Unit)? = null,
    loadEnabled: Boolean = true,
    fallbackState: CoverState? = null,
    onRefreshSuccess: (() -> Unit)? = null
) {
    val context = LocalContext.current
    // Match the durable shelf thumbnail bound rather than decode a full-size hero.
    val sizedRequest = remember(request) { request?.newBuilder()?.size(720)?.build() }
    val sizedCachedRequest = remember(cachedRequest) { cachedRequest?.newBuilder()?.size(720)?.build() }
    // Seed synchronously so the first transition frame never shows placeholder artwork,
    // and keep the last decoded image when the hero swaps to the detail cover URL.
    var image by remember {
        mutableStateOf(initialImage?.image ?: cachedRequest?.let {
            imageLoader.memoryCache?.get(MemoryCache.Key(it.memoryCacheKey ?: it.data.toString()))?.image
        })
    }
    var reportSeededImage by remember { mutableStateOf(initialImage == null && image != null) }
    var failure by remember(request) { mutableStateOf<CoverState?>(null) }
    val replacementAllowed by rememberUpdatedState(loadEnabled)
    // Local artwork may be restored during entry motion, without starting a remote request.
    LaunchedEffect(sizedRequest, sizedCachedRequest, loadEnabled) {
        if (reportSeededImage) {
            reportSeededImage = false
            image?.let { onImageLoaded?.invoke(AppLoadedImage(it)) }
        }
        loadCachedCover(sizedRequest, sizedCachedRequest, imageLoader, restoreCached = image == null,
            onImage = {
                if (image == null || replacementAllowed) {
                    image = it
                    onImageLoaded?.invoke(AppLoadedImage(it))
                }
            },
            onError = { if (replacementAllowed) failure = CoverState.Failed(it.imageFailure()) },
            onRefreshSuccess = { if (replacementAllowed) onRefreshSuccess?.invoke() },
            loadReplacement = loadEnabled)
    }
    val loadedImage = image
    val fallback = failure ?: fallbackState
    Image(
        painter = when {
            loadedImage != null -> loadedImage.asPainter(context)
            fallback != null -> painterResource(coverFallbackResource(fallback))
            // Draw nothing while decoding instead of flashing the missing-cover artwork.
            else -> EmptyPainter
        },
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}

private val EmptyPainter = ColorPainter(Color.Transparent)

internal suspend fun loadCachedCover(
    request: ImageRequest?,
    cachedRequest: ImageRequest?,
    imageLoader: ImageLoader,
    restoreCached: Boolean,
    onImage: (CoilImage) -> Unit,
    onError: (Throwable) -> Unit,
    onRefreshSuccess: (() -> Unit)? = null,
    loadReplacement: Boolean = true
) {
    if (restoreCached) {
        // An unavailable entry thumbnail must not hide a usable remote-image cache.
        val remoteCacheRequest = request?.takeIf { (it.data as? String)?.toHttpUrlOrNull() != null }
        for (candidate in listOfNotNull(cachedRequest, remoteCacheRequest).distinctBy { it.data }) {
            val cached = imageLoader.execute(candidate.newBuilder()
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.READ_ONLY)
                .networkCachePolicy(CachePolicy.DISABLED).build()) as? SuccessResult
            if (cached != null) {
                onImage(cached.image)
                break
            }
        }
    }
    if (loadReplacement && request != null) {
        when (val fresh = imageLoader.execute(request)) {
            is SuccessResult -> {
                onImage(fresh.image)
                onRefreshSuccess?.invoke()
            }
            is ErrorResult -> onError(fresh.throwable)
        }
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
internal fun Throwable.imageFailure(): CoverFailure {
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
    if (contentScale == ContentScale.FillWidth) {
        ReaderInlineImage(sharedMirrorImageRequest(model), loader, contentDescription, modifier, loading, error)
        return
    }
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
    val imageRequest = sharedMirrorImageRequest(model, animateLoad = true)
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
internal fun sharedMirrorImageRequest(model: Any?, allowNetwork: Boolean = true, animateLoad: Boolean = false): ImageRequest {
    val context = LocalContext.current
    val activeDomain = PresentationAccess.settings.domain.value
    val reduced = animateLoad && LocalReduceMotion.current
    return remember(model, context, activeDomain, allowNetwork, animateLoad, reduced) {
        val request = mirrorImageRequest(context, model, allowNetwork)
        if (animateLoad) request.newBuilder().crossfade(if (reduced) 0 else AppMotion.quick).build()
        else request
    }
}

internal fun mirrorImageRequest(context: Context, model: Any?, allowNetwork: Boolean): ImageRequest {
    if (model is ImageRequest) {
        return if (allowNetwork) model else model.newBuilder()
            .networkCachePolicy(CachePolicy.DISABLED).build()
    }
    val rawUrl = model as? String
    val parsed = rawUrl?.toHttpUrlOrNull()
    val isMirrorImage = parsed?.host?.let(EsjzoneUrls::isEsjHost) == true
    val requestData = if (isMirrorImage) EsjzoneUrls.resolve(rawUrl.orEmpty()) else model
    return ImageRequest.Builder(context)
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

@Composable
internal fun readerImageLoader(model: Any?): ImageLoader {
    val url = model as? String
    val parsed = remember(url) { url?.toHttpUrlOrNull() }
    return if (parsed?.isHttps == true && parsed.host == "www.wenku8.net" &&
        parsed.username.isEmpty() && parsed.password.isEmpty()) {
        PresentationAccess.wenkuImageLoader
    } else PresentationAccess.imageLoader
}

@Composable
fun rememberReaderReducedMotion(): Boolean = LocalReduceMotion.current

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
