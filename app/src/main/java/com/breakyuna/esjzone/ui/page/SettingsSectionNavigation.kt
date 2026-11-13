package com.breakyuna.esjzone.ui.page

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.breakyuna.esjzone.ui.designsystem.LocalReduceMotion
import com.breakyuna.esjzone.ui.navigation.pagePopContentTransform
import com.breakyuna.esjzone.ui.navigation.pagePushContentTransform

/** Section keys share the outer page's saved state holder; navigation owns gesture commit/cancel. */
@Composable
internal fun SettingsSectionNavigation(
    section: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (String) -> Unit
) {
    val reduced = LocalReduceMotion.current
    NavDisplay(
        backStack = if (section == "MAIN") listOf("MAIN") else listOf("MAIN", section),
        onBack = onBack,
        modifier = modifier,
        entryDecorators = emptyList(),
        transitionSpec = {
            if (reduced) EnterTransition.None togetherWith ExitTransition.None
            else pagePushContentTransform()
        },
        popTransitionSpec = {
            if (reduced) EnterTransition.None togetherWith ExitTransition.None
            else pagePopContentTransform()
        },
        predictivePopTransitionSpec = {
            if (reduced) EnterTransition.None togetherWith ExitTransition.None
            else pagePopContentTransform(predictive = true)
        },
        entryProvider = { key ->
            NavEntry(key) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    content(key)
                }
            }
        }
    )
}
