package com.breakyuna.esjzone.ui.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

object AppMotion {
    const val instant = 0
    const val quick = 120
    const val standard = 240
    const val expressive = 360

    val standardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val emphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun <T> standardSpec(): FiniteAnimationSpec<T> = tween(
        durationMillis = standard,
        easing = standardEasing
    )

    fun <T> expressiveSpec(): FiniteAnimationSpec<T> = tween(
        durationMillis = expressive,
        easing = emphasizedEasing
    )
}
