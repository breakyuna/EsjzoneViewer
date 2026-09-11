package com.breakyuna.esjzone.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
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
import com.breakyuna.esjzone.ui.designsystem.glass.AppGlassSpec
import com.breakyuna.esjzone.ui.designsystem.glass.AppGlassMaterial
import com.breakyuna.esjzone.ui.designsystem.glass.AppGlassScene
import com.breakyuna.esjzone.ui.designsystem.glass.AppGlassSurface
import com.breakyuna.esjzone.ui.designsystem.glass.appGlassSource
import com.breakyuna.esjzone.ui.designsystem.glass.rememberAppGlassScene

/**
 * Bottom / side insets compensation for pages whose content scrolls underneath
 * floating frosted-glass islands so terminal list items are never blocked.
 */
val LocalFloatingNavPadding = compositionLocalOf { PaddingValues(0.dp) }

private enum class AppTabId(
    val route: AppNavKey,
    val filledIcon: ImageVector,
    val outlinedIcon: ImageVector
) {
    HOME(AppNavKey.HomeTab, Icons.Filled.Home, Icons.Outlined.Home),
    HISTORY(AppNavKey.HistoryTab, Icons.Filled.History, Icons.Outlined.History),
    BOOKSHELF(AppNavKey.BookshelfTab, Icons.Filled.AutoStories, Icons.Outlined.AutoStories),
    PROFILE(AppNavKey.ProfileTab, Icons.Filled.Person, Icons.Outlined.Person)
}

