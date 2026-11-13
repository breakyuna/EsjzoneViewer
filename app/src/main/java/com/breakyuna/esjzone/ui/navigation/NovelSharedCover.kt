package com.breakyuna.esjzone.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animate
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventTransitionState.InProgress
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.breakyuna.esjzone.novellibrary.novel.CoverState
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppMotion
import com.breakyuna.esjzone.ui.designsystem.AppLoadedImage
import java.util.UUID
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate

internal class SharedCoverOrigin(
    val token: String,
    val coverUrl: String?,
    val state: CoverState?,
    image: AppLoadedImage? = null
) {
    var image by mutableStateOf(image)
}

internal val LocalSharedCoverDestinationEnabled = compositionLocalOf { true }

internal class NovelSharedCovers {
    val sources = mutableStateMapOf<String, SharedCoverOrigin>()
}

internal val LocalNovelSharedCovers = compositionLocalOf<NovelSharedCovers?> { null }

internal class NovelCoverSource(
    val modifier: Modifier,
    val initialImage: AppLoadedImage?,
    val onImageLoaded: (AppLoadedImage) -> Unit,
    val open: (() -> Unit) -> Unit
)

/** Each rendered occurrence has its own key, even when a book appears in several rails. */
@Composable
internal fun rememberNovelCoverSource(
    url: String,
    coverUrl: String? = null,
    state: CoverState? = null,
    enableSharedElement: Boolean = true,
    cachedImage: AppLoadedImage? = null
): NovelCoverSource {
    val identity = remember(url) { EsjzoneUrls.canonicalPageKey(url) }
    val token = rememberSaveable(identity) { UUID.randomUUID().toString() }
    val transitions = LocalNovelSharedCovers.current
    // Restore only the clicked occurrence, even while its local URL is resolving.
    // Keep this reference after the outgoing detail page releases the registry entry.
    val restoredImage = remember(identity, token) {
        transitions?.sources?.get(identity)?.takeIf { it.token == token }?.image
    }
    // Shelf layout handoffs must seed both the visible painter and the next detail entry.
    val initialImage = restoredImage ?: cachedImage
    val image = remember(identity, coverUrl) { mutableStateOf(initialImage) }
    val modifier = if (enableSharedElement) Modifier.novelCoverSharedElement(token) else Modifier
    return NovelCoverSource(modifier, initialImage, onImageLoaded = { image.value = it }) { action ->
        transitions?.sources?.set(identity, SharedCoverOrigin(token, coverUrl, state, image.value ?: initialImage))
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
    val navigationTransition = LocalNavAnimatedContentScope.current.transition
    val dispatcher = checkNotNull(LocalNavigationEventDispatcherOwner.current).navigationEventDispatcher
    // Observe the same gesture as NavDisplay without registering another back handler.
    val gesture = dispatcher.transitionState.collectAsStateWithLifecycle()
    val coverState = remember(navigationTransition, token) {
        SeekableTransitionState(navigationTransition.currentState)
    }
    val boundsMotion = rememberTransition(coverState, label = "NovelCover:$token")
    var gestureDriven by remember(coverState) { mutableStateOf(false) }
    val visibility = remember(boundsMotion) {
        object : AnimatedVisibilityScope {
            override val transition = boundsMotion
        }
    }
    LaunchedEffect(navigationTransition, coverState, gesture) {
        snapshotFlow {
            val pending = navigationTransition.pendingTargetState
            val event = gesture.value as? InProgress
            val progress = if (pending != null && event != null) event.latestEvent.progress else null
            // Pending is authoritative only while the gesture is active. On cancellation,
            // the dispatcher can become idle before NavDisplay clears its pending target.
            (if (progress != null) requireNotNull(pending) else navigationTransition.targetState) to progress
        }.collectLatest { (target, progress) ->
            if (progress != null) {
                gestureDriven = true
                coverState.seekTo(progress.coerceIn(0f, 1f), target)
            } else if (gestureDriven && target == coverState.currentState && target != coverState.targetState) {
                // Reversing animateTo's target also animates its old initial values towards
                // the abandoned endpoint. Seek the original segment backwards instead so
                // cancellation cannot briefly move further towards the source card.
                val gestureTarget = coverState.targetState
                val fraction = coverState.fraction
                channelFlow {
                    animate(fraction, 0f, animationSpec = tween(AppMotion.expressive, easing = AppMotion.standardEasing)) {
                        value, _ -> trySend(value)
                    }
                }.conflate().collect { coverState.seekTo(it, gestureTarget) }
                coverState.snapTo(target)
                gestureDriven = false
            } else {
                // Keep the linear bounds spec through handoff; changing it at release would
                // remap the sampled gesture fraction and jump the cover to a different point.
                coverState.animateTo(target, animationSpec = if (gestureDriven) {
                    tween(AppMotion.expressive, easing = AppMotion.standardEasing)
                } else null)
                gestureDriven = false
            }
        }
    }
    return with(scope) {
        sharedElement(
            sharedContentState = rememberSharedContentState("novel-cover:$token", IndependentSharedCoverConfig),
            animatedVisibilityScope = visibility,
            boundsTransform = { _, _ ->
                tween(AppMotion.expressive, easing = if (gestureDriven) LinearEasing else AppMotion.standardEasing)
            },
            clipInOverlayDuringTransition = OverlayClip(AppShapes.cover)
        )
    }
}

// The independently seekable cover owns its shared bounds path; page offsets never apply to it.
private object IndependentSharedCoverConfig : SharedTransitionScope.SharedContentConfig {
    override val permitTransformDuringDeferredTransition: Boolean = false
}
