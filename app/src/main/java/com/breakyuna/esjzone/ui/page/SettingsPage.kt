package com.breakyuna.esjzone.ui.page

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.breakyuna.esjzone.AppLanguage
import com.breakyuna.esjzone.ui.reader.ReaderScript
import com.breakyuna.esjzone.BuildConfig
import com.breakyuna.esjzone.Constants
import com.breakyuna.esjzone.update.ReleaseCheckState
import com.breakyuna.esjzone.update.ReleaseUpdateChecker
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.accountContentWidth
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.hasCredentials
import com.breakyuna.esjzone.database.BookshelfRepository
import com.breakyuna.esjzone.network.features.CommunitySyncManager
import com.breakyuna.esjzone.ui.navigation.AppDestination
import com.breakyuna.esjzone.ui.navigation.LocalAppNavigator
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.rememberAppViewModel
import com.breakyuna.esjzone.ui.screen.LoginScreen
import com.breakyuna.esjzone.ui.screen.MainScreen
import com.breakyuna.esjzone.util.LocaleHelper
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Account preferences and local maintenance; every write retains existing semantics. */
object SettingsPage : AppDestination {
    override val key: String = "SettingsPage"
    private fun readResolve(): Any = SettingsPage

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val rootNavigator = LocalAppNavigator.current
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val model = rememberAppViewModel { SettingsPageModel() }
        val state by model.state.collectAsStateWithLifecycle()
        val autoBackup by PresentationAccess.autoBackup.enabled.collectAsStateWithLifecycle()
        val adult by PresentationAccess.settings.adult
        val hideHomeRecommendations by PresentationAccess.settings.hideHomeRecommendations
        val domain by PresentationAccess.settings.domain
        val language by PresentationAccess.settings.language
        val autoSave by PresentationAccess.settings.readerAutoSave
        val downloadConcurrency by PresentationAccess.settings.downloadConcurrency
        val navigationOrder by PresentationAccess.settings.navigationOrder
        val startTab by PresentationAccess.settings.startTab
        val readerSettings by PresentationAccess.readerSettings.settings.collectAsStateWithLifecycle()
        val checkState by ReleaseUpdateChecker.status.collectAsStateWithLifecycle()
        val autoCheck by ReleaseUpdateChecker.autoCheck.collectAsStateWithLifecycle()
        var showLogout by remember { mutableStateOf(false) }
        val sectionStateHolder = rememberSaveableStateHolder()
        var section by rememberSaveable { mutableStateOf("MAIN") }
        BackHandler(enabled = section != "MAIN") { section = "MAIN" }
        val pageTitle = stringResource(when (section) {
            "NAVIGATION" -> R.string.settings_navigation_section
            "READING" -> R.string.settings_reading_download_section
            "STORAGE" -> R.string.settings_storage_section
            "ABOUT" -> R.string.about
            else -> R.string.settings_screen_title
        })
        var switchingDomain by remember { mutableStateOf(false) }
        var siteUnavailable by remember { mutableStateOf(false) }
        var draggingNavigationItem by remember { mutableStateOf<String?>(null) }
        var editableNavigationOrder by remember { mutableStateOf(navigationOrder) }
        LaunchedEffect(navigationOrder, draggingNavigationItem) {
            if (draggingNavigationItem == null) editableNavigationOrder = navigationOrder
        }
        LaunchedEffect(Unit) {
            ReleaseUpdateChecker.initialize(context)
        }
        LaunchedEffect(section) {
            if (section == "STORAGE") model.refreshCacheStats()
        }
        LaunchedEffect(state.logoutCompleted) { if (state.logoutCompleted) rootNavigator?.replaceAll(LoginScreen) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(pageTitle, style = AppTypography.titleMedium) },
                    navigationIcon = { BackIconButton { if (section == "MAIN") navigator?.pop() else section = "MAIN" } },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { padding ->
            AnimatedContent(
                targetState = section,
                modifier = Modifier.fillMaxSize().padding(padding),
                transitionSpec = {
                    val direction = if (targetState == "MAIN") {
                        AnimatedContentTransitionScope.SlideDirection.Right
                    } else {
                        AnimatedContentTransitionScope.SlideDirection.Left
                    }
                    slideIntoContainer(direction, tween(250)) togetherWith
                        slideOutOfContainer(direction, tween(250))
                },
                label = "SettingsSectionTransition"
            ) { displayedSection ->
            sectionStateHolder.SaveableStateProvider(displayedSection) {
            Column(
                Modifier
                    .fillMaxSize()
                    .accountContentWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = AppSpacing.lg, end = AppSpacing.lg, top = AppSpacing.sm, bottom = AppSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
            ) {
                if (displayedSection == "MAIN") {
                    SettingsSection(title = stringResource(R.string.settings_general_section)) {
                        InlineSettingRow(
                            title = stringResource(R.string.settings_language_section),
                            options = listOf(
                                stringResource(R.string.settings_language_system_short),
                                stringResource(R.string.settings_language_chinese_short),
                                stringResource(R.string.settings_language_en)
                            ),
                            selectedIndex = language.ordinal
                        ) { index ->
                            val candidate = AppLanguage.entries[index]
                            PresentationAccess.settings.setLanguage(candidate)
                            LocaleHelper.syncSystemLocale(context, candidate)
                            model.persist("language", candidate.code)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                        InlineSettingRow(
                            title = stringResource(R.string.settings_script_section),
                            options = listOf(
                                stringResource(R.string.reader_script_original),
                                stringResource(R.string.reader_script_simplified),
                                stringResource(R.string.reader_script_traditional)
                            ),
                            selectedIndex = readerSettings.script.ordinal
                        ) { index ->
                            PresentationAccess.readerSettings.saveInBackground(
                                readerSettings.copy(script = ReaderScript.entries[index])
                            )
                        }
                    }
                    SettingsSection {
                        ToggleRow(stringResource(R.string.auto_backup), autoBackup) {
                            model.setAutoBackup(it, BookshelfRepository.scopeFor(authorization))
                        }
                        LinkRow(Icons.Filled.Download, stringResource(R.string.local_backup)) {
                            navigator?.pushIfNotCurrent(LocalBackupPage)
                        }
                        if (state.autoBackupFailed) Text(stringResource(R.string.backup_failed),
                            color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(AppSpacing.md))
                    }
                    SettingsSection(title = stringResource(R.string.settings_content_section)) {
                        ToggleRow(stringResource(R.string.settings_showadultcontent), adult) { PresentationAccess.settings.setAdult(it); model.persist("show_adult", it.toString()) }
                        ToggleRow(
                            stringResource(R.string.settings_hide_home_recommendations),
                            hideHomeRecommendations
                        ) { PresentationAccess.settings.setHideHomeRecommendations(it) }
                    }
                    SettingsSection(title = stringResource(R.string.settings_network_section)) {
                        SiteSettingRow(
                            domains = PresentationAccess.settings.DOMAINS,
                            selectedDomain = domain,
                            enabled = !switchingDomain
                        ) { candidate ->
                            if (candidate != domain && !switchingDomain) {
                                switchingDomain = true
                                scope.launch {
                                    try {
                                        val session = withContext(Dispatchers.IO) {
                                            PresentationAccess.client.restoreAuthorization(candidate)
                                        }?.takeIf { it.hasCredentials() }
                                        if (session != null) {
                                            PresentationAccess.settings.setDomain(candidate)
                                            PresentationAccess.client.clearParsedPageCache()
                                            if (PresentationAccess.client.hasSiteSession(candidate)) {
                                                BookshelfRepository.scheduleSync(session)
                                                CommunitySyncManager.schedulePreSync(session)
                                            }
                                            rootNavigator?.replaceAll(MainScreen(session))
                                        } else {
                                            siteUnavailable = true
                                        }
                                    } catch (e: Exception) {
                                        AppLogger.e("SettingsPage", "Failed to switch site", e)
                                    } finally {
                                        switchingDomain = false
                                    }
                                }
                            }
                        }
                    }
                    SettingsSection(title = stringResource(R.string.settings_preferences_section)) {
                        LinkRow(Icons.Filled.Reorder, stringResource(R.string.settings_navigation_section)) { section = "NAVIGATION" }
                        LinkRow(Icons.Filled.MenuBook, stringResource(R.string.settings_reading_download_section)) { section = "READING" }
                        LinkRow(Icons.Filled.Storage, stringResource(R.string.settings_storage_section)) { section = "STORAGE" }
                    }
                    SettingsSection(title = stringResource(R.string.settings_diagnostics_section)) {
                        LinkRow(Icons.Filled.BugReport, stringResource(R.string.system_logs)) { navigator?.pushIfNotCurrent(LogsPage) }
                        LinkRow(Icons.Filled.Info, stringResource(R.string.about)) { section = "ABOUT" }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = AppShapes.standard
                    ) {
                        LinkRow(
                            Icons.AutoMirrored.Filled.Logout,
                            stringResource(R.string.settings_logout_title),
                            subtitle = null,
                            centered = true
                        ) { if (!state.logoutInProgress) showLogout = true }
                    }
                }
                if (displayedSection == "NAVIGATION") {
                    SettingsSection {
                        Text(
                            stringResource(R.string.settings_navigation_description),
                            style = AppTypography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(AppSpacing.md)
                        )
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppShapes.standard,
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                editableNavigationOrder.forEach { item ->
                                    key(item) {
                                    val dragging = draggingNavigationItem == item
                                    val selected = item == if (startTab == com.breakyuna.esjzone.data.settings.SettingsDefaults.START_TAB_FOLLOW_NAV) {
                                        editableNavigationOrder.firstOrNull()
                                    } else startTab
                                    var dragOffsetX by remember(item) { mutableFloatStateOf(0f) }
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .zIndex(if (dragging) 1f else 0f)
                                            .graphicsLayer {
                                                translationX = dragOffsetX
                                                scaleX = if (dragging) 1.05f else 1f
                                                scaleY = if (dragging) 1.05f else 1f
                                            }
                                            .selectable(
                                                selected = selected,
                                                role = Role.RadioButton,
                                                onClick = { PresentationAccess.settings.setStartTab(item) }
                                            )
                                            .pointerInput(item) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = {
                                                        draggingNavigationItem = item
                                                        dragOffsetX = 0f
                                                    },
                                                    onDragCancel = {
                                                        dragOffsetX = 0f
                                                        draggingNavigationItem = null
                                                    },
                                                    onDragEnd = {
                                                        dragOffsetX = 0f
                                                        draggingNavigationItem = null
                                                        PresentationAccess.settings.setNavigationOrder(
                                                            editableNavigationOrder
                                                        )
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragOffsetX += dragAmount.x
                                                        val threshold = size.width * 0.55f
                                                        val currentIndex = editableNavigationOrder.indexOf(item)
                                                        val targetIndex = when {
                                                            dragOffsetX > threshold -> currentIndex + 1
                                                            dragOffsetX < -threshold -> currentIndex - 1
                                                            else -> currentIndex
                                                        }
                                                        if (targetIndex in editableNavigationOrder.indices && targetIndex != currentIndex) {
                                                            val reordered = editableNavigationOrder.toMutableList()
                                                            java.util.Collections.swap(reordered, currentIndex, targetIndex)
                                                            editableNavigationOrder = reordered
                                                            val indexDelta = targetIndex - currentIndex
                                                            dragOffsetX -= indexDelta * size.width.toFloat()
                                                        }
                                                    }
                                                )
                                            },
                                        shape = AppShapes.compact,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else Color.Transparent,
                                        shadowElevation = if (dragging) 6.dp else 0.dp
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = AppSpacing.xxs, vertical = AppSpacing.md),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Icon(
                                                navigationItemIcon(item),
                                                contentDescription = null,
                                                modifier = Modifier.size(22.dp),
                                                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                navigationItemLabel(item),
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    PresentationAccess.settings.setNavigationOrder(
                                        com.breakyuna.esjzone.data.settings.SettingsDefaults.NAVIGATION_ORDER
                                    )
                                    editableNavigationOrder =
                                        com.breakyuna.esjzone.data.settings.SettingsDefaults.NAVIGATION_ORDER
                                    draggingNavigationItem = null
                                },
                                enabled = editableNavigationOrder != com.breakyuna.esjzone.data.settings.SettingsDefaults.NAVIGATION_ORDER
                            ) {
                                Icon(Icons.Filled.RestartAlt, contentDescription = null)
                                Text(stringResource(R.string.settings_navigation_reset), modifier = Modifier.padding(start = 8.dp))
                            }
                        }


                    }

                    }
                    if (displayedSection == "READING") {
                        ReaderMoreSettingsContent(readerSettings, PresentationAccess.readerSettings::saveDebounced)
                        SettingsSection(title = stringResource(R.string.settings_download_section)) {
                            ToggleRow(
                                stringResource(R.string.download_auto_save),
                                autoSave
                            ) { enabled ->
                                PresentationAccess.settings.setReaderAutoSave(enabled)
                                model.persist(PresentationAccess.settings.READER_AUTO_SAVE_KEY, enabled.toString())
                            }
                            var showConcurrencyMenu by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    stringResource(R.string.settings_download_concurrency_label),
                                    style = AppTypography.bodyMedium,
                                    modifier = Modifier.weight(1f).padding(end = AppSpacing.md)
                                )
                                Box {
                                    Surface(
                                        onClick = { showConcurrencyMenu = true },
                                        shape = AppShapes.compact,
                                        color = MaterialTheme.colorScheme.surfaceContainerLow
                                    ) {
                                        Text(
                                            downloadConcurrency.toString(),
                                            style = AppTypography.labelLarge,
                                            modifier = Modifier.heightIn(min = 48.dp).padding(
                                                horizontal = AppSpacing.lg,
                                                vertical = AppSpacing.md
                                            )
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showConcurrencyMenu,
                                        onDismissRequest = { showConcurrencyMenu = false }
                                    ) {
                                        listOf(1, 3, 5, 8).forEach { candidate ->
                                            DropdownMenuItem(
                                                text = { Text(candidate.toString()) },
                                                onClick = {
                                                    PresentationAccess.settings.setDownloadConcurrency(candidate)
                                                    model.persist(PresentationAccess.settings.DOWNLOAD_CONCURRENCY_KEY, candidate.toString())
                                                    showConcurrencyMenu = false
                                                },
                                                trailingIcon = if (candidate == downloadConcurrency) {
                                                    { Icon(Icons.Filled.Check, contentDescription = null) }
                                                } else null
                                            )
                                        }
                                    }
                                }
                            }
                        }

                    }
                    if (displayedSection == "STORAGE") {
                        SettingsSection {
                            LinkRow(Icons.Filled.Download, stringResource(R.string.local_backup)) {
                                navigator?.pushIfNotCurrent(LocalBackupPage)
                            }
                        }
                        SettingsSection {
                            val cache = state.cacheStats
                            CacheRow(stringResource(R.string.settings_page_cache), when { state.cacheStatsError -> stringResource(R.string.local_cache_stats_failed); cache == null -> stringResource(R.string.local_cache_loading); else -> stringResource(R.string.local_cache_pages, formatBytes(cache.pageBytes), cache.pageEntries) }, state.cacheOperation != null, stringResource(R.string.local_cache_clear_pages)) { model.clearPageCache() }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                            CacheRow(stringResource(R.string.settings_image_cache), when { state.cacheStatsError -> stringResource(R.string.local_cache_stats_failed); cache == null -> stringResource(R.string.local_cache_loading); else -> formatBytes(cache.imageBytes) }, state.cacheOperation != null, stringResource(R.string.local_cache_clear_images)) { model.clearImageCache() }
                            Text(stringResource(R.string.settings_cache_note), style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(AppSpacing.md))
                            if (state.cacheOperation != null) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            if (state.cacheStatsError) TextButton(onClick = model::refreshCacheStats) { Text(stringResource(R.string.retry)) }
                            if (state.cacheClearError) Text(stringResource(R.string.local_cache_clear_failed), color = MaterialTheme.colorScheme.error)
                        }

                    }
                    if (displayedSection == "ABOUT") {
                        Column(Modifier.fillMaxWidth().padding(AppSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                            Text(stringResource(R.string.app_name), style = AppTypography.titleLarge)
                            Text(stringResource(R.string.about_disclaimer), style = AppTypography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        SettingsSection {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { ReleaseUpdateChecker.checkNow(context) }
                                    .padding(AppSpacing.md),
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                                    Text(stringResource(R.string.build_version), style = AppTypography.bodyMedium)
                                    Text(
                                        text = when (val s = checkState) {
                                            ReleaseCheckState.Checking -> stringResource(R.string.update_checking)
                                            ReleaseCheckState.UpToDate -> stringResource(R.string.update_up_to_date)
                                            ReleaseCheckState.Error -> stringResource(R.string.update_check_error)
                                            is ReleaseCheckState.Available -> stringResource(R.string.update_available_status, s.version)
                                            ReleaseCheckState.Idle -> stringResource(R.string.update_check_tap_version)
                                        },
                                        style = AppTypography.bodySmall,
                                        color = if (checkState is ReleaseCheckState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(BuildConfig.VERSION_NAME, style = AppTypography.labelMedium,
                                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow, AppShapes.compact)
                                        .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            ToggleRow(
                                title = stringResource(R.string.update_auto_check),
                                checked = autoCheck,
                                onCheckedChange = { ReleaseUpdateChecker.setAutoCheck(context, it) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                            LinkRow(Icons.Filled.Info, stringResource(R.string.open_source_licenses)) {
                                navigator?.pushIfNotCurrent(OpenSourceLicensesPage)
                            }
                        }
                        SettingsSection {
                            Column(Modifier.padding(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                                Text(stringResource(R.string.original_author), style = AppTypography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(Constants.ORIGINAL_AUTHOR, style = AppTypography.bodyMedium)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                            Column(Modifier.padding(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                                Text(stringResource(R.string.maintainers), style = AppTypography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(Constants.MAINTAINERS.joinToString(", "), style = AppTypography.bodyMedium)
                            }
                        }
                    }

            }
            }
            }
        }

        if (state.logoutFailed) AlertDialog(
            onDismissRequest = { model.clearLogoutFailure() },
            title = { Text(stringResource(R.string.settings_logout_failed_title)) },
            text = { Text(stringResource(R.string.settings_logout_failed_message)) },
            confirmButton = {
                TextButton(onClick = { model.clearLogoutFailure() }) {
                    Text(stringResource(R.string.logout_confirm))
                }
            }
        )

        if (siteUnavailable) AlertDialog(
            onDismissRequest = { siteUnavailable = false },
            title = { Text(stringResource(R.string.settings_mirror_unavailable_title)) },
            text = { Text(stringResource(R.string.settings_mirror_unavailable_message)) },
            confirmButton = {
                TextButton(onClick = { siteUnavailable = false }) {
                    Text(stringResource(R.string.logout_cancel))
                }
            }
        )

        if (showLogout) AlertDialog(
            onDismissRequest = { if (!state.logoutInProgress) showLogout = false },
            title = { Text(stringResource(R.string.logout_confirm_message)) },
            text = { Text(stringResource(R.string.logout_consequence)) },
            confirmButton = { TextButton(onClick = { model.logout(authorization); showLogout = false }, enabled = !state.logoutInProgress) { if (state.logoutInProgress) CircularProgressIndicator(Modifier.size(18.dp)) else Text(stringResource(R.string.logout_confirm)) } },
            dismissButton = { TextButton(onClick = { showLogout = false }, enabled = !state.logoutInProgress) { Text(stringResource(R.string.logout_cancel)) } }
        )
    }
}

@Composable
private fun navigationItemLabel(id: String): String = when (id) {
    "HOME" -> stringResource(R.string.navigation_home)
    "HISTORY" -> stringResource(R.string.history)
    "BOOKSHELF" -> stringResource(R.string.bookshelf)
    "PROFILE" -> stringResource(R.string.navigation_profile)
    else -> id
}

private fun navigationItemIcon(id: String): ImageVector = when (id) {
    "HOME" -> Icons.Filled.Home
    "HISTORY" -> Icons.Filled.History
    "BOOKSHELF" -> Icons.Filled.AutoStories
    "PROFILE" -> Icons.Filled.Person
    else -> Icons.Filled.Reorder
}

@Composable
internal fun SettingsSection(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        if (title != null) {
            Text(title, style = AppTypography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = AppSpacing.md).semantics { heading() })
        }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = AppShapes.standard
        ) {
            Column(
                Modifier.fillMaxWidth().padding(AppSpacing.xs),
                content = content
            )
        }
    }
}

@Composable
private fun InlineSettingRow(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        Text(title, style = AppTypography.bodyMedium)
        Surface(
            shape = AppShapes.compact,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Row(Modifier.fillMaxWidth().selectableGroup().padding(AppSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                verticalAlignment = Alignment.CenterVertically) {
                options.forEachIndexed { index, option ->
                    Box(
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            .clip(AppShapes.compact)
                            .background(if (index == selectedIndex) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            .selectable(
                                selected = index == selectedIndex,
                                role = Role.RadioButton,
                                onClick = { if (index != selectedIndex) onSelect(index) }
                            ).padding(horizontal = AppSpacing.xs, vertical = AppSpacing.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(option, style = AppTypography.labelMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = if (index == selectedIndex) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SiteSettingRow(
    domains: List<String>,
    selectedDomain: String,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().selectableGroup().padding(AppSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        domains.forEach { candidate ->
            val selected = candidate == selectedDomain
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clip(AppShapes.compact)
                    .background(if (selected) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent)
                    .selectable(
                        selected = selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(candidate) }
                    ).padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
            ) {
                Text(candidate, modifier = Modifier.weight(1f), style = AppTypography.bodyMedium,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                if (selected) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = AppTypography.bodyMedium)
        Switch(checked, onCheckedChange)
    }
}

@Composable
private fun CacheRow(title: String, value: String, busy: Boolean, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(AppSpacing.md), horizontalArrangement = Arrangement.spacedBy(AppSpacing.md), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Text(title, style = AppTypography.bodyMedium)
            Text(value, style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(
            onClick = onClick,
            enabled = !busy,
            shape = AppShapes.compact,
            contentPadding = PaddingValues(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Text(action, style = AppTypography.labelMedium)
        }
    }
}

@Composable
private fun LinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    centered: Boolean = false,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, enabled = enabled, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            if (centered) {
                Text(
                    title,
                    style = AppTypography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = AppSpacing.sm)
                )
            } else {
                Column(Modifier.weight(1f).padding(horizontal = AppSpacing.md)) {
                    Text(title, style = AppTypography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    subtitle?.let {
                        Text(it, style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (enabled) Icon(Icons.Filled.ChevronRight, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
