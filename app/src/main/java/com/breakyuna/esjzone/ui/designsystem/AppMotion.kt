package com.breakyuna.esjzone.ui.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object AppMotion {
    const val instant = 0
    const val quick = 120
    const val short = 180
    const val standard = 240
    const val expressive = 360
    const val containerTransform = 400
    const val fadeThroughExit = 90
    const val fadeThroughEnter = 210
    const val tabSwitch = fadeThroughExit + fadeThroughEnter
    const val weeklyTab = 280
    const val carousel = 500
    const val historyRotation = 450
    const val loadingDelay = 300L
    const val breathing = 800
    const val ghost = 1800
    const val commentHighlight = 1200

    val standardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val emphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val standardAccelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)
    val emphasizedEasing
        get() = emphasizedDecelerate

    fun <T> standardSpec(): FiniteAnimationSpec<T> = tween(standard, easing = standardEasing)

    fun <T> expressiveSpec(): FiniteAnimationSpec<T> =
        tween(expressive, easing = emphasizedDecelerate)

    fun <T> exitSpec(): FiniteAnimationSpec<T> = tween(short, easing = emphasizedAccelerate)

    fun <T> spatialFast() = spring<T>(dampingRatio = 0.9f, stiffness = 1400f)

    fun <T> spatialDefault() = spring<T>(dampingRatio = 0.9f, stiffness = 700f)

    fun <T> spatialSlow() = spring<T>(dampingRatio = 0.9f, stiffness = 300f)

    fun <T> effectsFast() = spring<T>(dampingRatio = 1f, stiffness = 3800f)

    fun <T> effectsDefault() = spring<T>(dampingRatio = 1f, stiffness = 1600f)

    fun <T> effectsSlow() = spring<T>(dampingRatio = 1f, stiffness = 800f)
}
