@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.breakyuna.esjzone.ui.navigation

import androidx.compose.animation.core.snap
import androidx.compose.material3.ripple
import com.breakyuna.esjzone.ui.designsystem.LocalReduceMotion
import com.breakyuna.esjzone.ui.designsystem.AppMotion
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
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
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.ui.discovery.DiscoveryScaffold
import com.breakyuna.esjzone.ui.product.EmptyState
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
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
 * Additional bottom / side spacing supplied by the navigation shell to page content.
 */
val LocalFloatingNavPadding = compositionLocalOf { PaddingValues(0.dp) }
/** Root tab content can yield the floating island to an edit surface or modal. */
val LocalFloatingNavSuppression = compositionLocalOf<(Boolean) -> Unit> { {} }
internal val LocalHomeReselectRequest = compositionLocalOf { 0 }
internal val LocalBookshelfReselectRequest = compositionLocalOf { 0 }

internal enum class AppTabId(val route: AppNavKey) {
    HOME(AppNavKey.HomeTab),
    HISTORY(AppNavKey.HistoryTab),
    BOOKSHELF(AppNavKey.BookshelfTab),
    PROFILE(AppNavKey.ProfileTab)
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
    androidx.compose.runtime.SideEffect {
        com.breakyuna.esjzone.app.CoverLoadingPolicy.showPage(tab.name)
    }
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
    // Compact navigation stays at tab roots. Wide landscape keeps the rail available
    // while each tab retains its child stack; edit modes can still suppress it.
    val keepWideRail = com.breakyuna.esjzone.ui.designsystem.appUsesWideLayout()
    val railOutsideStack = widthSizeClass != WindowWidthSizeClass.COMPACT && keepWideRail

    val isRootOfSecondaryTab = tab != defaultTabId && (selectedStack.size <= 1 || selectedStack.lastOrNull() == tab.route)
    BackHandler(enabled = isRootOfSecondaryTab) {
        selectedTab = defaultTabId.name
    }

    val isRootOfDefaultTab = tab == defaultTabId && (selectedStack.size <= 1 || selectedStack.lastOrNull() == tab.route)
    AppExitBackHandler(enabled = isRootOfDefaultTab)

    var homeReselectRequest by remember { mutableIntStateOf(0) }
    var bookshelfReselectRequest by remember { mutableIntStateOf(0) }
    var autoResumeHandled by rememberSaveable { mutableStateOf(false) }

