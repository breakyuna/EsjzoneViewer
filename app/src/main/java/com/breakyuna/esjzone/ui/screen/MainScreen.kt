package com.breakyuna.esjzone.ui.screen

import android.os.SystemClock
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.ui.navigation.CoverTransition
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.tab.FavoriteTab
import com.breakyuna.esjzone.ui.tab.HistoryTab
import com.breakyuna.esjzone.ui.tab.HomeTab
import com.breakyuna.esjzone.ui.tab.ProfileTab

class MainScreen(val authorization: Authorization) : Screen {

    override val key: ScreenKey = "MainScreen"

    @Composable
    override fun Content() {
        CompositionLocalProvider(value = LocalAuthorization provides authorization) {
            Navigator(screen = TabScreen) { navigator ->

                CoverTransition(navigator = navigator) { screen ->
                    CompositionLocalProvider(value = LocalBaseNavigator provides navigator) {
                        screen.Content()
                    }
                }
            }
        }
    }

}

private object TabScreen : Screen {
    private fun readResolve(): Any = TabScreen

    @Composable
    override fun Content() {
        TabNavigator(tab = HomeTab) {
            Scaffold(
                bottomBar = {
                    NavigationBar(
                        tonalElevation = 3.dp,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        TabNavigationItem(tab = HomeTab)
                        TabNavigationItem(
                            tab = HistoryTab,
                            onDoubleClick = HistoryTab::requestOpenLastReading
                        )
                        TabNavigationItem(tab = FavoriteTab)
                        TabNavigationItem(tab = ProfileTab)
                    }
                }
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(it),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CurrentTab()
                }
            }
        }
    }

}

@Composable
private fun RowScope.TabNavigationItem(
    tab: Tab,
    onDoubleClick: (() -> Unit)? = null
) {
    val tabNavigator = LocalTabNavigator.current
    val isSelected = tabNavigator.current == tab
    val lastTapAt = remember { mutableLongStateOf(0L) }

    NavigationBarItem(
        selected = isSelected,
        onClick = {
            val now = SystemClock.uptimeMillis()
            val wasAlreadySelected = tabNavigator.current == tab
            val isDoubleClick = onDoubleClick != null &&
                wasAlreadySelected &&
                now - lastTapAt.longValue in 1..420
            if (isDoubleClick) {
                lastTapAt.longValue = 0L
                tabNavigator.current = tab
                onDoubleClick?.invoke()
            } else {
                lastTapAt.longValue = now
                tabNavigator.current = tab
            }
        },
        icon = {
            tab.options.icon?.let { icon ->
                Icon(painter = icon, contentDescription = tab.options.title)
            }
        },
        label = {
            Text(
                text = tab.options.title,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    )
}
