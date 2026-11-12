package com.breakyuna.esjzone.ui.designsystem

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** One observable source, also overridable by previews and accessibility hosts. */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
internal fun rememberReducedMotionState(): Boolean {
    val context = LocalContext.current
    val preview = LocalInspectionMode.current
    val resolver = context.contentResolver
    val keys = remember {
        listOf(
            Settings.Global.ANIMATOR_DURATION_SCALE,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            Settings.Global.WINDOW_ANIMATION_SCALE,
        )
    }
    fun read() =
        preview ||
            keys.any {
                runCatching { Settings.Global.getFloat(resolver, it, 1f) <= 0f }.getOrDefault(false)
            }
    var reduced by remember(resolver, preview) { mutableStateOf(read()) }
    DisposableEffect(resolver, preview) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    reduced = read()
                }
            }
        if (!preview)
            keys.forEach {
                resolver.registerContentObserver(Settings.Global.getUriFor(it), false, observer)
            }
        // Read after registration so a change during initial composition cannot be missed.
        reduced = read()
        onDispose { if (!preview) resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

@Composable fun rememberSystemReducedMotion(): Boolean = LocalReduceMotion.current

/** Stop decorative loops when the owning navigation entry is no longer resumed. */
@Composable
fun rememberMotionActive(): Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by
        remember(lifecycle) {
            mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return resumed && !LocalReduceMotion.current
}

@Composable
fun rememberTouchExplorationEnabled(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var enabled by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        enabled = manager?.isTouchExplorationEnabled == true
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}
