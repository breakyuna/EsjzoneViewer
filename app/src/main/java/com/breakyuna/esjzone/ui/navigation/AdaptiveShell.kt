package com.breakyuna.esjzone.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.ui.discovery.DiscoveryScaffold
import com.breakyuna.esjzone.ui.product.EmptyState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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

/**
 * Bottom / side insets compensation for pages whose content scrolls underneath
 * floating glass islands so terminal list items are never blocked.
 */
val LocalFloatingNavPadding = compositionLocalOf { PaddingValues(0.dp) }
/** Root tab content can yield the floating island to an edit surface or modal. */
val LocalFloatingNavSuppression = compositionLocalOf<(Boolean) -> Unit> { {} }

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
 * Compact windows display a bottom navigation bar.
 * Medium and Expanded windows display a side navigation rail.
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
    val widthSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass.windowWidthSizeClass
    val savedNavigationOrder by PresentationAccess.settings.navigationOrder
    val savedStartTab by PresentationAccess.settings.startTab
    val orderedTabs = remember(savedNavigationOrder) {
        val byName = AppTabId.entries.associateBy { it.name }
        savedNavigationOrder.mapNotNull(byName::get).let { saved ->
            saved + AppTabId.entries.filterNot { it in saved }
        }
    }
    val defaultTabId = remember(savedStartTab, orderedTabs) {
        if (savedStartTab == com.breakyuna.esjzone.data.settings.SettingsDefaults.START_TAB_FOLLOW_NAV) {
            orderedTabs.firstOrNull() ?: AppTabId.HOME
        } else {
            AppTabId.entries.find { it.name == savedStartTab } ?: AppTabId.HOME
        }
    }
    var selectedTab by rememberSaveable { mutableStateOf(defaultTabId.name) }
    val suppressedTabs = remember { mutableStateMapOf<AppTabId, Boolean>() }
    val tab = AppTabId.entries.find { it.name == selectedTab } ?: defaultTabId
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
    val showFloatingNavigation = selectedStack.lastOrNull() == tab.route && suppressedTabs[tab] != true

    val floatingNavPadding = PaddingValues(0.dp)

    val isRootOfSecondaryTab = tab != defaultTabId && (selectedStack.size <= 1 || selectedStack.lastOrNull() == tab.route)
    BackHandler(enabled = isRootOfSecondaryTab) {
        selectedTab = defaultTabId.name
    }

    val isRootOfDefaultTab = tab == defaultTabId && (selectedStack.size <= 1 || selectedStack.lastOrNull() == tab.route)
    AppExitBackHandler(enabled = isRootOfDefaultTab)

    var lastHistoryTabClickTime by remember { mutableStateOf(0L) }
    var autoResumeHandled by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!autoResumeHandled) {
            autoResumeHandled = true
            if (PresentationAccess.readerSettings.settings.value.autoResumeLastReading) {
                try {
                    val latest = withContext(Dispatchers.IO) {
                        PresentationAccess.database.localReadingActivityDao().getLatest()
                    }
                    if (latest != null) {
                        selectedTab = defaultTabId.name
                        val defaultNavigator = when (defaultTabId) {
                            AppTabId.HOME -> homeNavigator
                            AppTabId.HISTORY -> historyNavigator
                            AppTabId.BOOKSHELF -> bookshelfNavigator
                            AppTabId.PROFILE -> profileNavigator
                        }
                        val defaultStack = when (defaultTabId) {
                            AppTabId.HOME -> homeStack
                            AppTabId.HISTORY -> historyStack
                            AppTabId.BOOKSHELF -> bookshelfStack
                            AppTabId.PROFILE -> profileStack
                        }
                        if (defaultStack.size <= 1) {
                            HistoryTab.openLocalActivity(defaultNavigator, latest)
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    com.breakyuna.esjzone.util.AppLogger.e(
                        "AdaptiveShell",
                        "Failed to auto resume latest reading",
                        e
                    )
                }
            }
        }
    }

    val onTabSelected: (AppTabId) -> Unit = { targetTab ->
        focusManager.clearFocus(force = true)
        if (tab == targetTab) {
            if (targetTab == AppTabId.HISTORY) {
                val now = System.currentTimeMillis()
                if (now - lastHistoryTabClickTime < 400L) {
                    HistoryTab.requestOpenLastReading()
                    lastHistoryTabClickTime = 0L
                } else {
                    lastHistoryTabClickTime = now
                }
            }
        } else {
            selectedTab = targetTab.name
            if (targetTab == AppTabId.HISTORY) {
                lastHistoryTabClickTime = System.currentTimeMillis()
            }
        }
    }

    CompositionLocalProvider(
        LocalAuthorization provides authorization,
        LocalFloatingNavPadding provides if (showFloatingNavigation) floatingNavPadding else PaddingValues(0.dp)
    ) {
        when (widthSizeClass) {
            WindowWidthSizeClass.COMPACT -> {
                Column(
                    modifier = modifier.windowInsetsPadding(
                        shellSafeDrawing(top = !topInsetConsumed, bottom = false)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        TabStacksDisplay(
                            selected = tab,
                            suppressionState = suppressedTabs,
                            homeStack = homeStack,
                            homeNavigator = homeNavigator,
                            historyStack = historyStack,
                            historyNavigator = historyNavigator,
                            bookshelfStack = bookshelfStack,
                            bookshelfNavigator = bookshelfNavigator,
                            profileStack = profileStack,
                            profileNavigator = profileNavigator,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (showFloatingNavigation) {
                        AppNavigationBar(
                            selected = tab,
                            onSelected = onTabSelected,
                            tabs = orderedTabs,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            WindowWidthSizeClass.MEDIUM,
            WindowWidthSizeClass.EXPANDED -> {
                Row(
                    modifier = modifier.windowInsetsPadding(
                        shellSafeDrawing(top = !topInsetConsumed, bottom = true)
                    )
                ) {
                    if (showFloatingNavigation) {
                        AppSideNavigationBar(
                            selected = tab,
                            onSelected = onTabSelected,
                            tabs = orderedTabs,
                            modifier = Modifier.fillMaxHeight()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        TabStacksDisplay(
                            selected = tab,
                            suppressionState = suppressedTabs,
                            homeStack = homeStack,
                            homeNavigator = homeNavigator,
                            historyStack = historyStack,
                            historyNavigator = historyNavigator,
                            bookshelfStack = bookshelfStack,
                            bookshelfNavigator = bookshelfNavigator,
                            profileStack = profileStack,
                            profileNavigator = profileNavigator,
                            modifier = Modifier.fillMaxSize()
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
    suppressionState: MutableMap<AppTabId, Boolean>,
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
        AppTabId.entries.forEach { tabId ->
            key(tabId) {
                val (stack, navigator) = when (tabId) {
                    AppTabId.HOME -> homeStack to homeNavigator
                    AppTabId.HISTORY -> historyStack to historyNavigator
                    AppTabId.BOOKSHELF -> bookshelfStack to bookshelfNavigator
                    AppTabId.PROFILE -> profileStack to profileNavigator
                }
                val active = selected == tabId
                val alpha by animateFloatAsState(
                    targetValue = if (active) 1f else 0f,
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                    label = "${tabId.name}TabAlpha"
                )
                val zIndex = if (active) 1f else 0f
                TabStackDisplay(
                    tabId = tabId,
                    suppressionState = suppressionState,
                    stack = stack,
                    active = active,
                    navigator = navigator,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(zIndex)
                        .graphicsLayer { this.alpha = alpha }
                        .blockInactiveTabInput(active)
                        .then(if (!active) Modifier.clearAndSetSemantics { } else Modifier)
                )
            }
        }
    }
}

/** Invisible, retained tab trees must never receive a pointer that missed the active tab. */
private fun Modifier.blockInactiveTabInput(active: Boolean): Modifier = pointerInput(active) {
    if (!active) awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}

@Composable
private fun TabStackDisplay(
    tabId: AppTabId,
    suppressionState: MutableMap<AppTabId, Boolean>,
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
    val suppressNavigation = remember(tabId, suppressionState) {
        { suppressed: Boolean -> suppressionState[tabId] = suppressed }
    }
    CompositionLocalProvider(
        LocalBaseNavigator provides navigator,
        LocalFloatingNavSuppression provides suppressNavigation,
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
                    val destination = navigator.destination(key.route)
                    if (destination != null) {
                        destination.Content()
                    } else {
                        DiscoveryScaffold(
                            title = stringResource(R.string.load_failed_short),
                            onBack = { if (!navigator.pop()) Unit }
                        ) { padding ->
                            EmptyState(
                                title = stringResource(R.string.load_failed_short),
                                message = stringResource(R.string.community_empty_guidance),
                                modifier = Modifier.fillMaxSize().padding(padding),
                                actionLabel = stringResource(R.string.close),
                                onAction = { if (!navigator.pop()) Unit }
                            )
                        }
                    }
                }
            }
        )
    }
}

private val BottomBarHeight = 52.dp

@Composable
private fun AppNavigationBar(
    selected: AppTabId,
    onSelected: (AppTabId) -> Unit,
    tabs: List<AppTabId>,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = NavigationBarDefaults.Elevation
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(windowInsets)
                .height(BottomBarHeight)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val isSelected = selected == tab
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onSelected(tab) },
                    icon = {
                        Icon(
                            imageVector = if (isSelected) tab.filledIcon else tab.outlinedIcon,
                            contentDescription = tabLabel(tab),
                            modifier = Modifier.offset(y = 3.dp)
                        )
                    },
                    label = {
                        Text(
                            text = tabLabel(tab),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun AppSideNavigationBar(
    selected: AppTabId,
    onSelected: (AppTabId) -> Unit,
    tabs: List<AppTabId>,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        Spacer(modifier = Modifier.weight(1f))
        tabs.forEach { tab ->
            val isSelected = selected == tab
            NavigationRailItem(
                selected = isSelected,
                onClick = { onSelected(tab) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) tab.filledIcon else tab.outlinedIcon,
                        contentDescription = tabLabel(tab)
                    )
                },
                label = {
                    Text(
                        text = tabLabel(tab),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                },
                alwaysShowLabel = true,
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun tabLabel(tab: AppTabId): String = when (tab) {
    AppTabId.HOME -> HomeTab.options.title
    AppTabId.HISTORY -> HistoryTab.options.title
    AppTabId.BOOKSHELF -> FavoriteTab.options.title
    AppTabId.PROFILE -> ProfileTab.options.title
}
