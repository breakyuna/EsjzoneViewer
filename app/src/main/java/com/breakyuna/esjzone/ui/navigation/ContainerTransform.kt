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
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.ui.page.BookmarksPage
import com.breakyuna.esjzone.ui.page.DownloadPage
import com.breakyuna.esjzone.ui.page.ForumPage
import com.breakyuna.esjzone.ui.page.GuestbookPage
import com.breakyuna.esjzone.ui.page.ReadingStatisticsPage
import com.breakyuna.esjzone.ui.page.SettingsPage

private const val ContainerTransformDurationMillis = 400
internal val LocalContainerTransformScope = compositionLocalOf<SharedTransitionScope?> { null }
internal val LocalHomeCarouselTransformTarget = compositionLocalOf<ContainerTransformTarget?> { null }
private const val ContainerTransformRouteMetadata = "com.breakyuna.esjzone.containerTransformRoute"

// Routes are runtime metadata; contentKey must remain a Bundle-saveable value.
internal fun containerTransformMetadata(key: AppNavKey): Map<String, Any> =
    mapOf(ContainerTransformRouteMetadata to key)

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
        val READING_STATISTICS = ContainerTransformTarget("READING_STATISTICS", AppNavKey.ProfileTab, LegacyRoute.Static(ReadingStatisticsPage.key), 16.dp)
        val BOOKSHELF_READING_STATISTICS = ContainerTransformTarget("BOOKSHELF_READING_STATISTICS", AppNavKey.BookshelfTab, LegacyRoute.Static(ReadingStatisticsPage.key), 24.dp)
        val BOOKMARKS = ContainerTransformTarget("BOOKMARKS", AppNavKey.ProfileTab, LegacyRoute.Static(BookmarksPage.key), 16.dp)
        val DOWNLOADS = ContainerTransformTarget("DOWNLOADS", AppNavKey.ProfileTab, LegacyRoute.Static(DownloadPage.key), 16.dp)
        val SETTINGS = ContainerTransformTarget("SETTINGS", AppNavKey.ProfileTab, LegacyRoute.Static(SettingsPage.key), 16.dp)
        private val shortcuts = listOf(FORUM, GUESTBOOK, WATER_COOLER, READING_STATISTICS, BOOKSHELF_READING_STATISTICS, BOOKMARKS, DOWNLOADS, SETTINGS)

        fun homeCarousel(novelIdentity: String, page: Int) = ContainerTransformTarget(
            name = "HOME_CAROUSEL",
            origin = AppNavKey.HomeTab,
            route = LegacyRoute.Novel(novelIdentity),
            cornerRadius = 26.dp,
            homeCarouselPage = page
        )

        fun forKey(key: NavKey?, origin: NavKey? = null): ContainerTransformTarget? {
            val destination = key as? AppNavKey.Legacy ?: return null
            val route = destination.route
            val page = destination.homeCarouselPage
            return if (route is LegacyRoute.Novel && page != null) {
                homeCarousel(route.identity, page)
            } else {
                shortcuts.firstOrNull { it.route == route && (origin == null || it.origin == origin) }
            }
        }
    }
}

/** Only linked entry points replace the ordinary navigation transition. */
internal fun usesContainerTransform(from: NavKey?, to: NavKey?): Boolean =
    ContainerTransformTarget.forKey(to, origin = from)?.let { it.origin == from } == true ||
        ContainerTransformTarget.forKey(from, origin = to)?.let { it.origin == to } == true

// Read each scene's retained entry, including the outgoing scene after a pop.
internal fun usesContainerTransform(from: Scene<NavKey>, to: Scene<NavKey>): Boolean =
    usesContainerTransform(
        from.entries.lastOrNull()?.metadata?.get(ContainerTransformRouteMetadata) as? AppNavKey,
        to.entries.lastOrNull()?.metadata?.get(ContainerTransformRouteMetadata) as? AppNavKey
    )

internal fun containerContentTransform() =
    fadeIn(tween(ContainerTransformDurationMillis)) togetherWith
        fadeOut(tween(ContainerTransformDurationMillis))

@Composable
internal fun ContainerTransformLayout(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    SharedTransitionLayout(modifier) {
        val covers = remember { NovelSharedCovers() }
        CompositionLocalProvider(
            LocalContainerTransformScope provides this,
            LocalNovelSharedCovers provides covers
        ) { content() }
    }
}

@Composable
internal fun Modifier.containerTransformSource(target: ContainerTransformTarget): Modifier =
    containerTransformBounds(target, fullScreen = false)

/** Keep the base card/page layout at its final size while the container changes bounds. */
@Composable
internal fun Modifier.containerTransformContent(): Modifier {
    val sharedScope = LocalContainerTransformScope.current ?: return this
    return with(sharedScope) { skipToLookaheadSize() }
}

internal enum class HomeCarouselElement { COVER, TITLE, AUTHOR }

/** Pair each element with the clicked pager instance, including repeated novels. */
@Composable
internal fun Modifier.homeCarouselSharedElement(
    target: ContainerTransformTarget?,
    element: HomeCarouselElement
): Modifier {
    if (target?.homeCarouselPage == null) return this
    val sharedScope = LocalContainerTransformScope.current ?: return this
    val visibilityScope = LocalNavAnimatedContentScope.current
    return with(sharedScope) {
        val sharedState = rememberSharedContentState(target to element)
        if (element == HomeCarouselElement.COVER) {
            sharedElement(
                sharedContentState = sharedState,
                animatedVisibilityScope = visibilityScope,
                boundsTransform = { _, _ ->
                    tween(ContainerTransformDurationMillis, easing = FastOutSlowInEasing)
                }
            )
        } else {
            sharedBounds(
                sharedContentState = sharedState,
                animatedVisibilityScope = visibilityScope,
                boundsTransform = { _, _ ->
                    tween(ContainerTransformDurationMillis, easing = FastOutSlowInEasing)
                },
                enter = fadeIn(tween(ContainerTransformDurationMillis)),
                exit = fadeOut(tween(ContainerTransformDurationMillis)),
                // Keep text laid out once at each endpoint and scale it along the path.
                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.TopStart
                )
            )
        }
    }
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
        // Keep the page layout stable while its container and shared elements animate.
        CompositionLocalProvider(
            LocalSharedCoverDestinationEnabled provides false,
            LocalHomeCarouselTransformTarget provides target.takeIf { it.homeCarouselPage != null }
        ) {
            Box(Modifier.containerTransformContent().fillMaxSize()) { content() }
        }
    }
}
