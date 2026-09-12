package com.breakyuna.esjzone.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp

/** Keep account pages comfortable to scan on both phones and wide tablets. */
fun Modifier.accountContentWidth(): Modifier =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = AppLayout.contentMaxWidth)

@Composable
fun AccountIconBadge(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(44.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, AppShapes.standard),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
fun AccountSummary(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth().clip(AppShapes.prominent)
            .background(Brush.linearGradient(listOf(colors.surfaceContainerHigh, colors.surfaceContainerLow)))
            .padding(AppSpacing.xl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg)
    ) {
        AccountIconBadge(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Text(title, style = AppTypography.titleLarge, color = colors.onSurface)
            Text(subtitle, style = AppTypography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}
