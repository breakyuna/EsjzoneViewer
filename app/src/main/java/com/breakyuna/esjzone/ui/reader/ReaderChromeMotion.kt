package com.breakyuna.esjzone.ui.reader

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import com.breakyuna.esjzone.ui.designsystem.AppMotion

internal object ReaderChromeMotion {
    private val enterOffset = AppMotion.spatialDefault<IntOffset>()
    private val exitOffset = AppMotion.spatialFast<IntOffset>()
    val topEnter = slideInVertically(enterOffset) { -it } + fadeIn(AppMotion.effectsDefault())
    val topExit = slideOutVertically(exitOffset) { -it } + fadeOut(tween(AppMotion.fadeThroughExit))
    val bottomEnter = slideInVertically(enterOffset) { it } + fadeIn(AppMotion.effectsDefault())
    val bottomExit =
        slideOutVertically(exitOffset) { it } + fadeOut(tween(AppMotion.fadeThroughExit))
}
