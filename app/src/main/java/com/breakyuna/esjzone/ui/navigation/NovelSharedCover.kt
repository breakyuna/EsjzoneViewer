package com.breakyuna.esjzone.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.breakyuna.esjzone.novellibrary.novel.CoverState
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.ui.designsystem.AppMotion
import java.util.UUID

internal data class SharedCoverOrigin(val token: String, val coverUrl: String?, val state: CoverState?)

internal val LocalSharedCoverDestinationEnabled = compositionLocalOf { true }

internal class NovelSharedCovers {
    val sources = mutableStateMapOf<String, SharedCoverOrigin>()
}

internal val LocalNovelSharedCovers = compositionLocalOf<NovelSharedCovers?> { null }

internal class NovelCoverSource(val modifier: Modifier, val open: (() -> Unit) -> Unit)

/** Each rendered occurrence has its own key, even when a book appears in several rails. */
@Composable
internal fun rememberNovelCoverSource(url: String, coverUrl: String? = null, state: CoverState? = null): NovelCoverSource {
    val identity = remember(url) { EsjzoneUrls.canonicalPageKey(url) }
    val token = rememberSaveable(identity) { UUID.randomUUID().toString() }
    val transitions = LocalNovelSharedCovers.current
    val modifier = Modifier.novelCoverSharedElement(token)
    return NovelCoverSource(modifier) { action ->
        transitions?.sources?.set(identity, SharedCoverOrigin(token, coverUrl, state))
        action()
    }
}

@Composable
internal fun Modifier.novelCoverDestination(url: String): Modifier {
    val origin = rememberSharedCoverOrigin(url)
    return if (origin == null) this else novelCoverSharedElement(origin.token)
}

@Composable
internal fun rememberSharedCoverOrigin(url: String): SharedCoverOrigin? {
    val transitions = LocalNovelSharedCovers.current
    val enabled = LocalSharedCoverDestinationEnabled.current
    val identity = remember(url) { EsjzoneUrls.canonicalPageKey(url) }
    return remember(identity, enabled) { if (enabled) transitions?.sources?.get(identity) else null }
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