/**
 * The application shell keeps one Navigation 3 back stack per top-level tab.
 * Compact windows display a bottom floating frosted-glass capsule island hovering
 * over the page content. Medium and Expanded windows display a side vertical
 * floating frosted-glass capsule island.
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
    val widthSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass.windowWidthSizeClass
    val navigationGlassScene = rememberAppGlassScene()
    val tab = AppTabId.valueOf(selectedTab)
    val homeNavigator = remember(homeStack, rootNavigator) { rootNavigator.child(homeStack) }
    val historyNavigator = remember(historyStack, rootNavigator) { rootNavigator.child(historyStack) }
    val bookshelfNavigator = remember(bookshelfStack, rootNavigator) {
        rootNavigator.child(bookshelfStack)
    }
    val profileNavigator = remember(profileStack, rootNavigator) {
        rootNavigator.child(profileStack)
    }
    val selectedStack = when (tab) {
        AppTabId.HOME -> homeStack
        AppTabId.HISTORY -> historyStack
        AppTabId.BOOKSHELF -> bookshelfStack
        AppTabId.PROFILE -> profileStack
    }
    // A tab bar is a root-level control, not a child-page overlay. Keeping it off a pushed
    // destination prevents a second navigation hierarchy from appearing above detail pages.
    val showFloatingNavigation = selectedStack.lastOrNull() == tab.route

    val floatingNavPadding = when (widthSizeClass) {
        WindowWidthSizeClass.COMPACT -> PaddingValues(bottom = 96.dp)
        WindowWidthSizeClass.MEDIUM, WindowWidthSizeClass.EXPANDED -> PaddingValues(start = 84.dp)
        else -> PaddingValues(bottom = 96.dp)
    }

    CompositionLocalProvider(
        LocalAuthorization provides authorization,
        LocalFloatingNavPadding provides if (showFloatingNavigation) floatingNavPadding else PaddingValues(0.dp)
    ) {
        when (widthSizeClass) {
            WindowWidthSizeClass.COMPACT -> {
                Box(
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
                        modifier = Modifier
                            .fillMaxSize()
                            .appGlassSource(navigationGlassScene)
                    )
                    if (showFloatingNavigation) {
                        AppNavigationBar(
                            selected = tab,
                            onSelected = { selectedTab = it.name },
                            glassScene = navigationGlassScene,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
            }

            WindowWidthSizeClass.MEDIUM,
            WindowWidthSizeClass.EXPANDED -> {
                Box(
                    modifier = modifier.windowInsetsPadding(
                        shellSafeDrawing(top = !topInsetConsumed, bottom = true)
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
                        modifier = Modifier
                            .fillMaxSize()
                            .appGlassSource(navigationGlassScene)
                    )
                    if (showFloatingNavigation) {
                        AppSideNavigationBar(
                            selected = tab,
                            onSelected = { selectedTab = it.name },
                            glassScene = navigationGlassScene,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
                                .padding(start = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
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
            predictivePopTransitionSpec = predictivePopTransition,
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
    onSelected: (AppTabId) -> Unit,
    glassScene: AppGlassScene,
    modifier: Modifier = Modifier
) {
    val capsuleShape = RoundedCornerShape(34.dp)
    val rimBrush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.45f),
            MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
            MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
        )
    )
    AppGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = capsuleShape,
                spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            ),
        spec = AppGlassSpec(
            shape = capsuleShape,
            alpha = 0.24f,
            borderAlpha = 0.34f,
            tintAlpha = 0.12f,
            edgeSoftness = 8.dp,
            specularIntensity = 0.82f,
            material = AppGlassMaterial.REGULAR,
            borderBrush = rimBrush
        ),
        scene = glassScene
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .align(Alignment.TopCenter)
                .clip(RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.09f),
                            Color.Transparent
                        )
                    )
                )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppTabId.entries.forEach { tab ->
                FloatingNavHorizontalItem(
                    selected = selected == tab,
                    onClick = { onSelected(tab) },
                    selectedIcon = tab.filledIcon,
                    unselectedIcon = tab.outlinedIcon,
                    label = tabLabel(tab),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AppSideNavigationBar(
    selected: AppTabId,
    onSelected: (AppTabId) -> Unit,
    glassScene: AppGlassScene,
    modifier: Modifier = Modifier
) {
    val capsuleShape = RoundedCornerShape(percent = 50)
    val rimBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.40f),
            MaterialTheme.colorScheme.outline.copy(alpha = 0.20f),
            MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
        ),
        start = Offset.Zero,
        end = Offset(100f, 300f)
    )
    AppGlassSurface(
        modifier = modifier
            .wrapContentSize()
            .shadow(
                elevation = 12.dp,
                shape = capsuleShape,
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                ambientColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
            ),
        spec = AppGlassSpec(
            shape = capsuleShape,
            alpha = 0.24f,
            borderAlpha = 0.28f,
            tintAlpha = 0.12f,
            edgeSoftness = 8.dp,
            specularIntensity = 0.76f,
            material = AppGlassMaterial.REGULAR,
            borderBrush = rimBrush
        ),
        scene = glassScene
    ) {
        Column(
            modifier = Modifier
                .width(58.dp)
                .wrapContentHeight()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppTabId.entries.forEach { tab ->
                FloatingNavVerticalItem(
                    selected = selected == tab,
                    onClick = { onSelected(tab) },
                    selectedIcon = tab.filledIcon,
                    unselectedIcon = tab.outlinedIcon,
                    contentDescription = tabLabel(tab)
                )
            }
        }
    }
}

@Composable
private fun FloatingNavHorizontalItem(
    selected: Boolean,
    onClick: () -> Unit,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    val activeBorderAlpha by animateColorAsState(
        targetValue = if (selected) 0.38f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "nav_item_border"
    )
    val activeBgAlphaTop by animateColorAsState(
        targetValue = if (selected) 0.22f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "nav_item_bg_top"
    )
    val activeBgAlphaBottom by animateColorAsState(
        targetValue = if (selected) 0.10f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "nav_item_bg_bottom"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "nav_item_color"
    )

    val pillShape = RoundedCornerShape(28.dp)
    val primaryColor = MaterialTheme.colorScheme.primary
    val pillBgBrush = remember(primaryColor, activeBgAlphaTop, activeBgAlphaBottom) {
        Brush.verticalGradient(
            colors = listOf(
                primaryColor.copy(alpha = activeBgAlphaTop),
                primaryColor.copy(alpha = activeBgAlphaBottom)
            )
        )
    }
    val pillBorderBrush = remember(primaryColor, activeBorderAlpha) {
        Brush.verticalGradient(
            colors = listOf(
                primaryColor.copy(alpha = activeBorderAlpha),
                primaryColor.copy(alpha = activeBorderAlpha * 0.35f)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(percent = 50))
            .semantics { contentDescription = label }
            .clickable(
                onClick = onClick,
                role = Role.Tab
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(72.dp)
                .height(56.dp)
                .clip(pillShape)
                .background(pillBgBrush)
                .border(
                    BorderStroke(1.dp, pillBorderBrush),
                    shape = pillShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Icon(
                    imageVector = if (selected) selectedIcon else unselectedIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = contentColor,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun FloatingNavVerticalItem(
    selected: Boolean,
    onClick: () -> Unit,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val activeBorderAlpha by animateColorAsState(
        targetValue = if (selected) 0.36f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "nav_v_item_border"
    )
    val activeBgAlphaTop by animateColorAsState(
        targetValue = if (selected) 0.22f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "nav_v_item_bg_top"
    )
    val activeBgAlphaBottom by animateColorAsState(
        targetValue = if (selected) 0.10f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "nav_v_item_bg_bottom"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.94f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "nav_item_color"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val circleBgBrush = remember(primaryColor, activeBgAlphaTop, activeBgAlphaBottom) {
        Brush.verticalGradient(
            colors = listOf(
                primaryColor.copy(alpha = activeBgAlphaTop),
                primaryColor.copy(alpha = activeBgAlphaBottom)
            )
        )
    }
    val circleBorderBrush = remember(primaryColor, activeBorderAlpha) {
        Brush.verticalGradient(
            colors = listOf(
                primaryColor.copy(alpha = activeBorderAlpha),
                primaryColor.copy(alpha = activeBorderAlpha * 0.35f)
            )
        )
    }

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(circleBgBrush)
            .border(
                BorderStroke(1.dp, circleBorderBrush),
                shape = CircleShape
            )
            .clickable(
                onClick = onClick,
                role = Role.Tab
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (selected) selectedIcon else unselectedIcon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun tabLabel(tab: AppTabId): String = when (tab) {
    AppTabId.HOME -> HomeTab.options.title
    AppTabId.HISTORY -> HistoryTab.options.title
    AppTabId.BOOKSHELF -> FavoriteTab.options.title
    AppTabId.PROFILE -> ProfileTab.options.title
}
