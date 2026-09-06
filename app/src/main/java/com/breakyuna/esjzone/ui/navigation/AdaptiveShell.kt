package com.breakyuna.esjzone.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.window.core.layout.WindowWidthSizeClass
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.ui.tab.FavoriteTab
import com.breakyuna.esjzone.ui.tab.HistoryTab
import com.breakyuna.esjzone.ui.tab.HomeTab
import com.breakyuna.esjzone.ui.tab.ProfileTab

private enum class AppTabId(val route: AppNavKey, val icon: ImageVector) {
    HOME(AppNavKey.HomeTab, Icons.Filled.Home),
    HISTORY(AppNavKey.HistoryTab, Icons.Filled.History),
    BOOKSHELF(AppNavKey.BookshelfTab, Icons.Filled.AutoStories),
    PROFILE(AppNavKey.ProfileTab, Icons.Filled.Person)
}

/**
 * The application shell keeps one Navigation 3 back stack per top-level tab.
 * Compact windows use a NavigationBar. Medium windows use a compact rail and
 * expanded windows use a permanent, labelled drawer rail. Each stack has its
 * own saveable state and entry ViewModel store, so switching tabs never
 * recreates the feature presentation.
 */
@Composable
fun AdaptiveAppShell(
    authorization: Authorization,
    rootNavigator: AppNavigator,
    modifier: Modifier = Modifier.fillMaxSize(),
    topInsetConsumed: Boolean = false
) {
    val homeStack: MutableList<NavKey> = rememberNavBackStack(AppNavKey.HomeTab)
    val historyStack: MutableList<NavKey> = rememberNavBackStack(AppNavKey.HistoryTab)
    val bookshelfStack: MutableList<NavKey> = rememberNavBackStack(AppNavKey.BookshelfTab)
    val profileStack: MutableList<NavKey> = rememberNavBackStack(AppNavKey.ProfileTab)
    var selectedTab by rememberSaveable { mutableStateOf(AppTabId.HOME.name) }
    // WindowWidthSizeClass is available in the locked Adaptive 1.2.0 API. The breakpoint helpers and
    // constants on WindowSizeClass were added by WindowManager 1.4 and must
    // not leak into this stage's locked Adaptive 1.2.0 dependency set.
    val widthSizeClass = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass
    val tab = AppTabId.valueOf(selectedTab)
    val homeNavigator = remember(homeStack, rootNavigator) { rootNavigator.child(homeStack) }
    val historyNavigator = remember(historyStack, rootNavigator) { rootNavigator.child(historyStack) }
    val bookshelfNavigator = remember(bookshelfStack, rootNavigator) {
        rootNavigator.child(bookshelfStack)
    }
    val profileNavigator = remember(profileStack, rootNavigator) {
        rootNavigator.child(profileStack)
    }

    CompositionLocalProvider(
        LocalAuthorization provides authorization
    ) {
        when (widthSizeClass) {
            WindowWidthSizeClass.COMPACT -> {
                // NavigationBar owns the bottom gesture inset. The content
                // only receives top and horizontal safe insets, preventing
                // a second bottom padding in legacy LazyColumns.
                Column(
                    modifier = modifier.windowInsetsPadding(
                        shellSafeDrawing(top = !topInsetConsumed, bottom = false)
                    )
                ) {
                    TabStacksDisplay(
                        selected = tab,
                        homeStack = homeStack,
                        homeNavigator = homeNavigator,
                        historyStack = historyStack,
                        historyNavigator = historyNavigator,
                        bookshelfStack = bookshelfStack,
                        bookshelfNavigator = bookshelfNavigator,
                        profileStack = profileStack,
                        profileNavigator = profileNavigator,
                        modifier = Modifier.weight(1f)
                    )
                    AppNavigationBar(
                        selected = tab,
                        onSelected = { selectedTab = it.name }
                    )
                }
            }

            WindowWidthSizeClass.MEDIUM -> {
                Row(
                    modifier = modifier.windowInsetsPadding(
                        shellSafeDrawing(top = !topInsetConsumed, bottom = true)
                    )
                ) {
                    AppNavigationRail(
                        selected = tab,
                        onSelected = { selectedTab = it.name }
                    )
                    TabStacksDisplay(
                        selected = tab,
                        homeStack = homeStack,
                        homeNavigator = homeNavigator,
                        historyStack = historyStack,
                        historyNavigator = historyNavigator,
                        bookshelfStack = bookshelfStack,
                        bookshelfNavigator = bookshelfNavigator,
                        profileStack = profileStack,
                        profileNavigator = profileNavigator,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            WindowWidthSizeClass.EXPANDED -> {
                Row(
                    modifier = modifier.windowInsetsPadding(
                        shellSafeDrawing(top = !topInsetConsumed, bottom = true)
                    )
                ) {
                    AppExpandedNavigationRail(
                        selected = tab,
                        onSelected = { selectedTab = it.name }
                    )
                    TabStacksDisplay(
                        selected = tab,
                        homeStack = homeStack,
                        homeNavigator = homeNavigator,
                        historyStack = historyStack,
                        historyNavigator = historyNavigator,
                        bookshelfStack = bookshelfStack,
                        bookshelfNavigator = bookshelfNavigator,
                        profileStack = profileStack,
                        profileNavigator = profileNavigator,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private fun shellSafeDrawing(top: Boolean, bottom: Boolean): WindowInsets {
    val sides = when {
        top && bottom -> WindowInsetsSides.Top + WindowInsetsSides.Bottom +
            WindowInsetsSides.Horizontal
        top -> WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        bottom -> WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
        else -> WindowInsetsSides.Horizontal
    }
    return WindowInsets.safeDrawing.only(sides)
}

@Composable
private fun TabStacksDisplay(
    selected: AppTabId,
    homeStack: MutableList<NavKey>,
    homeNavigator: AppNavigator,
    historyStack: MutableList<NavKey>,
    historyNavigator: AppNavigator,
    bookshelfStack: MutableList<NavKey>,
    bookshelfNavigator: AppNavigator,
    profileStack: MutableList<NavKey>,
    profileNavigator: AppNavigator,
    modifier: Modifier = Modifier
) {
    // Keep all four NavDisplays mounted. Their saveable-state holders and
    // entry ViewModel stores therefore survive a tab switch; only the
    // selected display is placed above the others for input and rendering.
    androidx.compose.foundation.layout.Box(modifier) {
        TabStackDisplay(
            stack = homeStack,
            navigator = homeNavigator,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(if (selected == AppTabId.HOME) 1f else -1f)
                .graphicsLayer { alpha = if (selected == AppTabId.HOME) 1f else 0f }
        )
        TabStackDisplay(
            stack = historyStack,
            navigator = historyNavigator,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(if (selected == AppTabId.HISTORY) 1f else -1f)
                .graphicsLayer { alpha = if (selected == AppTabId.HISTORY) 1f else 0f }
        )
        TabStackDisplay(
            stack = bookshelfStack,
            navigator = bookshelfNavigator,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(if (selected == AppTabId.BOOKSHELF) 1f else -1f)
                .graphicsLayer { alpha = if (selected == AppTabId.BOOKSHELF) 1f else 0f }
        )
        TabStackDisplay(
            stack = profileStack,
            navigator = profileNavigator,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(if (selected == AppTabId.PROFILE) 1f else -1f)
                .graphicsLayer { alpha = if (selected == AppTabId.PROFILE) 1f else 0f }
        )
    }
}

@Composable
private fun TabStackDisplay(
    stack: MutableList<NavKey>,
    navigator: AppNavigator,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalBaseNavigator provides navigator) {
        NavDisplay(
            backStack = stack,
            modifier = modifier,
            onBack = { if (!navigator.pop()) Unit },
            transitionSpec = pushTransition,
            popTransitionSpec = popTransition,
            predictivePopTransitionSpec = popTransition,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator()
            ),
            entryProvider = entryProvider {
                entry<AppNavKey.HomeTab> { HomeTab.Content() }
                entry<AppNavKey.HistoryTab> { HistoryTab.Content() }
                entry<AppNavKey.BookshelfTab> { FavoriteTab.Content() }
                entry<AppNavKey.ProfileTab> { ProfileTab.Content() }
                entry<AppNavKey.Legacy> { key ->
                    navigator.destination(key.route)?.Content()
                }
            }
        )
    }
}

@Composable
private fun AppNavigationBar(
    selected: AppTabId,
    onSelected: (AppTabId) -> Unit
) {
    NavigationBar(windowInsets = WindowInsets.navigationBars) {
        AppTabId.entries.forEach { tab ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelected(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tabLabel(tab)) }
            )
        }
    }
}

@Composable
private fun AppNavigationRail(
    selected: AppTabId,
    onSelected: (AppTabId) -> Unit
) {
    NavigationRail(windowInsets = WindowInsets(0, 0, 0, 0)) {
        AppTabId.entries.forEach { tab ->
            NavigationRailItem(
                selected = selected == tab,
                onClick = { onSelected(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tabLabel(tab)) },
                alwaysShowLabel = false
            )
        }
    }
}

@Composable
private fun AppExpandedNavigationRail(
    selected: AppTabId,
    onSelected: (AppTabId) -> Unit
) {
    // NavigationRail has a fixed compact width. Expanded windows get a
    // permanent labelled drawer so the shell scales without introducing a
    // second navigation graph or a modal drawer state.
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(232.dp)
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
    ) {
        AppTabId.entries.forEach { tab ->
            NavigationDrawerItem(
                selected = selected == tab,
                onClick = { onSelected(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tabLabel(tab)) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun tabLabel(tab: AppTabId): String = when (tab) {
    AppTabId.HOME -> HomeTab.options.title
    AppTabId.HISTORY -> HistoryTab.options.title
    AppTabId.BOOKSHELF -> FavoriteTab.options.title
    AppTabId.PROFILE -> ProfileTab.options.title
}
