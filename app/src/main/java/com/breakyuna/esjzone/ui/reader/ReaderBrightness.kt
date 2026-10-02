package com.breakyuna.esjzone.ui.reader

import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Restore the host window when comments, another page, or another app takes focus. */
@Composable
internal fun ReaderBrightness(window: Window?, brightness: Float) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentBrightness = rememberUpdatedState(brightness)
    DisposableEffect(window, lifecycle) {
        var previous: Float? = null
        fun apply() {
            if (window == null || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
            if (previous == null) previous = window.attributes.screenBrightness
            window.attributes = window.attributes.apply { screenBrightness = currentBrightness.value }
        }
        fun restore() {
            val saved = previous ?: return
            window?.let { it.attributes = it.attributes.apply { screenBrightness = saved } }
            previous = null
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> apply()
                Lifecycle.Event.ON_PAUSE -> restore()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        apply()
        onDispose { lifecycle.removeObserver(observer); restore() }
    }
    SideEffect {
        if (window != null && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            window.attributes = window.attributes.apply { screenBrightness = brightness }
        }
    }
}
