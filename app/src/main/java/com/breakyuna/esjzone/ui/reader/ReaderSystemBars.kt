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

/** Own system-bar visibility only while this reader entry is resumed. */
@Composable
fun ReaderSystemBars(
    showChrome: Boolean,
    showSystemStatusBar: Boolean,
    showSystemNavigationBar: Boolean
) {
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentStatus = rememberUpdatedState(showChrome || showSystemStatusBar)
    val currentNavigation = rememberUpdatedState(showChrome || showSystemNavigationBar)
    DisposableEffect(view, lifecycle) {
        var context = view.context
        while (context is ContextWrapper && context !is Activity) context = context.baseContext
        val window = (context as? Activity)?.window
        if (window == null) return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(window, view)
        val statusType = WindowInsetsCompat.Type.statusBars()
        val navigationType = WindowInsetsCompat.Type.navigationBars()
        var previousStatusVisible = true
        var previousNavigationVisible = true
        var previousBehavior = controller.systemBarsBehavior
        var ownsBars = false
        fun restore() {
            if (!ownsBars) return
            ownsBars = false
            controller.systemBarsBehavior = previousBehavior
            if (previousStatusVisible) controller.show(statusType) else controller.hide(statusType)
            if (previousNavigationVisible) controller.show(navigationType) else controller.hide(navigationType)
        }
        fun apply() {
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
            if (!ownsBars) {
                val insets = ViewCompat.getRootWindowInsets(view)
                previousStatusVisible = insets?.isVisible(statusType) ?: true
                previousNavigationVisible = insets?.isVisible(navigationType) ?: true
                previousBehavior = controller.systemBarsBehavior
                ownsBars = true
            }
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (currentStatus.value) controller.show(statusType) else controller.hide(statusType)
            if (currentNavigation.value) controller.show(navigationType) else controller.hide(navigationType)
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
    DisposableEffect(showChrome, showSystemStatusBar, showSystemNavigationBar, view, lifecycle) {
        var context = view.context
        while (context is ContextWrapper && context !is Activity) context = context.baseContext
        val window = (context as? Activity)?.window
        if (window != null && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            val controller = WindowCompat.getInsetsController(window, view)
            if (showChrome || showSystemStatusBar) controller.show(WindowInsetsCompat.Type.statusBars())
            else controller.hide(WindowInsetsCompat.Type.statusBars())
            if (showChrome || showSystemNavigationBar) controller.show(WindowInsetsCompat.Type.navigationBars())
            else controller.hide(WindowInsetsCompat.Type.navigationBars())
        }
        onDispose { }
    }
}
