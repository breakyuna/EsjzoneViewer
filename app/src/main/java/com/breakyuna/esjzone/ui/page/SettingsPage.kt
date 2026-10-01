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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.NoAdultContent
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.breakyuna.esjzone.ui.designsystem.AccountIconBadge
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
        var showSiteDialog by remember { mutableStateOf(false) }
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
                    title = { Text(pageTitle, style = com.breakyuna.esjzone.ui.designsystem.AppTypography.titleLarge) },
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
                    .padding(horizontal = com.breakyuna.esjzone.ui.designsystem.AppSpacing.lg, vertical = com.breakyuna.esjzone.ui.designsystem.AppSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(com.breakyuna.esjzone.ui.designsystem.AppSpacing.md)
            ) {
                if (displayedSection == "MAIN") {
                    SettingsSection(Icons.Filled.Language, stringResource(R.string.settings_general_section)) {
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
                        HorizontalDivider()
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
                        HorizontalDivider()
                        ToggleRow(stringResource(R.string.settings_showadultcontent), stringResource(R.string.settings_adult_description), adult) { PresentationAccess.settings.setAdult(it); model.persist("show_adult", it.toString()) }
                        ToggleRow(
                            stringResource(R.string.settings_hide_home_recommendations),
                            stringResource(R.string.settings_hide_home_recommendations_description),
                            hideHomeRecommendations
                        ) { PresentationAccess.settings.setHideHomeRecommendations(it) }
                    }
                    SettingsSection(Icons.Filled.Dns, stringResource(R.string.settings_network_section)) {
                        SelectionSettingRow(domain) {
                            if (!switchingDomain) showSiteDialog = true
                        }
                    }
                    SettingsSection(Icons.Filled.Reorder, stringResource(R.string.settings_preferences_section)) {
                        LinkRow(Icons.Filled.Reorder, stringResource(R.string.settings_navigation_section)) { section = "NAVIGATION" }
                        LinkRow(Icons.Filled.MenuBook, stringResource(R.string.settings_reading_download_section)) { section = "READING" }
                        LinkRow(Icons.Filled.Storage, stringResource(R.string.settings_storage_section)) { section = "STORAGE" }
                        LinkRow(Icons.Filled.BugReport, stringResource(R.string.system_logs)) { navigator?.pushIfNotCurrent(LogsPage) }
                        LinkRow(Icons.Filled.Info, stringResource(R.string.about)) { section = "ABOUT" }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = com.breakyuna.esjzone.ui.designsystem.AppShapes.standard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shadowElevation = 1.dp
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
                    SettingsSection(Icons.Filled.Reorder, stringResource(R.string.settings_navigation_section)) {
                        Text(
                            stringResource(R.string.settings_navigation_description),
                            style = com.breakyuna.esjzone.ui.designsystem.AppTypography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                editableNavigationOrder.forEach { item ->
                                    key(item) {
                                    val selected = draggingNavigationItem == item
                                    var dragOffsetX by remember(item) { mutableFloatStateOf(0f) }
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .zIndex(if (selected) 1f else 0f)
                                            .graphicsLayer {
                                                translationX = dragOffsetX
                                                scaleX = if (selected) 1.05f else 1f
                                                scaleY = if (selected) 1.05f else 1f
                                            }
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
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50),
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else Color.Transparent,
                                        shadowElevation = if (selected) 6.dp else 0.dp
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 2.dp, vertical = 6.dp),
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

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        Text(
                            stringResource(R.string.settings_startup_page_title),
                            style = com.breakyuna.esjzone.ui.designsystem.AppTypography.labelLarge
                        )
                        Text(
                            stringResource(R.string.settings_startup_page_description),
                            style = com.breakyuna.esjzone.ui.designsystem.AppTypography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Column(Modifier.selectableGroup()) {
                            listOf(
                                com.breakyuna.esjzone.data.settings.SettingsDefaults.START_TAB_FOLLOW_NAV,
                                "HOME", "BOOKSHELF", "HISTORY", "PROFILE"
                            ).forEach { candidate ->
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .selectable(
                                            selected = candidate == startTab,
                                            role = Role.RadioButton,
                                            onClick = { PresentationAccess.settings.setStartTab(candidate) }
                                        ).padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = candidate == startTab, onClick = null)
                                    Text(startupPageTitle(candidate))
                                }
                            }
                        }
                    }

                    }
                    if (displayedSection == "READING") {
                    SettingsSection(Icons.Filled.MenuBook, stringResource(R.string.settings_reader_section)) {
                        ReaderMoreSettingsContent(readerSettings, PresentationAccess.readerSettings::saveDebounced)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                        ToggleRow(
                            stringResource(R.string.download_auto_save),
                            stringResource(R.string.download_auto_save_description),
                            autoSave
                        ) { enabled ->
                            PresentationAccess.settings.setReaderAutoSave(enabled)
                            model.persist(PresentationAccess.settings.READER_AUTO_SAVE_KEY, enabled.toString())
                        }
                    }

                    SettingsSection(Icons.Filled.Download, stringResource(R.string.settings_download_section)) {
                        Text(
                            stringResource(R.string.settings_download_concurrency_description),
                            style = com.breakyuna.esjzone.ui.designsystem.AppTypography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        var showConcurrencyMenu by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                stringResource(R.string.settings_download_concurrency_label),
                                style = com.breakyuna.esjzone.ui.designsystem.AppTypography.labelLarge
                            )
                            Box {
                                Surface(
                                    onClick = { showConcurrencyMenu = true },
                                    shape = com.breakyuna.esjzone.ui.designsystem.AppShapes.compact,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant
                                    )
                                ) {
                                    Text(
                                        downloadConcurrency.toString(),
                                        style = com.breakyuna.esjzone.ui.designsystem.AppTypography.titleMedium,
                                        modifier = Modifier.padding(
                                            horizontal = 14.dp,
                                            vertical = 2.dp
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
                    SettingsSection(Icons.Filled.Storage, stringResource(R.string.settings_storage_section)) {
                        LinkRow(Icons.Filled.Download, stringResource(R.string.local_backup), stringResource(R.string.backup_short_description)) {
                            navigator?.pushIfNotCurrent(LocalBackupPage)
                        }
                        HorizontalDivider()
                        val cache = state.cacheStats
                        CacheRow(stringResource(R.string.settings_page_cache), when { state.cacheStatsError -> stringResource(R.string.local_cache_stats_failed); cache == null -> stringResource(R.string.local_cache_loading); else -> stringResource(R.string.local_cache_pages, formatBytes(cache.pageBytes), cache.pageEntries) }, state.cacheOperation != null, stringResource(R.string.local_cache_clear_pages)) { model.clearPageCache() }
                        HorizontalDivider()
                        CacheRow(stringResource(R.string.settings_image_cache), when { state.cacheStatsError -> stringResource(R.string.local_cache_stats_failed); cache == null -> stringResource(R.string.local_cache_loading); else -> formatBytes(cache.imageBytes) }, state.cacheOperation != null, stringResource(R.string.local_cache_clear_images)) { model.clearImageCache() }
                        Text(stringResource(R.string.settings_cache_note), style = com.breakyuna.esjzone.ui.designsystem.AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                        if (state.cacheOperation != null) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        if (state.cacheStatsError) TextButton(onClick = model::refreshCacheStats) { Text(stringResource(R.string.retry)) }
                        if (state.cacheClearError) Text(stringResource(R.string.local_cache_clear_failed), color = MaterialTheme.colorScheme.error)
                    }

                    }
                    if (displayedSection == "ABOUT") {
                    SettingsSection(Icons.Filled.Info, stringResource(R.string.about)) {
                        Text(
                            text = stringResource(R.string.about_disclaimer),
                            style = com.breakyuna.esjzone.ui.designsystem.AppTypography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { ReleaseUpdateChecker.checkNow(context) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.build_version), style = com.breakyuna.esjzone.ui.designsystem.AppTypography.labelLarge)
                                Text(
                                    text = when (val s = checkState) {
                                        ReleaseCheckState.Checking -> stringResource(R.string.update_checking)
                                        ReleaseCheckState.UpToDate -> stringResource(R.string.update_up_to_date)
                                        ReleaseCheckState.Error -> stringResource(R.string.update_check_error)
                                        is ReleaseCheckState.Available -> stringResource(R.string.update_available_status, s.version)
                                        ReleaseCheckState.Idle -> stringResource(R.string.update_check_tap_version)
                                    },
                                    style = com.breakyuna.esjzone.ui.designsystem.AppTypography.bodySmall,
                                    color = if (checkState is ReleaseCheckState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = BuildConfig.VERSION_NAME,
                                style = com.breakyuna.esjzone.ui.designsystem.AppTypography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        LinkRow(Icons.Filled.Info, stringResource(R.string.open_source_licenses)) {
                            navigator?.pushIfNotCurrent(OpenSourceLicensesPage)
                        }
                        ToggleRow(
                            title = stringResource(R.string.update_auto_check),
                            subtitle = stringResource(R.string.update_auto_check_description),
                            checked = autoCheck,
                            onCheckedChange = { ReleaseUpdateChecker.setAutoCheck(context, it) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.original_author), style = com.breakyuna.esjzone.ui.designsystem.AppTypography.labelLarge)
                            Text(
                                text = Constants.ORIGINAL_AUTHOR,
                                style = com.breakyuna.esjzone.ui.designsystem.AppTypography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.maintainers), style = com.breakyuna.esjzone.ui.designsystem.AppTypography.labelLarge)
                            Text(
                                text = Constants.MAINTAINERS.joinToString(", "),
                                style = com.breakyuna.esjzone.ui.designsystem.AppTypography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

            }
            }
            }
        }
        if (showSiteDialog) AlertDialog(
            onDismissRequest = { showSiteDialog = false },
            title = { Text(stringResource(R.string.settings_network_section)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.settings_active_mirror), style = com.breakyuna.esjzone.ui.designsystem.AppTypography.labelLarge)
                    PresentationAccess.settings.DOMAINS.forEach { candidate ->
                        ChoiceRow(
                            title = candidate,
                            selected = candidate == domain,
                            onClick = {
                                if (candidate != domain && !switchingDomain) {
                                    showSiteDialog = false
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
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSiteDialog = false }) { Text(stringResource(R.string.logout_cancel)) }
            }
        )

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
private fun startupPageTitle(id: String): String = when (id) {
    com.breakyuna.esjzone.data.settings.SettingsDefaults.START_TAB_FOLLOW_NAV ->
        stringResource(R.string.settings_startup_follow_nav)
    "HOME" -> stringResource(R.string.navigation_home)
    "BOOKSHELF" -> stringResource(R.string.bookshelf)
    "HISTORY" -> stringResource(R.string.history)
    "PROFILE" -> stringResource(R.string.navigation_profile)
    else -> id
}

@Composable
private fun SettingsSection(icon: ImageVector, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(com.breakyuna.esjzone.ui.designsystem.AppSpacing.sm)) {
            AccountIconBadge(icon)
            Text(title, style = com.breakyuna.esjzone.ui.designsystem.AppTypography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = com.breakyuna.esjzone.ui.designsystem.AppShapes.standard,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant
            ),
            shadowElevation = 1.dp
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                content = content
            )
        }
    }
}

@Composable
private fun ChoiceRow(title: String, subtitle: String? = null, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, shape = com.breakyuna.esjzone.ui.designsystem.AppShapes.compact, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, style = com.breakyuna.esjzone.ui.designsystem.AppTypography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis); subtitle?.let { Text(it, style = com.breakyuna.esjzone.ui.designsystem.AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            if (selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
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
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(.35f),
            style = com.breakyuna.esjzone.ui.designsystem.AppTypography.labelLarge)
        Surface(
            modifier = Modifier.weight(.65f),
            shape = com.breakyuna.esjzone.ui.designsystem.AppShapes.compact,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(Modifier.selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
                options.forEachIndexed { index, option ->
                    if (index > 0) {
                        Box(Modifier.width(1.dp).height(20.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant))
                    }
                    Box(
                        modifier = Modifier.weight(1f).height(48.dp)
                            .background(if (index == selectedIndex) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .selectable(
                                selected = index == selectedIndex,
                                role = Role.RadioButton,
                                onClick = { if (index != selectedIndex) onSelect(index) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(option, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                            color = if (index == selectedIndex) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionSettingRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = com.breakyuna.esjzone.ui.designsystem.AppShapes.compact,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = com.breakyuna.esjzone.ui.designsystem.AppTypography.labelLarge)
                subtitle?.let {
                    Text(it, style = com.breakyuna.esjzone.ui.designsystem.AppTypography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(com.breakyuna.esjzone.ui.designsystem.AppSpacing.md), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, style = com.breakyuna.esjzone.ui.designsystem.AppTypography.labelLarge); Text(subtitle, style = com.breakyuna.esjzone.ui.designsystem.AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked, onCheckedChange) }
}

@Composable
private fun CacheRow(title: String, value: String, busy: Boolean, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(com.breakyuna.esjzone.ui.designsystem.AppSpacing.md), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = com.breakyuna.esjzone.ui.designsystem.AppTypography.labelLarge)
            Text(value, style = com.breakyuna.esjzone.ui.designsystem.AppTypography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Button(
            onClick = onClick,
            enabled = !busy,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Text(action, style = com.breakyuna.esjzone.ui.designsystem.AppTypography.labelMedium)
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
                .padding(
                    horizontal = if (centered) 12.dp else 8.dp,
                    vertical = if (centered) 8.dp else 6.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(if (centered) 20.dp else 24.dp))
            if (centered) {
                Text(
                    title,
                    style = com.breakyuna.esjzone.ui.designsystem.AppTypography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = com.breakyuna.esjzone.ui.designsystem.AppSpacing.sm)
                )
            } else {
                Column(Modifier.weight(1f).padding(horizontal = com.breakyuna.esjzone.ui.designsystem.AppSpacing.md)) {
                    Text(title, style = com.breakyuna.esjzone.ui.designsystem.AppTypography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    subtitle?.let {
                        Text(it, style = com.breakyuna.esjzone.ui.designsystem.AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (enabled) Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
