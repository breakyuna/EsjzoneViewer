package com.breakyuna.esjzone.ui.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.breakyuna.esjzone.ui.designsystem.GlobalToast as Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import com.breakyuna.esjzone.R

/**
 * Pure helper for double-tap-to-exit rule.
 */
object BackToExitPolicy {
    const val EXIT_INTERVAL_MILLIS = 2000L

    fun shouldExit(lastPressTime: Long, currentPressTime: Long): Boolean {
        return lastPressTime > 0L && (currentPressTime - lastPressTime) in 0..EXIT_INTERVAL_MILLIS
    }
}

/**
 * Intercepts back navigation at application exit boundaries, requiring a confirmation
 * back press within [BackToExitPolicy.EXIT_INTERVAL_MILLIS] before finishing the Activity.
 */
@Composable
fun AppExitBackHandler(enabled: Boolean = true) {
    val context = LocalContext.current
    val activity = LocalActivity.current ?: context.findActivity()
    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    LaunchedEffect(enabled) {
        if (!enabled) {
            lastBackPressTime = 0L
        }
    }
    val exitPrompt = stringResource(R.string.app_exit_confirm)
    BackHandler(enabled = enabled) {
        val now = System.currentTimeMillis()
        if (BackToExitPolicy.shouldExit(lastBackPressTime, now)) {
            activity?.finish()
        } else {
            lastBackPressTime = now
            Toast.makeText(context, exitPrompt, Toast.LENGTH_SHORT).show()
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext?.findActivity()
    else -> null
}
