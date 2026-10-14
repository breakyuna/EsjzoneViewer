package com.breakyuna.esjzone.ui.reader

import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Keep the screen on only while this reader entry is resumed. */
@Composable
internal fun ReaderKeepScreenOn(window: Window?, enabled: Boolean) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(window, lifecycle, enabled) {
        if (window == null || !enabled) return@DisposableEffect onDispose { }
        val flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        var previousKeepScreenOn: Boolean? = null
        fun apply() {
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || previousKeepScreenOn != null) return
            previousKeepScreenOn = window.attributes.flags and flag != 0
            window.addFlags(flag)
        }
        fun restore() {
            val previous = previousKeepScreenOn ?: return
            if (!previous) window.clearFlags(flag)
            previousKeepScreenOn = null
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> apply()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> restore()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        apply()
        onDispose {
            lifecycle.removeObserver(observer)
            restore()
        }
    }
}
