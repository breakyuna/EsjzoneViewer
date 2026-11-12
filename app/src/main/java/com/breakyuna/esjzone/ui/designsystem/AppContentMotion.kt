package com.breakyuna.esjzone.ui.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Reveal controls without abruptly changing the space available to the row text. */
@Composable
fun AppSelectionReveal(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandHorizontally(tween(AppMotion.standard)) + fadeIn(tween(AppMotion.quick)),
        exit = shrinkHorizontally(tween(AppMotion.standard)) + fadeOut(tween(AppMotion.quick)),
    ) {
        content()
    }
}

@Composable
fun AppAnimatedTitle(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    val reduced = LocalReduceMotion.current
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = {
            if (reduced) (EnterTransition.None togetherWith ExitTransition.None).using(null)
            else
                fadeIn(tween(AppMotion.quick, delayMillis = 60)) togetherWith
                    fadeOut(tween(AppMotion.quick))
        },
        label = "ModeTitle",
    ) { displayed ->
        GlobalText(displayed, style = style, maxLines = maxLines, overflow = overflow)
    }
}

private val LocalContentArrivalEnabled = staticCompositionLocalOf { true }

/** One reveal per content instance; refreshes retain the already visible content. */
@Composable
fun Modifier.contentArrival(key: Any = Unit, delayMillis: Int = 0): Modifier {
    if (!LocalContentArrivalEnabled.current || LocalReduceMotion.current) return this
    var revealed by rememberSaveable(key) { mutableStateOf(false) }
    val alpha = remember(key) { Animatable(if (revealed) 1f else 0f) }
    LaunchedEffect(alpha) {
        revealed = true
        alpha.animateTo(1f, tween(AppMotion.standard, delayMillis = delayMillis))
    }
    return graphicsLayer { this.alpha = alpha.value }
}

/** State-type keys keep ordinary result refreshes in the same composition. */
@Composable
fun <T : Any> AppStateContent(
    state: T,
    modifier: Modifier = Modifier,
    contentKey: (T) -> Any = { it::class },
    content: @Composable (T) -> Unit,
) {
    val reduced = LocalReduceMotion.current
    AnimatedContent(
        targetState = state,
        contentKey = contentKey,
        modifier = modifier,
        transitionSpec = {
            if (reduced) (EnterTransition.None togetherWith ExitTransition.None).using(null)
            else
                (fadeIn(tween(AppMotion.standard)) togetherWith fadeOut(tween(AppMotion.quick)))
                    .using(SizeTransform(clip = false))
        },
        label = "PageState",
    ) { snapshot ->
        MotionContent(active = contentKey(snapshot) == contentKey(state)) {
            CompositionLocalProvider(LocalContentArrivalEnabled provides false) {
                content(snapshot)
            }
        }
    }
}

/** Shared-axis X for tabs and small, paged content, without moving the outer scaffold. */
@Composable
fun AppPagedContent(page: Int, modifier: Modifier = Modifier, content: @Composable (Int) -> Unit) {
    val reduced = LocalReduceMotion.current
    val distance = with(LocalDensity.current) { 30.dp.roundToPx() }
    AnimatedContent(
        targetState = page,
        modifier = modifier,
        transitionSpec = {
            if (reduced) (EnterTransition.None togetherWith ExitTransition.None).using(null)
            else {
                val direction = if (targetState > initialState) 1 else -1
                ((slideInHorizontally(AppMotion.standardSpec()) { direction * distance } +
                        fadeIn(
                            tween(
                                AppMotion.fadeThroughEnter,
                                delayMillis = AppMotion.fadeThroughExit,
                            )
                        )) togetherWith
                        (slideOutHorizontally(AppMotion.standardSpec()) { -direction * distance } +
                            fadeOut(tween(AppMotion.fadeThroughExit))))
                    .using(SizeTransform(clip = false))
            }
        },
        label = "PagedContent",
    ) { displayedPage ->
        MotionContent(active = displayedPage == page) { content(displayedPage) }
    }
}

@Composable
private fun MotionContent(active: Boolean, content: @Composable () -> Unit) {
    Box(
        Modifier.then(if (active) Modifier else Modifier.clearAndSetSemantics {}).pointerInput(
            active
        ) {
            if (!active)
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach {
                        it.consume()
                    }
                }
        }
    ) {
        content()
    }
}
