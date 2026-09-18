package com.breakyuna.esjzone.ui.designsystem.glass

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import kotlinx.coroutines.flow.collect

/** Shared material signals, with each selectable retaining its own focus/press state. */
internal class NavigationGlassInteraction {
    val source = MutableInteractionSource()
    var origin = Offset.Zero
}

internal val LocalNavigationGlassInteraction = compositionLocalOf<NavigationGlassInteraction?> { null }
internal val LocalNavigationAdaptiveBackdrop = compositionLocalOf { false }

internal class NavigationItemInteraction {
    val source = MutableInteractionSource()
    var origin = Offset.Zero

    fun onPlaced(coordinates: LayoutCoordinates) {
        origin = coordinates.positionInRoot()
    }
}

/** Forward local touch coordinates to the single dock material, including cancellation. */
@Composable
internal fun rememberNavigationItemInteraction(): NavigationItemInteraction {
    val dock = LocalNavigationGlassInteraction.current
    val item = remember { NavigationItemInteraction() }
    LaunchedEffect(dock, item) {
        if (dock == null) return@LaunchedEffect
        val presses = mutableMapOf<PressInteraction.Press, PressInteraction.Press>()
        val focuses = mutableSetOf<FocusInteraction.Focus>()
        try {
            item.source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        val position = if (interaction.pressPosition == Offset.Unspecified) {
                            Offset.Unspecified
                        } else {
                            interaction.pressPosition + item.origin - dock.origin
                        }
                        val forwarded = PressInteraction.Press(position)
                        presses[interaction] = forwarded
                        dock.source.emit(forwarded)
                    }
                    is PressInteraction.Release -> presses.remove(interaction.press)?.let {
                        dock.source.emit(PressInteraction.Release(it))
                    }
                    is PressInteraction.Cancel -> presses.remove(interaction.press)?.let {
                        dock.source.emit(PressInteraction.Cancel(it))
                    }
                    is FocusInteraction.Focus -> {
                        focuses.add(interaction)
                        dock.source.emit(interaction)
                    }
                    is FocusInteraction.Unfocus -> {
                        focuses.remove(interaction.focus)
                        dock.source.emit(interaction)
                    }
                }
            }
        } finally {
            // A tab can disappear while pressed (navigation, rotation, reorder, modal).
            // Never leave the shared material stuck in a pressed/focused state.
            presses.values.forEach { dock.source.tryEmit(PressInteraction.Cancel(it)) }
            focuses.forEach { dock.source.tryEmit(FocusInteraction.Unfocus(it)) }
        }
    }
    return item
}
