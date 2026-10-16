package com.breakyuna.esjzone.ui.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

/** Reveal controls without abruptly changing the space available to the row text. */
@Composable
fun AppSelectionReveal(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandHorizontally(tween(AppMotion.standard)) + fadeIn(tween(AppMotion.quick)),
        exit = shrinkHorizontally(tween(AppMotion.standard)) + fadeOut(tween(AppMotion.quick))
    ) { content() }
}

@Composable
fun AppAnimatedTitle(text: String, style: TextStyle, modifier: Modifier = Modifier, maxLines: Int = 1, overflow: TextOverflow = TextOverflow.Ellipsis) {
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = { fadeIn(tween(AppMotion.quick, delayMillis = 60)) togetherWith fadeOut(tween(AppMotion.quick)) },
        label = "ModeTitle"
    ) { displayed -> GlobalText(displayed, style = style, maxLines = maxLines, overflow = overflow) }
}

/** One reveal per content instance; refreshes retain the already visible content. */
@Composable
fun Modifier.contentArrival(key: Any = Unit, delayMillis: Int = 0): Modifier {
    var revealed by rememberSaveable(key) { mutableStateOf(false) }
    val alpha = remember(key) { Animatable(if (revealed) 1f else 0f) }
    LaunchedEffect(alpha) {
        revealed = true
        alpha.animateTo(1f, tween(AppMotion.standard, delayMillis = delayMillis))
    }
    return graphicsLayer { this.alpha = alpha.value }
}
