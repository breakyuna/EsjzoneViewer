package com.breakyuna.esjzone.ui.page

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.ui.designsystem.*
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource

@Composable
internal fun NovelFavoriteButton(
    favorite: Boolean,
    pending: Boolean,
    failed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReduceMotion.current
    val semantics = appSemanticColors()
    val container by
        animateColorAsState(
            if (favorite) MaterialTheme.colorScheme.secondaryContainer else semantics.favorite,
            AppMotion.effectsDefault(),
            label = "FavoriteContainer",
        )
    val foreground by
        animateColorAsState(
            if (favorite) MaterialTheme.colorScheme.onSecondaryContainer else semantics.onFavorite,
            AppMotion.effectsDefault(),
            label = "FavoriteContent",
        )
    val scale = remember { Animatable(1f) }
    var pulse by remember { mutableIntStateOf(0) }
    var consumedPulse by remember { mutableIntStateOf(0) }
    // Changing favorite to false or enabling reduced motion cancels any existing pulse.
    // A new effect resets before animating; no suspending cleanup in a cancelled job.
    LaunchedEffect(pulse, favorite, reduced) {
        val animate = pulse != consumedPulse && favorite && !reduced
        consumedPulse = pulse
        scale.snapTo(1f)
        if (animate) {
            scale.animateTo(
                1.2f,
                tween(AppMotion.fadeThroughExit, easing = AppMotion.standardEasing),
            )
            scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f))
        }
    }
    Button(
        onClick = {
            if (!favorite && !reduced) pulse++
            onToggle()
        },
        modifier = modifier.heightIn(min = AppTouchTarget.minimum),
        shape = AppShapes.standard,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = foreground),
        contentPadding = PaddingValues(horizontal = AppSpacing.sm, vertical = AppSpacing.sm),
    ) {
        AnimatedContent(
            targetState = favorite,
            label = "FavoriteIcon",
            transitionSpec = {
                if (reduced) (EnterTransition.None togetherWith ExitTransition.None).using(null)
                else
                    (scaleIn(AppMotion.spatialFast(), initialScale = 0.6f) +
                        fadeIn(tween(AppMotion.quick))) togetherWith
                        (scaleOut(tween(AppMotion.quick), targetScale = 0.6f) +
                            fadeOut(tween(AppMotion.quick)))
            },
        ) { selected ->
            Icon(
                if (selected) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = null,
                modifier =
                    Modifier.size(19.dp).graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                    },
            )
        }
        Spacer(Modifier.width(6.dp))
        AppAnimatedTitle(
            text =
                stringResource(
                    when {
                        failed -> R.string.novel_favorite_failed
                        pending -> R.string.novel_favorite_pending
                        favorite -> R.string.novel_favorited
                        else -> R.string.novel_favorite
                    }
                ),
            style = LocalTextStyle.current,
            maxLines = 2,
        )
    }
}
