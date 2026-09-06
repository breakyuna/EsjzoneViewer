package com.breakyuna.esjzone.ui.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

object AppMotion {
    const val instant = 0
    const val quick = 120
    const val standard = 240
    const val expressive = 360

    /** Material's standard deceleration curve for ordinary content changes. */
    val standardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Slightly more expressive curve reserved for prominent shell transitions. */
    val emphasizedEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    fun <T> standardSpec(): FiniteAnimationSpec<T> = tween(
        durationMillis = standard,
        easing = standardEasing
    )

    fun <T> expressiveSpec(): FiniteAnimationSpec<T> = tween(
        durationMillis = expressive,
        easing = emphasizedEasing
    )
}
