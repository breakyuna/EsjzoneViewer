package com.breakyuna.esjzone.ui.reader

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** Own status-bar visibility only while this navigation entry is resumed. */
@Composable
fun ReaderSystemBars(showBars: Boolean) {
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentShowBars = rememberUpdatedState(showBars)
    DisposableEffect(view, lifecycle) {
        var context = view.context
        while (context is ContextWrapper && context !is Activity) context = context.baseContext
        val window = (context as? Activity)?.window
        if (window == null) return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(window, view)
        val type = WindowInsetsCompat.Type.statusBars()
        var previousVisible = true
        var previousBehavior = controller.systemBarsBehavior
        var ownsBars = false
        fun restore() {
            if (!ownsBars) return
            ownsBars = false
            controller.systemBarsBehavior = previousBehavior
            if (previousVisible) controller.show(type) else controller.hide(type)
        }
        fun apply() {
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
            if (!ownsBars) {
                previousVisible = ViewCompat.getRootWindowInsets(view)?.isVisible(type) ?: true
                previousBehavior = controller.systemBarsBehavior
                ownsBars = true
            }
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (currentShowBars.value) controller.show(type) else controller.hide(type)
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> apply()
                Lifecycle.Event.ON_PAUSE -> restore()
                else -> Unit
            }
        }
        // Recomposition applies visibility via the separate effect below.
        lifecycle.addObserver(observer)
        apply()
        onDispose { lifecycle.removeObserver(observer); restore() }
    }
    DisposableEffect(showBars, view, lifecycle) {
        var context = view.context
        while (context is ContextWrapper && context !is Activity) context = context.baseContext
        val window = (context as? Activity)?.window
        if (window != null && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            val controller = WindowCompat.getInsetsController(window, view)
            if (showBars) controller.show(WindowInsetsCompat.Type.statusBars())
            else controller.hide(WindowInsetsCompat.Type.statusBars())
        }
        onDispose { }
    }
}
