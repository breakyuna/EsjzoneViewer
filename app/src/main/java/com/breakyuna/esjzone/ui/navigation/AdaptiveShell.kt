package com.breakyuna.esjzone.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.key
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
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
import com.breakyuna.esjzone.ui.designsystem.glass.LocalNavigationAdaptiveBackdrop
import com.breakyuna.esjzone.ui.designsystem.glass.rememberNavigationItemInteraction
import com.breakyuna.esjzone.ui.designsystem.glass.AppNavigationGlassSurface
import com.breakyuna.esjzone.ui.designsystem.glass.appGlassSource
import com.breakyuna.esjzone.ui.designsystem.glass.rememberAppGlassScene

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
    val savedNavigationOrder by PresentationAccess.settings.navigationOrder
    val orderedTabs = remember(savedNavigationOrder) {
        val byName = AppTabId.entries.associateBy { it.name }
        savedNavigationOrder.mapNotNull(byName::get).let { saved ->
            saved + AppTabId.entries.filterNot { it in saved }
        }
    }
    val suppressedTabs = remember { mutableStateMapOf<AppTabId, Boolean>() }
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
    val showFloatingNavigation = selectedStack.lastOrNull() == tab.route && suppressedTabs[tab] != true

    val floatingNavPadding = when (widthSizeClass) {
        WindowWidthSizeClass.COMPACT -> PaddingValues(bottom = 96.dp)
        WindowWidthSizeClass.MEDIUM, WindowWidthSizeClass.EXPANDED -> PaddingValues(start = 84.dp)
        else -> PaddingValues(bottom = 96.dp)
    }

    val isRootOfSecondaryTab = tab != AppTabId.HOME && (selectedStack.size <= 1 || selectedStack.lastOrNull() == tab.route)
    BackHandler(enabled = isRootOfSecondaryTab) {
        selectedTab = AppTabId.HOME.name
    }

    var lastHistoryTabClickTime by remember { mutableStateOf(0L) }

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
                Box(
                    modifier = modifier.windowInsetsPadding(
                        shellSafeDrawing(top = !topInsetConsumed, bottom = false)
                    )
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
                        modifier = Modifier
                            .fillMaxSize()
                            .appGlassSource(navigationGlassScene)
                    )
                    if (showFloatingNavigation) {
                        AppNavigationBar(
                            selected = tab,
                            onSelected = onTabSelected,
                            tabs = orderedTabs,
                            glassScene = navigationGlassScene,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                // Navigation sits just above ordinary tab content, not above
                                // page-owned overlays (which reserve higher levels).
                                .zIndex(1f)
                                .windowInsetsPadding(WindowInsets.navigationBars)
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
                        suppressionState = suppressedTabs,
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
                            onSelected = onTabSelected,
                            tabs = orderedTabs,
                            glassScene = navigationGlassScene,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .zIndex(1f)
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
        TabStackDisplay(
            tabId = AppTabId.HOME,
            suppressionState = suppressionState,
            stack = homeStack,
            active = selected == AppTabId.HOME,
            navigator = homeNavigator,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(if (selected == AppTabId.HOME) 0f else -1f)
                .graphicsLayer { alpha = if (selected == AppTabId.HOME) 1f else 0f }
                .blockInactiveTabInput(selected == AppTabId.HOME)
                .then(if (selected != AppTabId.HOME) Modifier.clearAndSetSemantics { } else Modifier)
        )
        TabStackDisplay(
            tabId = AppTabId.HISTORY,
            suppressionState = suppressionState,
            stack = historyStack,
            active = selected == AppTabId.HISTORY,
            navigator = historyNavigator,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(if (selected == AppTabId.HISTORY) 0f else -1f)
                .graphicsLayer { alpha = if (selected == AppTabId.HISTORY) 1f else 0f }
                .blockInactiveTabInput(selected == AppTabId.HISTORY)
                .then(if (selected != AppTabId.HISTORY) Modifier.clearAndSetSemantics { } else Modifier)
        )
        TabStackDisplay(
            tabId = AppTabId.BOOKSHELF,
            suppressionState = suppressionState,
            stack = bookshelfStack,
            active = selected == AppTabId.BOOKSHELF,
            navigator = bookshelfNavigator,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(if (selected == AppTabId.BOOKSHELF) 0f else -1f)
                .graphicsLayer { alpha = if (selected == AppTabId.BOOKSHELF) 1f else 0f }
                .blockInactiveTabInput(selected == AppTabId.BOOKSHELF)
                .then(if (selected != AppTabId.BOOKSHELF) Modifier.clearAndSetSemantics { } else Modifier)
        )
        TabStackDisplay(
            tabId = AppTabId.PROFILE,
            suppressionState = suppressionState,
            stack = profileStack,
            active = selected == AppTabId.PROFILE,
            navigator = profileNavigator,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(if (selected == AppTabId.PROFILE) 0f else -1f)
                .graphicsLayer { alpha = if (selected == AppTabId.PROFILE) 1f else 0f }
                .blockInactiveTabInput(selected == AppTabId.PROFILE)
                .then(if (selected != AppTabId.PROFILE) Modifier.clearAndSetSemantics { } else Modifier)
        )
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

@Composable
private fun AppNavigationBar(
    selected: AppTabId,
    onSelected: (AppTabId) -> Unit,
    tabs: List<AppTabId>,
    glassScene: AppGlassScene,
    modifier: Modifier = Modifier
) {
    val navShape = RoundedCornerShape(percent = 50)
    AppNavigationGlassSurface(
        scene = glassScene,
        selectedFraction = ((tabs.indexOf(selected).coerceAtLeast(0)) + 0.5f) / tabs.size,
        itemCount = tabs.size,
        shape = navShape,
        modifier = modifier.widthIn(max = NavigationGlassMetrics.bottomMaxWidth).fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(NavigationGlassMetrics.bottomHeight)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(
                    horizontal = NavigationGlassMetrics.horizontalPadding,
                    vertical = NavigationGlassMetrics.bottomVerticalPadding
                )
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                key(tab) {
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
}

@Composable
private fun AppSideNavigationBar(
    selected: AppTabId,
    onSelected: (AppTabId) -> Unit,
    tabs: List<AppTabId>,
    glassScene: AppGlassScene,
    modifier: Modifier = Modifier
) {
    AppNavigationGlassSurface(
        scene = glassScene,
        selectedFraction = ((tabs.indexOf(selected).coerceAtLeast(0)) + 0.5f) / tabs.size,
        itemCount = tabs.size,
        modifier = modifier.wrapContentSize(),
        vertical = true
    ) {
        Column(
            modifier = Modifier
                .width(NavigationGlassMetrics.railWidth)
                .wrapContentHeight()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(
                    horizontal = NavigationGlassMetrics.horizontalPadding,
                    vertical = NavigationGlassMetrics.railVerticalPadding
                )
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(NavigationGlassMetrics.railItemGap, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            tabs.forEach { tab ->
                key(tab) {
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
}

private data class NavigationItemColors(
    val content: Color,
    val halo: Color,
    val labelShadow: Color
)

/** The same readable selection treatment for the bottom and side capsules. */
@Composable
private fun navigationItemColors(selected: Boolean): NavigationItemColors {
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    val adaptive = LocalNavigationAdaptiveBackdrop.current
    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.onPrimaryContainer else colors.onSurface,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "nav_content"
    )
    return NavigationItemColors(
        content = contentColor,
        // A supported GPU pass adapts the backdrop itself; avoid another fixed halo on top.
        halo = if (adaptive) Color.Transparent else
            colors.surface.copy(alpha = if (dark) 0.68f else 0.60f),
        labelShadow = colors.surface.copy(alpha = if (adaptive) 0.28f else 0.75f)
    )
}

/** Small feathered backing behind glyphs; leaves the pane and selection edges transparent. */
private fun Modifier.navigationContentHalo(color: Color): Modifier = drawWithCache {
    if (color == Color.Transparent || color.alpha <= 0.01f) {
        onDrawBehind { }
    } else {
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
}

/** Finite local feedback also works when the platform cannot render the glass shader. */
@Composable
private fun Modifier.navigationItemFeedback(source: InteractionSource, color: Color): Modifier {
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val press = animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 900f),
        label = "navigation_item_press"
    )
    val focus = animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 900f),
        label = "navigation_item_focus"
    )
    return drawWithCache {
        val inset = NavigationGlassMetrics.selectionHorizontalInset.toPx()
        val bounds = Size((size.width - inset * 2).coerceAtLeast(0f), (size.height - inset * 2).coerceAtLeast(0f))
        val radius = CornerRadius(bounds.minDimension / 2f)
        val outline = Stroke(1.dp.toPx())
        onDrawBehind {
            drawRoundRect(
                color = color.copy(alpha = 0.05f * press.value),
                topLeft = Offset(inset, inset),
                size = bounds,
                cornerRadius = radius
            )
            drawRoundRect(
                color = color.copy(alpha = 0.60f * focus.value),
                topLeft = Offset(inset, inset),
                size = bounds,
                cornerRadius = radius,
                style = outline
            )
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
    val interaction = rememberNavigationItemInteraction()
    val labelShadowRadius = with(LocalDensity.current) { 2.dp.toPx() }
    Box(
        // The entire weighted slot is actionable, including space beside the label.
        modifier = modifier
            .fillMaxHeight()
            .onGloballyPositioned(interaction::onPlaced)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interaction.source,
                indication = null
            )
            .navigationItemFeedback(interaction.source, colors.content),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(NavigationGlassMetrics.bottomItemHeight),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .navigationContentHalo(colors.halo)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
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
                            color = colors.labelShadow,
                            blurRadius = labelShadowRadius
                        )
                    ),
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
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
    val interaction = rememberNavigationItemInteraction()
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(NavigationGlassMetrics.railItemSize)
            .clip(CircleShape)
            .onGloballyPositioned(interaction::onPlaced)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interaction.source,
                indication = null
            )
            .navigationItemFeedback(interaction.source, colors.content),
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
