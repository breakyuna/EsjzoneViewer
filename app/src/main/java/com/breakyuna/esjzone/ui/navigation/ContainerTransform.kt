package com.breakyuna.esjzone.ui.navigation

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.ui.page.ForumPage
import com.breakyuna.esjzone.ui.page.GuestbookPage
import com.breakyuna.esjzone.ui.page.SettingsPage

private const val ContainerTransformDurationMillis = 300
private val LocalContainerTransformScope = compositionLocalOf<SharedTransitionScope?> { null }

internal data class ContainerTransformTarget(
    val name: String,
    val origin: AppNavKey,
    val route: LegacyRoute,
    val cornerRadius: Dp,
    val homeCarouselPage: Int? = null
) {
    companion object {
        val FORUM = ContainerTransformTarget("FORUM", AppNavKey.HomeTab, LegacyRoute.Static(ForumPage.key), 18.dp)
        val GUESTBOOK = ContainerTransformTarget("GUESTBOOK", AppNavKey.HomeTab, LegacyRoute.Static(GuestbookPage.key), 18.dp)
        val WATER_COOLER = ContainerTransformTarget("WATER_COOLER", AppNavKey.HomeTab, LegacyRoute.ForumPost(EsjzoneUrls.WATER_COOLER_PATH), 18.dp)
        val SETTINGS = ContainerTransformTarget("SETTINGS", AppNavKey.ProfileTab, LegacyRoute.Static(SettingsPage.key), 16.dp)
        private val shortcuts = listOf(FORUM, GUESTBOOK, WATER_COOLER, SETTINGS)

        fun homeCarousel(novelIdentity: String, page: Int) = ContainerTransformTarget(
            name = "HOME_CAROUSEL",
            origin = AppNavKey.HomeTab,
            route = LegacyRoute.Novel(novelIdentity),
            cornerRadius = 26.dp,
            homeCarouselPage = page
        )

        fun forKey(key: Any?): ContainerTransformTarget? {
            val destination = key as? AppNavKey.Legacy ?: return null
            val route = destination.route
            val page = destination.homeCarouselPage
            return if (route is LegacyRoute.Novel && page != null) {
                homeCarousel(route.identity, page)
            } else {
                shortcuts.firstOrNull { it.route == route }
            }
        }
    }
}

/** Only linked home/profile cards replace the ordinary navigation transition. */
internal fun usesContainerTransform(from: Any?, to: Any?): Boolean =
    ContainerTransformTarget.forKey(to)?.let { it.origin == from } == true ||
        ContainerTransformTarget.forKey(from)?.let { it.origin == to } == true

internal fun containerContentTransform() =
    fadeIn(tween(ContainerTransformDurationMillis)) togetherWith
        fadeOut(tween(ContainerTransformDurationMillis))

@Composable
internal fun ContainerTransformLayout(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    SharedTransitionLayout(modifier) {
        CompositionLocalProvider(LocalContainerTransformScope provides this) { content() }
    }
}

@Composable
internal fun Modifier.containerTransformSource(target: ContainerTransformTarget): Modifier =
    containerTransformBounds(target, fullScreen = false)

/** Keep card/page contents at their own final size while only the outer bounds change. */
@Composable
internal fun Modifier.containerTransformContent(): Modifier {
    val sharedScope = LocalContainerTransformScope.current ?: return this
    return with(sharedScope) { skipToLookaheadSize() }
}

@Composable
private fun Modifier.containerTransformBounds(
    target: ContainerTransformTarget,
    fullScreen: Boolean
): Modifier {
    val sharedScope = LocalContainerTransformScope.current ?: return this
    val visibilityScope = LocalNavAnimatedContentScope.current
    return with(sharedScope) {
        val sharedState = rememberSharedContentState(target)
        val cornerRadius = visibilityScope.transition.animateDp(
            transitionSpec = {
                tween(ContainerTransformDurationMillis, easing = FastOutSlowInEasing)
            },
            label = "${target.name}ContainerCorner"
        ) { state ->
            when {
                fullScreen && state == EnterExitState.Visible -> 0.dp
                !fullScreen && state != EnterExitState.Visible -> 0.dp
                else -> target.cornerRadius
            }
        }
        val radius = remember(sharedState, cornerRadius, fullScreen, target.cornerRadius) {
            {
                if (sharedState.isMatchFound) cornerRadius.value
                else if (fullScreen) 0.dp else target.cornerRadius
            }
        }
        // OverlayClip owns a reusable Path. Read the radius when it builds the outline,
        // and in the graphics-layer block, rather than recomposing on every frame.
        val overlayClip = remember(radius) {
            OverlayClip(object : Shape {
                override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
                    RoundedCornerShape(radius()).createOutline(size, layoutDirection, density)
            })
        }
        sharedBounds(
            sharedContentState = sharedState,
            animatedVisibilityScope = visibilityScope,
            boundsTransform = { _, _ ->
                tween(ContainerTransformDurationMillis, easing = FastOutSlowInEasing)
            },
            enter = fadeIn(tween(ContainerTransformDurationMillis)),
            exit = fadeOut(tween(ContainerTransformDurationMillis)),
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            clipInOverlayDuringTransition = overlayClip
        ).graphicsLayer {
            shape = RoundedCornerShape(radius())
            clip = true
        }
    }
}

@Composable
internal fun ContainerTransformDestination(
    target: ContainerTransformTarget,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier
            .containerTransformBounds(target, fullScreen = true)
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Reveal the page at its final layout size, rather than reflowing or scaling its text.
        Box(Modifier.containerTransformContent().fillMaxSize()) { content() }
    }
}
