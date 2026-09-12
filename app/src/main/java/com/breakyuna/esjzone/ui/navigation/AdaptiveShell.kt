package com.breakyuna.esjzone.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalFocusManager
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.ui.tab.FavoriteTab
import com.breakyuna.esjzone.ui.tab.HistoryTab
import com.breakyuna.esjzone.ui.tab.HomeTab
import com.breakyuna.esjzone.ui.tab.ProfileTab
import com.breakyuna.esjzone.ui.designsystem.glass.AppGlassScene
import com.breakyuna.esjzone.ui.designsystem.glass.NavigationGlassMetrics
import com.breakyuna.esjzone.ui.designsystem.glass.AppNavigationGlassSurface
import com.breakyuna.esjzone.ui.designsystem.glass.appGlassSource
import com.breakyuna.esjzone.ui.designsystem.glass.rememberAppGlassScene

/**
 * Bottom / side insets compensation for pages whose content scrolls underneath
 * floating glass islands so terminal list items are never blocked.
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
 * Compact windows display a bottom floating crystal-glass capsule island hovering
 * over the page content. Medium and Expanded windows display a side vertical
 * floating crystal-glass capsule island.
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
    val focusManager = LocalFocusManager.current
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
                            onSelected = { focusManager.clearFocus(force = true); selectedTab = it.name },
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
                            onSelected = { focusManager.clearFocus(force = true); selectedTab = it.name },
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
            active = selected == AppTabId.HOME,
            navigator = homeNavigator,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(if (selected == AppTabId.HOME) 1f else -1f)
                .graphicsLayer { alpha = if (selected == AppTabId.HOME) 1f else 0f }
                .then(if (selected != AppTabId.HOME) Modifier.clearAndSetSemantics { } else Modifier)
        )
        TabStackDisplay(
            stack = historyStack,
            active = selected == AppTabId.HISTORY,
            navigator = historyNavigator,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(if (selected == AppTabId.HISTORY) 1f else -1f)
                .graphicsLayer { alpha = if (selected == AppTabId.HISTORY) 1f else 0f }
                .then(if (selected != AppTabId.HISTORY) Modifier.clearAndSetSemantics { } else Modifier)
        )
        TabStackDisplay(
            stack = bookshelfStack,
            active = selected == AppTabId.BOOKSHELF,
            navigator = bookshelfNavigator,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(if (selected == AppTabId.BOOKSHELF) 1f else -1f)
                .graphicsLayer { alpha = if (selected == AppTabId.BOOKSHELF) 1f else 0f }
                .then(if (selected != AppTabId.BOOKSHELF) Modifier.clearAndSetSemantics { } else Modifier)
        )
        TabStackDisplay(
            stack = profileStack,
            active = selected == AppTabId.PROFILE,
            navigator = profileNavigator,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(if (selected == AppTabId.PROFILE) 1f else -1f)
                .graphicsLayer { alpha = if (selected == AppTabId.PROFILE) 1f else 0f }
                .then(if (selected != AppTabId.PROFILE) Modifier.clearAndSetSemantics { } else Modifier)
        )
    }
}

@Composable
private fun TabStackDisplay(
    stack: MutableList<NavKey>,
    active: Boolean,
    navigator: AppNavigator,
    modifier: Modifier = Modifier
) {
    // Alpha alone does not disable hidden pages' lifecycle-bound back handlers.
    // Keep state owners mounted but suspend off-screen entries below STARTED.
    val lifecycleOwner = rememberLifecycleOwner(
        maxLifecycle = if (active) Lifecycle.State.RESUMED else Lifecycle.State.CREATED
    )
    val dispatcherOwner = rememberNavigationEventDispatcherOwner(enabled = active)
    CompositionLocalProvider(
        LocalBaseNavigator provides navigator,
        LocalLifecycleOwner provides lifecycleOwner,
        LocalNavigationEventDispatcherOwner provides dispatcherOwner
    ) {
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
    AppNavigationGlassSurface(
        scene = glassScene,
        selectedFraction = (selected.ordinal + 0.5f) / AppTabId.entries.size,
        itemCount = AppTabId.entries.size,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(NavigationGlassMetrics.bottomHeight)
                .padding(
                    horizontal = NavigationGlassMetrics.horizontalPadding,
                    vertical = NavigationGlassMetrics.bottomVerticalPadding
                )
                .selectableGroup(),
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
    AppNavigationGlassSurface(
        scene = glassScene,
        selectedFraction = (selected.ordinal + 0.5f) / AppTabId.entries.size,
        itemCount = AppTabId.entries.size,
        modifier = modifier.wrapContentSize(),
        vertical = true
    ) {
        Column(
            modifier = Modifier
                .width(NavigationGlassMetrics.railWidth)
                .wrapContentHeight()
                .padding(
                    horizontal = NavigationGlassMetrics.horizontalPadding,
                    vertical = NavigationGlassMetrics.railVerticalPadding
                )
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(NavigationGlassMetrics.railItemGap, Alignment.CenterVertically),
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

private data class NavigationItemColors(
    val content: Color,
    val halo: Color
)

/** The same readable selection treatment for the bottom and side capsules. */
@Composable
private fun navigationItemColors(selected: Boolean): NavigationItemColors {
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.onPrimaryContainer else colors.onSurface,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "nav_content"
    )
    return NavigationItemColors(
        content = contentColor,
        halo = colors.surface.copy(alpha = if (dark) 0.76f else 0.70f)
    )
}

/** Small feathered backing behind glyphs; leaves the pane and selection edges transparent. */
private fun Modifier.navigationContentHalo(color: Color): Modifier = drawWithCache {
    val radius = (size.height * 0.5f).coerceAtLeast(1f)
    val horizontalScale = size.width / (radius * 2f)
    val brush = Brush.radialGradient(
        0f to color,
        0.55f to color.copy(alpha = color.alpha * 0.90f),
        1f to color.copy(alpha = 0f),
        radius = radius
    )
    onDrawBehind {
        // Fit an ellipse inside the content bounds; never cut a halo into a hard rectangle.
        scale(scaleX = horizontalScale, scaleY = 1f) {
            drawCircle(brush = brush, radius = radius)
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
    val colors = navigationItemColors(selected)
    val pillShape = RoundedCornerShape(percent = 50)
    val labelHaloRadius = with(LocalDensity.current) { 2.dp.toPx() }
    Box(
        modifier = modifier.fillMaxHeight(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = NavigationGlassMetrics.bottomItemMaxWidth)
                .fillMaxWidth()
                .height(NavigationGlassMetrics.bottomItemHeight)
                .clip(pillShape)
                // Fixed hit targets stay above the moving decorative lens.
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.Tab,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .navigationContentHalo(colors.halo)
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Icon(
                    imageVector = if (selected) selectedIcon else unselectedIcon,
                    contentDescription = null,
                    tint = colors.content,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        shadow = Shadow(
                            color = colors.halo.copy(alpha = 0.95f),
                            blurRadius = labelHaloRadius
                        )
                    ),
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = colors.content,
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
    val colors = navigationItemColors(selected)
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(NavigationGlassMetrics.railItemSize)
            .clip(CircleShape)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(36.dp).navigationContentHalo(colors.halo),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (selected) selectedIcon else unselectedIcon,
                contentDescription = contentDescription,
                tint = colors.content,
                modifier = Modifier.size(24.dp)
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
