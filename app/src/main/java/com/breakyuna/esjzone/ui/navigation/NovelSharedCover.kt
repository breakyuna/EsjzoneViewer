package com.breakyuna.esjzone.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.breakyuna.esjzone.novellibrary.novel.CoverState
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.ui.designsystem.AppMotion
import com.breakyuna.esjzone.ui.designsystem.AppLoadedImage
import java.util.UUID

internal data class SharedCoverOrigin(
    val token: String,
    val coverUrl: String?,
    val state: CoverState?,
    val image: AppLoadedImage? = null
)

internal val LocalSharedCoverDestinationEnabled = compositionLocalOf { true }

internal class NovelSharedCovers {
    val sources = mutableStateMapOf<String, SharedCoverOrigin>()
}

internal val LocalNovelSharedCovers = compositionLocalOf<NovelSharedCovers?> { null }

internal class NovelCoverSource(
    val modifier: Modifier,
    val onImageLoaded: (AppLoadedImage) -> Unit,
    val open: (() -> Unit) -> Unit
)

/** Each rendered occurrence has its own key, even when a book appears in several rails. */
@Composable
internal fun rememberNovelCoverSource(
    url: String,
    coverUrl: String? = null,
    state: CoverState? = null,
    enableSharedElement: Boolean = true
): NovelCoverSource {
    val identity = remember(url) { EsjzoneUrls.canonicalPageKey(url) }
    val token = rememberSaveable(identity) { UUID.randomUUID().toString() }
    val transitions = LocalNovelSharedCovers.current
    val image = remember(identity, coverUrl) { mutableStateOf<AppLoadedImage?>(null) }
    val modifier = if (enableSharedElement) Modifier.novelCoverSharedElement(token) else Modifier
    return NovelCoverSource(modifier, onImageLoaded = { image.value = it }) { action ->
        transitions?.sources?.set(identity, SharedCoverOrigin(token, coverUrl, state, image.value))
        action()
    }
}

@Composable
internal fun Modifier.novelCoverDestination(url: String): Modifier {
    val origin = rememberSharedCoverOrigin(url)
    return if (origin == null || !LocalSharedCoverDestinationEnabled.current) this
    else novelCoverSharedElement(origin.token)
}

@Composable
internal fun rememberSharedCoverOrigin(url: String, releaseOnDispose: Boolean = false): SharedCoverOrigin? {
    val transitions = LocalNovelSharedCovers.current
    val identity = remember(url) { EsjzoneUrls.canonicalPageKey(url) }
    val origin = remember(identity) { transitions?.sources?.get(identity) }
    if (releaseOnDispose) {
        DisposableEffect(identity, origin) {
            onDispose {
                // Owned by the page, so scrolling its lazy hero away does not release it.
                if (origin != null && transitions?.sources?.get(identity) === origin) {
                    transitions?.sources?.remove(identity)
                }
            }
        }
    }
    return origin
}

@Composable
private fun Modifier.novelCoverSharedElement(token: String): Modifier {
    val scope = LocalContainerTransformScope.current ?: return this
    val visibility = LocalNavAnimatedContentScope.current
    return with(scope) {
        sharedElement(
            sharedContentState = rememberSharedContentState("novel-cover:$token"),
            animatedVisibilityScope = visibility,
            boundsTransform = { _, _ -> tween(AppMotion.expressive, easing = AppMotion.standardEasing) },
            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(8.dp))
        )
    }
}