    val autoResumeDecisionFinished = LocalAutoResumeDecisionFinished.current
    LaunchedEffect(Unit) {
        try {
            if (!autoResumeHandled) {
                autoResumeHandled = true
                if (PresentationAccess.readerSettings.settings.value.autoResumeLastReading) {
                    try {
                        val latest = withContext(Dispatchers.IO) {
                            PresentationAccess.database.localReadingActivityDao().getLatest()
                        }
                        if (latest != null && com.breakyuna.esjzone.ui.page.historyRowIncluded(
                                PresentationAccess.settings.adult.value, latest.isAdult
                            )) {
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
        } finally {
            autoResumeDecisionFinished()
        }
    }

    val onTabSelected: (AppTabId) -> Unit = { targetTab ->
        focusManager.clearFocus(force = true)
        if (tab == targetTab) {
            if (!keepWideRail || selectedStack.lastOrNull() == tab.route) {
                when (targetTab) {
                    AppTabId.HOME -> homeReselectRequest++
                    AppTabId.HISTORY -> HistoryTab.requestOpenLastReading()
                    AppTabId.BOOKSHELF -> bookshelfReselectRequest++
                    else -> Unit
                }
            }
        } else {
            selectedTab = targetTab.name
        }
    }

    CompositionLocalProvider(
        LocalAuthorization provides authorization,
        LocalHomeReselectRequest provides homeReselectRequest,
        LocalBookshelfReselectRequest provides bookshelfReselectRequest,
        LocalFloatingNavPadding provides PaddingValues(0.dp)
    ) {
        Row(
            modifier = modifier.windowInsetsPadding(
                shellSafeDrawing(top = !topInsetConsumed, bottom = widthSizeClass != WindowWidthSizeClass.COMPACT)
            )
        ) {
            // A wide landscape rail intentionally remains available on child pages.
            if (railOutsideStack && suppressedTabs[tab] != true) {
                AppSideNavigationBar(
                    selected = tab,
                    onSelected = onTabSelected,
                    tabs = orderedTabs,
                    modifier = Modifier.fillMaxHeight()
                )
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                TabStacksDisplay(
                    selected = tab,
                    orderedTabs = orderedTabs,
                    suppressionState = suppressedTabs,
                    homeStack = homeStack,
                    homeNavigator = homeNavigator,
                    historyStack = historyStack,
                    historyNavigator = historyNavigator,
                    bookshelfStack = bookshelfStack,
                    bookshelfNavigator = bookshelfNavigator,
                    profileStack = profileStack,
                    profileNavigator = profileNavigator,
                    rootContent = { tabId, contentMotion, content ->
                        TabRootLayout(
                            showNavigation = !railOutsideStack && suppressedTabs[tabId] != true,
                            sideNavigation = widthSizeClass != WindowWidthSizeClass.COMPACT,
                            contentModifier = contentMotion,
                            navigationVisible = tabId == tab,
                            navigation = {
                                if (widthSizeClass == WindowWidthSizeClass.COMPACT) {
                                    AppNavigationBar(tab, onTabSelected, orderedTabs, Modifier.fillMaxWidth())
                                } else {
                                    AppSideNavigationBar(tab, onTabSelected, orderedTabs, Modifier.fillMaxHeight())
                                }
                            },
                            content = content
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/** Root chrome belongs to its NavDisplay entry, so push/pop never resize the host. */
@Composable
internal fun TabRootLayout(
    showNavigation: Boolean,
    sideNavigation: Boolean,
    navigation: @Composable () -> Unit,
    contentModifier: Modifier = Modifier,
    navigationVisible: Boolean = true,
    content: @Composable () -> Unit
) {
    val navigationContent: @Composable () -> Unit = {
        // Retain the measured space on inactive roots, but draw only one navigation bar.
        Box(Modifier.graphicsLayer { alpha = if (navigationVisible) 1f else 0f }
            .then(if (!navigationVisible) Modifier.clearAndSetSemantics { } else Modifier)) {
            navigation()
        }
    }
    if (sideNavigation) {
        Row(Modifier.fillMaxSize()) {
            if (showNavigation) navigationContent()
            com.breakyuna.esjzone.ui.designsystem.AppViewport(Modifier.weight(1f).fillMaxHeight().then(contentModifier), content)
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            com.breakyuna.esjzone.ui.designsystem.AppViewport(Modifier.weight(1f).fillMaxWidth().then(contentModifier), content)
            if (showNavigation) navigationContent()
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
    // The reader restores hidden system bars as it loses focus. Reserve their
    // final space from the first return frame instead of resizing with that animation.
    return WindowInsets.safeDrawing
        .union(WindowInsets.statusBarsIgnoringVisibility)
        .union(WindowInsets.navigationBarsIgnoringVisibility)
        .only(sides)
}

@Composable
private fun TabStacksDisplay(
    selected: AppTabId,
    orderedTabs: List<AppTabId>,
    suppressionState: MutableMap<AppTabId, Boolean>,
    homeStack: MutableList<NavKey>,
    homeNavigator: AppNavigator,
    historyStack: MutableList<NavKey>,
    historyNavigator: AppNavigator,
    bookshelfStack: MutableList<NavKey>,
    bookshelfNavigator: AppNavigator,
    profileStack: MutableList<NavKey>,
    profileNavigator: AppNavigator,
    rootContent: @Composable (AppTabId, Modifier, @Composable () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    TabSwitchLayers(selected, orderedTabs, modifier) { tabId, active, contentMotion ->
        val (stack, navigator) = when (tabId) {
            AppTabId.HOME -> homeStack to homeNavigator
            AppTabId.HISTORY -> historyStack to historyNavigator
            AppTabId.BOOKSHELF -> bookshelfStack to bookshelfNavigator
            AppTabId.PROFILE -> profileStack to profileNavigator
        }
        TabStackDisplay(
            tabId = tabId,
            suppressionState = suppressionState,
            stack = stack,
            active = active,
            navigator = navigator,
            rootContent = rootContent,
            contentMotion = contentMotion,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** Mount tabs on first selection, then retain visited stacks for this shell instance. */
@Composable
internal fun TabSwitchLayers(
    selected: AppTabId,
    orderedTabs: List<AppTabId>,
    modifier: Modifier = Modifier,
    content: @Composable (AppTabId, Boolean, Modifier) -> Unit
) {
    val reduced = LocalReduceMotion.current
    val transition = updateTransition(targetState = selected, label = "TabSwitch")
    // Do not restore all four page trees on the first frame back from a reader.
    // Back stacks above remain saveable; visited tabs stay mounted across tab switches.
    val mountedTabs = remember { mutableStateMapOf(selected to true) }
    androidx.compose.runtime.SideEffect { mountedTabs[selected] = true }
    Box(modifier) {
        AppTabId.entries.forEach { tabId ->
            if (tabId != selected && mountedTabs[tabId] != true) return@forEach
            key(tabId) {
                val active = selected == tabId
                val alpha = transition.animateFloat(
                    transitionSpec = {
                        if (reduced) snap()
                        else tween(durationMillis = 300, easing = FastOutSlowInEasing)
                    },
                    label = "${tabId.name}TabAlpha"
                ) { targetTab -> if (targetTab == tabId) 1f else 0f }
                val horizontalOffset = transition.animateFloat(
                    transitionSpec = {
                        if (reduced) snap()
                        else tween(durationMillis = 300, easing = FastOutSlowInEasing)
                    },
                    label = "${tabId.name}TabOffset"
                ) { targetTab ->
                    when {
                        targetTab == tabId -> 0f
                        orderedTabs.indexOf(tabId) < orderedTabs.indexOf(targetTab) -> -16f
                        else -> 16f
                    }
                }
                Box(
                    Modifier.fillMaxSize().zIndex(if (active) 1f else 0f)
                        // Fully hidden stacks (including shared overlays) must not paint.
                        .graphicsLayer { this.alpha = if (active || alpha.value > 0f) 1f else 0f }
                        .blockInactiveTabInput(active)
                        .then(if (!active) Modifier.clearAndSetSemantics { } else Modifier)
                ) {
                    content(tabId, active, Modifier.graphicsLayer {
                        this.alpha = alpha.value
                        translationX = horizontalOffset.value.dp.toPx()
                    })
                }
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
    rootContent: @Composable (AppTabId, Modifier, @Composable () -> Unit) -> Unit,
    contentMotion: Modifier,
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
        com.breakyuna.esjzone.app.LocalCoverPage provides tabId.name,
        LocalFloatingNavSuppression provides suppressNavigation,
        LocalLifecycleOwner provides lifecycleOwner,
        LocalNavigationEventDispatcherOwner provides dispatcherOwner
    ) {
        com.breakyuna.esjzone.ui.designsystem.AppViewport(modifier) {
            ContainerTransformLayout(Modifier.fillMaxSize()) {
                NavDisplay(
                    backStack = stack,
                    modifier = Modifier.fillMaxSize(),
                    onBack = { if (!navigator.pop()) Unit },
                    transitionSpec = {
                        if (usesContainerTransform(initialState, targetState)) {
                            containerContentTransform()
                        } else {
                            pushTransition()
                        }
                    },
                    popTransitionSpec = {
                        if (usesContainerTransform(initialState, targetState)) {
                            containerContentTransform(pop = true)
                        } else {
                            popTransition()
                        }
                    },
                    predictivePopTransitionSpec = { edge ->
                        if (usesContainerTransform(initialState, targetState)) {
                            containerContentTransform(pop = true)
                        } else {
                            predictivePopTransition(edge)
                        }
                    },
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator()
                    ),
                    entryProvider = entryProvider {
                        entry<AppNavKey.HomeTab>(metadata = ::containerTransformMetadata) {
                            rootContent(tabId, contentMotion) { HomeTab.Content(navigator) }
                        }
                        entry<AppNavKey.HistoryTab>(metadata = ::containerTransformMetadata) {
                            rootContent(tabId, contentMotion) { HistoryTab.Content(navigator) }
                        }
                        entry<AppNavKey.BookshelfTab>(metadata = ::containerTransformMetadata) {
                            rootContent(tabId, contentMotion) { FavoriteTab.Content(navigator) }
                        }
                        entry<AppNavKey.ProfileTab>(metadata = ::containerTransformMetadata) {
                            rootContent(tabId, contentMotion) { ProfileTab.Content(navigator) }
                        }
                        entry<AppNavKey.Legacy>(metadata = ::containerTransformMetadata) { key ->
                            Box(Modifier.fillMaxSize().then(contentMotion)) {
                                val destination = navigator.destination(key.route)
                                if (destination != null) {
                                    val containerTarget = ContainerTransformTarget.forKey(key, origin = tabId.route)
                                    if (containerTarget != null) {
                                        ContainerTransformDestination(containerTarget) { destination.Content(navigator) }
                                    } else {
                                        destination.Content(navigator)
                                    }
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
                    }
                )
            }
        }
    }
}

private val BottomBarHeight = 68.dp
private val BottomBarIndicatorWidth = 56.dp
private val BottomBarIndicatorHeight = 30.dp

@Composable
private fun AppNavigationBar(
    selected: AppTabId,
    onSelected: (AppTabId) -> Unit,
    tabs: List<AppTabId>,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets
        .union(WindowInsets.navigationBarsIgnoringVisibility.only(WindowInsetsSides.Bottom))
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
                key(tab) {
                    AppNavigationBarItem(
                        tab = tab,
                        selected = selected == tab,
                        onSelected = onSelected,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AppNavigationBarItem(
    tab: AppTabId,
    selected: Boolean,
    onSelected: (AppTabId) -> Unit,
    modifier: Modifier = Modifier
) {
    // Keep per-frame background and label color updates local to this item.
    val selectionProgress = animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = NavigationSelectionAnimationSpec,
        label = "${tab.name}BottomSelection"
    )
    val indicatorColor = MaterialTheme.colorScheme.primaryContainer
    val backgroundColor = indicatorColor.copy(alpha = indicatorColor.alpha * selectionProgress.value)
    val contentColor = lerp(
        MaterialTheme.colorScheme.onSurfaceVariant,
        MaterialTheme.colorScheme.primary,
        selectionProgress.value
    )
    val iconColor = lerp(
        MaterialTheme.colorScheme.onSurfaceVariant,
        MaterialTheme.colorScheme.onPrimaryContainer,
        selectionProgress.value
    )
    Column(
        modifier = modifier
            .fillMaxHeight()
            .selectable(
                selected = selected,
                interactionSource = remember(tab) { MutableInteractionSource() },
                indication = ripple(),
                role = Role.Tab,
                onClick = { onSelected(tab) }
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
    ) {
        Box(
            modifier = Modifier
                .size(width = BottomBarIndicatorWidth, height = BottomBarIndicatorHeight)
                .clip(CircleShape)
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            AppNavigationIcon(
                tab = tab,
                selected = selected,
                progress = selectionProgress,
                tint = iconColor,
                cutoutColor = indicatorColor
            )
        }
        Text(
            text = tabLabel(tab),
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
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
            key(tab) {
                val isSelected = selected == tab
                val selectionProgress = animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0f,
                    animationSpec = NavigationSelectionAnimationSpec,
                    label = "${tab.name}SideSelection"
                )
                val contentColor = lerp(
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    MaterialTheme.colorScheme.primary,
                    selectionProgress.value
                )
                val iconColor = lerp(
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                    selectionProgress.value
                )
                val indicatorColor = MaterialTheme.colorScheme.primaryContainer
                NavigationRailItem(
                    selected = isSelected,
                    onClick = { onSelected(tab) },
                    icon = {
                        Box(
                            Modifier.drawBehind {
                                val width = 56.dp.toPx()
                                val height = 32.dp.toPx()
                                drawRoundRect(
                                    color = indicatorColor.copy(
                                        alpha = indicatorColor.alpha * selectionProgress.value
                                    ),
                                    topLeft = Offset((size.width - width) / 2f, (size.height - height) / 2f),
                                    size = Size(width, height),
                                    cornerRadius = CornerRadius(height / 2f)
                                )
                            }
                        ) {
                            AppNavigationIcon(
                                tab = tab,
                                selected = isSelected,
                                progress = selectionProgress,
                                tint = iconColor,
                                cutoutColor = indicatorColor
                            )
                        }
                    },
                    label = {
                        Text(
                            text = tabLabel(tab),
                            color = contentColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    },
                    alwaysShowLabel = true,
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
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
