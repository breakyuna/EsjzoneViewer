package com.breakyuna.esjzone.ui.component

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab

/** Shared floating navigation surface used by the root tab scaffold. */
@Composable
fun QuietBottomNavigation(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val glassBgBrush = if (!isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.78f),
                Color.White.copy(alpha = 0.58f),
                Color(0xFFF2F2F7).copy(alpha = 0.68f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.74f)
            )
        )
    }
    val glassBorderBrush = if (!isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.95f),
                Color.White.copy(alpha = 0.28f),
                Color.White.copy(alpha = 0.70f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.38f),
                Color.White.copy(alpha = 0.10f),
                Color.White.copy(alpha = 0.28f)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.12f),
                    spotColor = Color.Black.copy(alpha = 0.22f)
                )
                .border(1.2.dp, glassBorderBrush, CircleShape),
            shape = CircleShape,
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(brush = glassBgBrush)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    if (!isDark) Color.White.copy(alpha = 0.38f)
                                    else Color.White.copy(alpha = 0.10f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    content = content
                )
            }
        }
    }
}

@Composable
fun RowScope.QuietBottomNavigationItem(
    tab: Tab,
    onDoubleClick: (() -> Unit)? = null
) {
    val tabNavigator = LocalTabNavigator.current
    val isSelected = tabNavigator.current == tab
    var lastTapAt by remember { mutableLongStateOf(0L) }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = if (!isDark) {
        Color(0xFF2C2C2E).copy(alpha = 0.78f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    }
    val animatedIconColor by animateColorAsState(
        targetValue = if (isSelected) activeColor else inactiveColor,
        animationSpec = tween(durationMillis = 200),
        label = "tab_icon_color"
    )
    val animatedTextColor by animateColorAsState(
        targetValue = if (isSelected) activeColor else inactiveColor,
        animationSpec = tween(durationMillis = 200),
        label = "tab_text_color"
    )
    val pillShape = CircleShape
    val pillBackground = when {
        isSelected && !isDark -> Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.65f), activeColor.copy(alpha = 0.14f))
        )
        isSelected && isDark -> Brush.verticalGradient(
            listOf(activeColor.copy(alpha = 0.30f), activeColor.copy(alpha = 0.16f))
        )
        else -> null
    }
    val pillBorder = if (isSelected) {
        BorderStroke(
            1.dp,
            if (!isDark) Color.White.copy(alpha = 0.70f)
            else Color.White.copy(alpha = 0.22f)
        )
    } else null

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(pillShape)
            .then(
                if (pillBorder != null && pillBackground != null) {
                    Modifier
                        .border(pillBorder, pillShape)
                        .background(pillBackground, pillShape)
                } else Modifier
            )
            .clickable {
                val now = SystemClock.uptimeMillis()
                val wasAlreadySelected = tabNavigator.current == tab
                val isDoubleClick = onDoubleClick != null &&
                    wasAlreadySelected && now - lastTapAt in 1..420
                if (isDoubleClick) {
                    lastTapAt = 0L
                    tabNavigator.current = tab
                    onDoubleClick.invoke()
                } else {
                    lastTapAt = now
                    tabNavigator.current = tab
                }
            }
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                selected = isSelected
            }
            .padding(horizontal = 4.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            tab.options.icon?.let { icon ->
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = animatedIconColor,
                    modifier = Modifier.size(23.dp)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = tab.options.title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = animatedTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
