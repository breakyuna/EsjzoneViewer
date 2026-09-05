package com.breakyuna.esjzone.ui.component

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import com.breakyuna.esjzone.ui.theme.QuietEditorial

/** Shared floating navigation surface used by the root tab scaffold. */
@Composable
fun QuietBottomNavigation(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            // Keep the system gesture/navigation area inside the floating
            // component so Scaffold reserves exactly the rendered bar area.
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .border(
                    QuietEditorial.hairline,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    QuietEditorial.cardShape
                ),
            shape = QuietEditorial.cardShape,
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
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

@Composable
fun RowScope.QuietBottomNavigationItem(
    tab: Tab,
    onDoubleClick: (() -> Unit)? = null
) {
    val tabNavigator = LocalTabNavigator.current
    val isSelected = tabNavigator.current == tab
    var lastTapAt by remember { mutableLongStateOf(0L) }
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant
    val itemShape = QuietEditorial.controlShape

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(itemShape)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent,
                itemShape
            )
            .selectable(
                selected = isSelected,
                role = Role.Tab,
                onClick = {
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
            )
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
                    tint = if (isSelected) activeColor else inactiveColor,
                    modifier = Modifier.size(23.dp)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = tab.options.title,
                style = QuietEditorial.smallLabel,
                color = if (isSelected) activeColor else inactiveColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
