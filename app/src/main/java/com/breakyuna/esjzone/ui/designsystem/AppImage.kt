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
import com.breakyuna.esjzone.app.PresentationAccess
import coil3.compose.SubcomposeAsyncImage
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.EnabledZoomGestures
import me.saket.telephoto.zoomable.ZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage

/**
 * Image loading seam; feature code must not depend on a concrete image-loader API.
 *
 * The loading and error slots are deliberately part of the facade. This keeps
 * image state predictable in cards and makes an offline/failure state visible
 * without every feature importing Coil. `model` may be a URL, URI, resource id,
 * or another model supported by the configured image loader. The loader is
 * application-scoped and owned by [com.breakyuna.esjzone.app.AppContainer];
 * callers must not create a feature-local ImageLoader.
 */
@Composable
fun AppImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    loading: @Composable () -> Unit = { AppImageLoading() },
    error: @Composable () -> Unit = { AppImageError() }
) {
    SubcomposeAsyncImage(
        model = model,
        imageLoader = PresentationAccess.imageLoader,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        alignment = alignment,
        loading = { loading() },
        error = { error() }
    )
}

/** Shared cover-image entry point for cards, shelves, detail heroes and downloads. */
@Composable
fun AppCoverImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    loading: @Composable () -> Unit = { AppImageLoading() },
    error: @Composable () -> Unit = { AppImageError() }
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
    loading: @Composable () -> Unit = { AppImageLoading() },
    error: @Composable () -> Unit = { AppImageError() }
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
    loading: @Composable () -> Unit = { AppImageLoading() },
    error: @Composable () -> Unit = { AppImageError() }
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
    Box(modifier = modifier) {
        ZoomableAsyncImage(
            model = model,
            imageLoader = PresentationAccess.imageLoader,
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
