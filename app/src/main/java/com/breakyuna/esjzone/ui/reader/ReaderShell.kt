package com.breakyuna.esjzone.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Root presentation boundary for immersive reading. It deliberately owns only
 * the reader canvas and tap surface; Navigation 3 owns the route and back
 * stack outside this shell.
 */
@Composable
fun ReaderShell(
    background: Color,
    onReadingAreaTap: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onReadingAreaTap
            ),
        content = content
    )
}
