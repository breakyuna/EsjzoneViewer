package com.breakyuna.esjzone.ui.screen

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import cafe.adriel.voyager.transitions.SlideTransition
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.tab.CategoryTab
import com.breakyuna.esjzone.ui.tab.HomeTab
import com.breakyuna.esjzone.ui.tab.ProfileTab
import com.breakyuna.esjzone.ui.tab.SearchTab

class MainScreen(val authorization: Authorization) : Screen {

    @Composable
    override fun Content() {
        CompositionLocalProvider(value = LocalAuthorization provides authorization) {
            Navigator(screen = TabScreen) { navigator ->

                SlideTransition(navigator = navigator) { screen ->
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
                        TabNavigationItem(tab = CategoryTab)
                        TabNavigationItem(tab = SearchTab)
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
private fun RowScope.TabNavigationItem(tab: Tab) {
    val tabNavigator = LocalTabNavigator.current
    val isSelected = tabNavigator.current == tab

    NavigationBarItem(
        selected = isSelected,
        onClick = { tabNavigator.current = tab },
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
