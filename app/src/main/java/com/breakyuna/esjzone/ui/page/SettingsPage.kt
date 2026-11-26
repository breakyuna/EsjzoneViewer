package com.breakyuna.esjzone.ui.page

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import com.breakyuna.esjzone.ui.designsystem.AppLayout
import com.breakyuna.esjzone.ui.designsystem.AppMotion
import com.breakyuna.esjzone.ui.designsystem.LocalReduceMotion
import com.breakyuna.esjzone.ui.designsystem.appUsesWideLayout
import com.breakyuna.esjzone.ui.navigation.AppNavigationIcon
import com.breakyuna.esjzone.ui.navigation.AppTabId
import com.breakyuna.esjzone.ui.navigation.NavigationSelectionAnimationSpec
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.breakyuna.esjzone.AppThemeMode
import com.breakyuna.esjzone.AppPalette
import com.breakyuna.esjzone.ui.designsystem.paletteSwatch
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
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
import com.breakyuna.esjzone.data.settings.SettingsDefaults
import com.breakyuna.esjzone.ui.navigation.AppNavigator
import com.breakyuna.esjzone.ui.navigation.AppDestination
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
    override fun Content(navigator: AppNavigator) {
        val rootNavigator = navigator.root
        val authorization = LocalAuthorization.current
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current
        val scope = rememberCoroutineScope()
        val model = rememberAppViewModel { SettingsPageModel() }
        val state by model.state.collectAsStateWithLifecycle()
        val scanModel: StorageLocationModel = androidx.lifecycle.viewmodel.compose.viewModel(key = "download-scan") {
            StorageLocationModel()
        }
        val scanState by scanModel.state.collectAsStateWithLifecycle()
        val snackbarHost = remember { SnackbarHostState() }
        val scanResult = scanState.result?.let { stringResource(it) }
        LaunchedEffect(scanState.result) {
            if (scanState.result == R.string.storage_reindex_done) model.refreshCacheStats()
            if (scanResult != null) {
                snackbarHost.showSnackbar(scanResult)
                scanModel.dismissResult()
            }
        }
        val autoBackup by PresentationAccess.autoBackup.enabled.collectAsStateWithLifecycle()
        val adult by PresentationAccess.settings.adult
        val hideHomeRecommendations by PresentationAccess.settings.hideHomeRecommendations
        val domain by PresentationAccess.settings.domain
        val language by PresentationAccess.settings.language
        val themeMode by PresentationAccess.settings.themeMode
        val palette by PresentationAccess.settings.palette
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
        val pageTitle = stringResource(when (section) {
            "NAVIGATION" -> R.string.settings_navigation_section
            "READING" -> R.string.settings_reading_section
            "STORAGE" -> R.string.settings_storage_analysis
            "DOWNLOAD" -> R.string.download_settings
            "ABOUT" -> R.string.about
            else -> R.string.settings_screen_title
        })
        var switchingDomain by remember { mutableStateOf(false) }
        var siteUnavailable by remember { mutableStateOf(false) }
        var draggingNavigationItem by remember { mutableStateOf<String?>(null) }
        val editableNavigationOrder = remember { mutableStateOf(navigationOrder) }
        LaunchedEffect(navigationOrder, draggingNavigationItem) {
            if (draggingNavigationItem == null) editableNavigationOrder.value = navigationOrder
        }
        LaunchedEffect(Unit) {
            ReleaseUpdateChecker.initialize(context)
        }
        LifecycleResumeEffect(section) {
            if (section == "STORAGE" || section == "DOWNLOAD") model.refreshCacheStats()
            onPauseOrDispose { }
        }
        LaunchedEffect(state.logoutCompleted) { if (state.logoutCompleted) rootNavigator.replaceAll(LoginScreen) }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHost) },
            topBar = {
                TopAppBar(
                    title = { Text(pageTitle, style = AppTypography.titleMedium) },
                    navigationIcon = { BackIconButton { if (section == "MAIN") navigator.pop() else section = "MAIN" } },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { padding ->
            SettingsPaneLayout(section, { section = it }, Modifier.fillMaxSize().padding(padding)) {
            SettingsSectionNavigation(
                section = section,
                onBack = { section = "MAIN" },
                modifier = Modifier.fillMaxSize()
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
                    SettingsSection(title = stringResource(R.string.settings_display_group)) {
                        ChoiceSettingRow(
                            icon = Icons.Filled.Palette,
                            title = stringResource(R.string.settings_theme),
                            options = listOf(
                                stringResource(R.string.settings_language_system_short),
                                stringResource(R.string.settings_theme_light),
                                stringResource(R.string.reader_background_dark)
                            ),
                            selectedIndex = themeMode.ordinal
                        ) { PresentationAccess.settings.setThemeMode(AppThemeMode.entries[it]) }
                        PaletteSettingRow(selected = palette) { PresentationAccess.settings.setPalette(it) }
                        ChoiceSettingRow(
                            icon = Icons.Filled.Language,
                            title = stringResource(R.string.settings_language_label),
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
                        LinkRow(Icons.Filled.Reorder, stringResource(R.string.settings_navigation_label)) { section = "NAVIGATION" }
                        SettingsToggleRow(
                            title = stringResource(R.string.settings_script_toggle),
                            checked = readerSettings.script != ReaderScript.ORIGINAL,
                            icon = Icons.Filled.Translate
                        ) { enabled ->
                            PresentationAccess.readerSettings.saveScriptInBackground(
                                if (enabled) ReaderScript.SIMPLIFIED else ReaderScript.ORIGINAL
                            )
                        }
                        if (readerSettings.script != ReaderScript.ORIGINAL) {
                            InlineSettingRow(
                                options = listOf(
                                    stringResource(R.string.reader_script_simplified),
                                    stringResource(R.string.reader_script_traditional)
                                ),
                                selectedIndex = if (readerSettings.script == ReaderScript.SIMPLIFIED) 0 else 1
                            ) { index ->
                                PresentationAccess.readerSettings.saveScriptInBackground(
                                    if (index == 0) ReaderScript.SIMPLIFIED else ReaderScript.TRADITIONAL
                                )
                            }
                        }
                    }
                    SettingsSection(title = stringResource(R.string.settings_reading_group)) {
                        SettingsToggleRow(
                            title = stringResource(R.string.settings_showadultcontent),
                            checked = adult,
                            icon = Icons.Filled.Visibility
                        ) { PresentationAccess.settings.setAdult(it); model.persist("show_adult", it.toString()) }
                        SettingsToggleRow(
                            title = stringResource(R.string.settings_hide_home_recommendations),
                            checked = hideHomeRecommendations,
                            helpText = stringResource(R.string.settings_hide_home_recommendations_reason),
                            icon = Icons.Filled.VisibilityOff
                        ) { PresentationAccess.settings.setHideHomeRecommendations(it) }
                        ChoiceSettingRow(
                            icon = Icons.Filled.Public,
                            title = stringResource(R.string.settings_site_switch),
                            options = PresentationAccess.settings.DOMAINS,
                            selectedIndex = PresentationAccess.settings.DOMAINS.indexOf(domain),
                            enabled = !switchingDomain
                        ) { index ->
                            val candidate = PresentationAccess.settings.DOMAINS[index]
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
                                            }
                                            rootNavigator.replaceAll(MainScreen(session))
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
                        LinkRow(Icons.Filled.MenuBook, stringResource(R.string.settings_reading_section)) { section = "READING" }
                    }
                    SettingsSection(title = stringResource(R.string.settings_data_group)) {
                        StorageLocationSettingRow()
                        SettingsToggleRow(
                            title = stringResource(R.string.auto_backup),
                            checked = autoBackup,
                            icon = Icons.Filled.Backup
                        ) { model.setAutoBackup(it, BookshelfRepository.scopeFor(authorization)) }
                        if (state.autoBackupFailed) Text(stringResource(R.string.backup_failed),
                            color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(AppSpacing.md))
                        LinkRow(Icons.Filled.Backup, stringResource(R.string.settings_backup)) {
                            navigator.pushIfNotCurrent(LocalBackupPage)
                        }
                        LinkRow(Icons.Filled.Download, stringResource(R.string.download_settings)) { section = "DOWNLOAD" }
                        LinkRow(Icons.Filled.Storage, stringResource(R.string.settings_storage_analysis)) { section = "STORAGE" }
                    }
                    SettingsSection(title = stringResource(R.string.settings_system_group)) {
                        LinkRow(Icons.Filled.BugReport, stringResource(R.string.system_logs)) { navigator.pushIfNotCurrent(LogsPage) }
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
                            style = AppTypography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(AppSpacing.md)
                        )
                        NavigationBarPreview(
                            orderState = editableNavigationOrder,
                            startTab = startTab,
                            draggingItem = draggingNavigationItem,
                            onDraggingItemChange = { draggingNavigationItem = it },
                            onCommitOrder = { PresentationAccess.settings.setNavigationOrder(it) },
                            onCancelOrder = { editableNavigationOrder.value = navigationOrder },
                            onSelectStartTab = { PresentationAccess.settings.setStartTab(it) }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    PresentationAccess.settings.setNavigationOrder(
                                        SettingsDefaults.NAVIGATION_ORDER
                                    )
                                    PresentationAccess.settings.setStartTab(SettingsDefaults.DEFAULT_START_TAB)
                                    editableNavigationOrder.value = SettingsDefaults.NAVIGATION_ORDER
                                    draggingNavigationItem = null
                                },
                                enabled = editableNavigationOrder.value != SettingsDefaults.NAVIGATION_ORDER ||
                                    startTab != SettingsDefaults.DEFAULT_START_TAB
                            ) {
                                Icon(Icons.Filled.RestartAlt, contentDescription = null)
                                Text(stringResource(R.string.settings_navigation_reset), modifier = Modifier.padding(start = 8.dp))
                            }
                        }


                    }

                    }
                    if (displayedSection == "READING") {
                        ReaderMoreSettingsContent(readerSettings, PresentationAccess.readerSettings::saveDebounced) {
                            navigator.pushIfNotCurrent(ReaderBackgroundPage)
                        }
                    }
                    if (displayedSection == "DOWNLOAD") {
                        SettingsSection {
                            val downloads = state.downloadStats
                            LinkRow(
                                Icons.Filled.Download,
                                stringResource(R.string.download_library),
                                subtitle = when {
                                    state.downloadStatsError -> stringResource(R.string.storage_usage_failed)
                                    downloads == null -> stringResource(R.string.storage_usage_loading)
                                    else -> stringResource(R.string.download_total_summary, downloads.novelCount, formatBytes(downloads.bytes))
                                }
                            ) {
                                navigator.pushIfNotCurrent(DownloadPage)
                            }
                            if (state.downloadStatsError) {
                                TextButton(onClick = model::refreshCacheStats) { Text(stringResource(R.string.retry)) }
                            }
                            LinkRow(
                                Icons.Filled.FolderOpen,
                                stringResource(if (scanState.busy) R.string.storage_location_working else R.string.storage_reindex),
                                enabled = !scanState.busy,
                                onClick = scanModel::reindex
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                            SettingsToggleRow(
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
                                    .heightIn(min = 64.dp)
                                    .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    stringResource(R.string.settings_download_concurrency_label),
                                    style = AppTypography.bodyLarge,
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
                                            style = AppTypography.bodyLarge,
                                            modifier = Modifier.heightIn(min = 56.dp).padding(
                                                horizontal = AppSpacing.lg,
                                                vertical = AppSpacing.md
                                            )
                                        )
                                    }
                                    if (showConcurrencyMenu) {
                                        val candidates = listOf(1, 3, 5, 8)
                                        SettingsChoiceMenu(
                                            expanded = showConcurrencyMenu,
                                            options = candidates.map { it.toString() },
                                            selectedIndex = candidates.indexOf(downloadConcurrency),
                                            onDismiss = { showConcurrencyMenu = false },
                                            onSelect = { index ->
                                                val candidate = candidates[index]
                                                PresentationAccess.settings.setDownloadConcurrency(candidate)
                                                model.persist(PresentationAccess.settings.DOWNLOAD_CONCURRENCY_KEY, candidate.toString())
                                            }
                                        )
                                    }
                                }
                            }
                        }

                    }
                    if (displayedSection == "STORAGE") {
                        SettingsSection {
                            CacheStorageChart(state.storageUsage, state.storageStatsError)
                            if (state.storageStatsError) {
                                TextButton(onClick = model::refreshCacheStats) { Text(stringResource(R.string.retry)) }
                            }
                        }

                        SettingsSection {
                            val cache = state.cacheStats
                            CacheRow(stringResource(R.string.settings_page_cache), when { state.cacheStatsError -> stringResource(R.string.local_cache_stats_failed); cache == null -> stringResource(R.string.local_cache_loading); else -> stringResource(R.string.local_cache_pages, formatBytes(cache.pageBytes), cache.pageEntries) }, state.cacheOperation != null, stringResource(R.string.local_cache_clear_pages)) { model.clearPageCache() }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                            CacheRow(stringResource(R.string.settings_image_cache), when { state.cacheStatsError -> stringResource(R.string.local_cache_stats_failed); cache == null -> stringResource(R.string.local_cache_loading); else -> formatBytes(cache.imageBytes) }, state.cacheOperation != null, stringResource(R.string.local_cache_clear_images)) { model.clearImageCache() }
                            Text(stringResource(R.string.settings_cache_note), style = AppTypography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(AppSpacing.md))
                            if (state.cacheOperation != null) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            if (state.cacheStatsError) TextButton(onClick = model::refreshCacheStats) { Text(stringResource(R.string.retry)) }
                            if (state.cacheClearError) Text(stringResource(R.string.local_cache_clear_failed), color = MaterialTheme.colorScheme.error)
                        }

                    }
                    if (displayedSection == "STORAGE" && BuildConfig.DEBUG) {
                        SettingsSection {
                            LinkRow(Icons.Filled.Storage, stringResource(R.string.cache_records)) {
                                navigator.pushIfNotCurrent(CacheRecordsPage)
                            }
                        }
                    }
                    if (displayedSection == "ABOUT") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.xxl),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
                        ) {
                            Image(
                                painter = painterResource(R.drawable.esjzone_icon_round),
                                contentDescription = null,
                                modifier = Modifier.size(104.dp)
                            )
                            Text(stringResource(R.string.app_name), style = AppTypography.displayLarge)
                            Surface(
                                shape = AppShapes.pill,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    BuildConfig.VERSION_NAME,
                                    style = AppTypography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm)
                                )
                            }
                        }
                        SettingsSection {
                            LinkRow(
                                icon = Icons.Filled.SystemUpdate,
                                title = stringResource(R.string.settings_check_updates),
                                subtitle = when (val update = checkState) {
                                    ReleaseCheckState.Checking -> stringResource(R.string.update_checking)
                                    ReleaseCheckState.UpToDate -> stringResource(R.string.update_up_to_date)
                                    ReleaseCheckState.Error -> stringResource(R.string.update_check_error)
                                    is ReleaseCheckState.Available -> stringResource(R.string.update_available_status, update.version)
                                    ReleaseCheckState.Idle -> null
                                },
                                enabled = checkState != ReleaseCheckState.Checking
                            ) { ReleaseUpdateChecker.checkNow(context) }
                            SettingsToggleRow(
                                title = stringResource(R.string.update_auto_check),
                                checked = autoCheck,
                                icon = Icons.Filled.Update,
                                onCheckedChange = { ReleaseUpdateChecker.setAutoCheck(context, it) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                            LinkRow(Icons.Filled.Public, stringResource(R.string.project_repository)) {
                                runCatching { uriHandler.openUri(Constants.PROJECT_REPOSITORY_URL) }
                                    .onFailure { error ->
                                        AppLogger.w("SettingsPage", "Unable to open project repository", error)
                                    }
                            }
                            LinkRow(Icons.Filled.Description, stringResource(R.string.open_source_licenses)) {
                                navigator.pushIfNotCurrent(OpenSourceLicensesPage)
                            }
                        }
                        SettingsSection {
                            Column(Modifier.padding(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                                Text(stringResource(R.string.original_author), style = AppTypography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(Constants.ORIGINAL_AUTHOR, style = AppTypography.bodyLarge)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                            Column(Modifier.padding(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                                Text(stringResource(R.string.maintainers), style = AppTypography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(Constants.MAINTAINERS.joinToString(", "), style = AppTypography.bodyLarge)
                            }
                        }
                        Text(
                            stringResource(R.string.about_disclaimer),
                            style = AppTypography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.lg)
                        )
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
            confirmButton = { TextButton(onClick = { model.logout(authorization); showLogout = false }, enabled = !state.logoutInProgress) { if (state.logoutInProgress) CircularProgressIndicator(Modifier.size(20.dp)) else Text(stringResource(R.string.logout_confirm)) } },
            dismissButton = { TextButton(onClick = { showLogout = false }, enabled = !state.logoutInProgress) { Text(stringResource(R.string.logout_cancel)) } }
        )
    }
}

@Composable
private fun NavigationBarPreview(
    orderState: MutableState<List<String>>,
    startTab: String,
    draggingItem: String?,
    onDraggingItemChange: (String?) -> Unit,
    onCommitOrder: (List<String>) -> Unit,
    onCancelOrder: () -> Unit,
    onSelectStartTab: (String) -> Unit
) {
    // A move and release can both run before the preview recomposes.
    var order by orderState
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val reduced = LocalReduceMotion.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val latestOnDraggingItemChange by rememberUpdatedState(onDraggingItemChange)
    val latestOnCommitOrder by rememberUpdatedState(onCommitOrder)
    val latestOnCancelOrder by rememberUpdatedState(onCancelOrder)

    var dragStartIndex by remember { mutableIntStateOf(0) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var lastDraggedItem by remember { mutableStateOf<String?>(null) }
    var lastTargetSlotIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(draggingItem) {
        if (draggingItem == null) {
            dragDistance = 0f
        }
    }

    val barShape = AppShapes.standard
    val itemShape = RoundedCornerShape(12.dp)
    val placeholderShape = RoundedCornerShape(10.dp)
    val barContainerColor = if (isDark) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val barBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.5f else 0.65f)
    val elevatedCardColor = if (isDark) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val shadowBaseColor = if (isDark) Color.Black else MaterialTheme.colorScheme.onSurface
    val itemHeight = (68f + (density.fontScale - 1f).coerceAtLeast(0f) * 20f).dp
    val selectedId = if (startTab == SettingsDefaults.START_TAB_FOLLOW_NAV) {
        order.firstOrNull()
    } else {
        startTab
    }

    // Reserve breathing room inside SettingsSection so lifted card elevation shadows never clip.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .background(barContainerColor, barShape)
                .border(AppLayout.divider, barBorderColor, barShape)
                .padding(AppSpacing.xs)
                .height(itemHeight)
                .selectableGroup()
        ) {
            val itemCount = order.size.coerceAtLeast(1)
            val slotWidth = maxWidth / itemCount
            val slotWidthPx = with(density) { slotWidth.toPx() }
            val edgeOvershootPx = with(density) { 4.dp.toPx() }
            val latestSlotWidthPx by rememberUpdatedState(slotWidthPx)

            val placeholderAlpha by animateFloatAsState(
                targetValue = if (draggingItem != null) 1f else 0f,
                animationSpec = if (reduced) snap() else AppMotion.effectsDefault(),
                label = "NavPreviewSlotAlpha"
            )
            key(lastDraggedItem) {
                val placeholderOffsetX by animateFloatAsState(
                    targetValue = lastTargetSlotIndex.coerceIn(0, (itemCount - 1).coerceAtLeast(0)) * slotWidthPx,
                    animationSpec = if (reduced) snap() else AppMotion.spatialFast(),
                    label = "NavPreviewSlotOffset"
                )
                if (placeholderAlpha > 0.005f) {
                    Box(
                        modifier = Modifier
                            .width(slotWidth)
                            .fillMaxHeight()
                            .graphicsLayer {
                                translationX = placeholderOffsetX
                                alpha = placeholderAlpha
                            }
                            .padding(2.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.10f else 0.08f),
                                placeholderShape
                            )
                            .border(
                                AppLayout.divider,
                                MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.24f else 0.18f),
                                placeholderShape
                            )
                    )
                }
            }

            order.forEach { item ->
                key(item) {
                    val isDragging = draggingItem == item
                    val anotherDragging = draggingItem != null && !isDragging
                    val selected = item == selectedId
                    val currentIndex = order.indexOf(item).coerceAtLeast(0)

                    val dragProgress by animateFloatAsState(
                        targetValue = if (isDragging) 1f else 0f,
                        animationSpec = if (reduced) snap() else AppMotion.effectsDefault(),
                        label = "NavPreviewDrag_$item"
                    )
                    val itemScale by animateFloatAsState(
                        targetValue = if (isDragging) 1.04f else 1f,
                        animationSpec = if (reduced) snap() else AppMotion.spatialFast(),
                        label = "NavPreviewScale_$item"
                    )
                    val latestItemScale by rememberUpdatedState(itemScale)
                    val dimAlpha by animateFloatAsState(
                        targetValue = if (anotherDragging) 0.58f else 1f,
                        animationSpec = if (reduced) snap() else AppMotion.effectsDefault(),
                        label = "NavPreviewDim_$item"
                    )
                    val offsetX = animateFloatAsState(
                        targetValue = if (isDragging) {
                            dragStartIndex * slotWidthPx + dragDistance
                        } else {
                            currentIndex * slotWidthPx
                        },
                        animationSpec = if (isDragging || reduced) snap() else AppMotion.spatialFast(),
                        label = "NavPreviewOffset_$item"
                    )
                    val selectionProgress = animateFloatAsState(
                        targetValue = if (selected) 1f else 0f,
                        animationSpec = if (reduced) snap() else NavigationSelectionAnimationSpec,
                        label = "NavPreviewSelect_$item"
                    )

                    // Ramp card opacity to 1 before shadow appears so shadows never bleed through transparent fills.
                    val cardBgAlpha = (dragProgress / 0.35f).coerceIn(0f, 1f)
                    val shadowAlpha = ((dragProgress - 0.35f) / 0.65f).coerceIn(0f, 1f)
                    val cardBorderColor = MaterialTheme.colorScheme.primary.copy(
                        alpha = (if (isDark) 0.42f else 0.26f) * dragProgress
                    )
                    val zIndex = when {
                        isDragging -> 2f
                        lastDraggedItem == item && dragProgress > 0.01f -> 1.5f
                        lastDraggedItem == item -> 1f
                        else -> 0f
                    }

                    val indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    val pillColor = indicatorColor.copy(alpha = indicatorColor.alpha * selectionProgress.value)
                    val idleLabelColor = lerp(
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        MaterialTheme.colorScheme.primary,
                        selectionProgress.value
                    )
                    val labelColor = if (selected) {
                        idleLabelColor
                    } else {
                        lerp(idleLabelColor, MaterialTheme.colorScheme.onSurface, dragProgress)
                    }
                    val idleIconColor = lerp(
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        selectionProgress.value
                    )
                    val iconColor = if (selected) {
                        idleIconColor
                    } else {
                        lerp(idleIconColor, MaterialTheme.colorScheme.onSurface, dragProgress)
                    }
                    val pillWidth = (slotWidth - 12.dp).coerceIn(28.dp, 52.dp)
                    val tabId = navigationTabId(item)

                    // Outer layer carries lift, scale, and a soft ambient spread shadow;
                    // inner layer adds a tight contact shadow beneath an opaque elevated card.
                    Box(
                        modifier = Modifier
                            .width(slotWidth)
                            .fillMaxHeight()
                            .zIndex(zIndex)
                            .graphicsLayer {
                                translationX = offsetX.value
                                translationY = -3.dp.toPx() * dragProgress
                                scaleX = itemScale
                                scaleY = itemScale
                                shadowElevation = (if (isDark) 14.dp else 12.dp).toPx() * shadowAlpha
                                shape = itemShape
                                clip = false
                                ambientShadowColor = shadowBaseColor.copy(alpha = if (isDark) 1f else 0.55f)
                                spotShadowColor = shadowBaseColor.copy(alpha = if (isDark) 1f else 0.42f)
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    shadowElevation = (if (isDark) 4.dp else 3.dp).toPx() * shadowAlpha
                                    shape = itemShape
                                    clip = false
                                    ambientShadowColor = shadowBaseColor.copy(alpha = if (isDark) 1f else 0.45f)
                                    spotShadowColor = shadowBaseColor.copy(alpha = if (isDark) 1f else 0.55f)
                                }
                                .background(elevatedCardColor.copy(alpha = cardBgAlpha), itemShape)
                                .border(AppLayout.divider, cardBorderColor, itemShape)
                                .clip(itemShape)
                                .selectable(
                                    selected = selected,
                                    interactionSource = remember(item) { MutableInteractionSource() },
                                    indication = ripple(),
                                    role = Role.RadioButton,
                                    onClick = { onSelectStartTab(item) }
                                )
                                .pointerInput(item) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            val startIdx = order.indexOf(item).coerceAtLeast(0)
                                            dragStartIndex = startIdx
                                            dragDistance = 0f
                                            lastDraggedItem = item
                                            lastTargetSlotIndex = startIdx
                                            latestOnDraggingItemChange(item)
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragCancel = {
                                            dragDistance = 0f
                                            lastTargetSlotIndex = dragStartIndex
                                            latestOnDraggingItemChange(null)
                                            latestOnCancelOrder()
                                        },
                                        onDragEnd = {
                                            latestOnCommitOrder(order)
                                            dragDistance = 0f
                                            latestOnDraggingItemChange(null)
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val width = latestSlotWidthPx
                                            if (width <= 0f) return@detectDragGesturesAfterLongPress
                                            val currentOrder = order
                                            val maxX = width * (currentOrder.lastIndex.coerceAtLeast(0))
                                            val rawX = dragStartIndex * width + dragDistance + dragAmount.x * latestItemScale
                                            val clampedX = rawX.coerceIn(-edgeOvershootPx, maxX + edgeOvershootPx)
                                            dragDistance = clampedX - dragStartIndex * width

                                            val currentIdx = currentOrder.indexOf(item)
                                            if (currentIdx < 0) return@detectDragGesturesAfterLongPress
                                            val delta = clampedX - currentIdx * width
                                            val threshold = width * 0.52f
                                            val targetIdx = when {
                                                delta > threshold && currentIdx < currentOrder.lastIndex -> currentIdx + 1
                                                delta < -threshold && currentIdx > 0 -> currentIdx - 1
                                                else -> currentIdx
                                            }
                                            if (targetIdx != currentIdx) {
                                                val reordered = currentOrder.toMutableList().apply {
                                                    add(targetIdx, removeAt(currentIdx))
                                                }
                                                lastTargetSlotIndex = targetIdx
                                                order = reordered
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { alpha = dimAlpha }
                                    .padding(horizontal = AppSpacing.xs),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs, Alignment.CenterVertically)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = pillWidth, height = 30.dp)
                                        .clip(CircleShape)
                                        .background(pillColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (tabId != null) {
                                        AppNavigationIcon(
                                            tab = tabId,
                                            selected = selected,
                                            progress = selectionProgress,
                                            tint = iconColor,
                                            cutoutColor = indicatorColor
                                        )
                                    } else {
                                        Icon(
                                            navigationItemIcon(item),
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp),
                                            tint = iconColor
                                        )
                                    }
                                }
                                Text(
                                    navigationItemLabel(item),
                                    style = AppTypography.labelMedium,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = labelColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun navigationTabId(id: String): AppTabId? = when (id) {
    "HOME" -> AppTabId.HOME
    "HISTORY" -> AppTabId.HISTORY
    "BOOKSHELF" -> AppTabId.BOOKSHELF
    "PROFILE" -> AppTabId.PROFILE
    else -> null
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
            Text(title, style = AppTypography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
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
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        Surface(
            shape = AppShapes.compact,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Row(Modifier.fillMaxWidth().selectableGroup().padding(AppSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                verticalAlignment = Alignment.CenterVertically) {
                options.forEachIndexed { index, option ->
                    Box(
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                            .clip(AppShapes.compact)
                            .background(if (index == selectedIndex) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            .selectable(
                                selected = index == selectedIndex,
                                role = Role.RadioButton,
                                onClick = { if (index != selectedIndex) onSelect(index) }
                            ).padding(horizontal = AppSpacing.xs, vertical = AppSpacing.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(option, style = AppTypography.labelLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = if (index == selectedIndex) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChoiceSettingRow(
    icon: ImageVector? = null,
    title: String,
    options: List<String>,
    selectedIndex: Int,
    enabled: Boolean = true,
    readerSheetStyle: Boolean = false,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(onClick = { expanded = true }, enabled = enabled, color = Color.Transparent) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = if (readerSheetStyle) 48.dp else 64.dp)
                .padding(
                    horizontal = if (readerSheetStyle) 0.dp else AppSpacing.lg,
                    vertical = if (readerSheetStyle) 0.dp else AppSpacing.sm
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            if (icon != null) SettingIcon(icon)
            Text(title, style = if (readerSheetStyle) MaterialTheme.typography.bodyMedium else AppTypography.bodyLarge, modifier = Modifier.weight(1f))
            Box {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(options.getOrNull(selectedIndex).orEmpty(), style = AppTypography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(Icons.Filled.ExpandMore, null, modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SettingsChoiceMenu(
                    expanded = expanded && enabled,
                    options = options,
                    selectedIndex = selectedIndex,
                    onDismiss = { expanded = false },
                    onSelect = onSelect
                )
            }
        }
    }
}

@Composable
private fun SettingsChoiceMenu(
    expanded: Boolean,
    options: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            DropdownMenuItem(
                text = { Text(option, color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface) },
                trailingIcon = { if (selected) Icon(Icons.Filled.Check, null) },
                modifier = Modifier.semantics { this.selected = selected },
                onClick = {
                    onDismiss()
                    if (!selected) onSelect(index)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    helpText: String? = null,
    icon: ImageVector? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
        .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
        .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) SettingIcon(icon)
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f, fill = false), style = AppTypography.bodyLarge)
            if (helpText != null) {
                val tooltipState = rememberTooltipState(isPersistent = true)
                val scope = rememberCoroutineScope()
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(helpText) } },
                    state = tooltipState
                ) {
                    IconButton(modifier = Modifier.size(AppSpacing.xl),
                        onClick = { scope.launch { tooltipState.show() } }) {
                        Icon(
                            Icons.Outlined.HelpOutline,
                            contentDescription = stringResource(R.string.settings_hide_home_recommendations_help),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun CacheRow(title: String, value: String, busy: Boolean, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(AppSpacing.lg), horizontalArrangement = Arrangement.spacedBy(AppSpacing.md), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Text(title, style = AppTypography.bodyLarge)
            Text(value, style = AppTypography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text(action, style = AppTypography.labelLarge)
        }
    }
}

@Composable
internal fun LinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    enabled: Boolean = true,
    centered: Boolean = false,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, enabled = enabled, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start
        ) {
            SettingIcon(icon)
            if (centered) {
                Text(
                    title,
                    style = AppTypography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = AppSpacing.sm)
                )
            } else {
                Column(Modifier.weight(1f).padding(horizontal = AppSpacing.md)) {
                    Text(title, style = AppTypography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    subtitle?.let {
                        Text(it, style = AppTypography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                value?.let {
                    Text(
                        it,
                        style = AppTypography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(.8f).padding(end = AppSpacing.xs),
                        textAlign = TextAlign.End,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (enabled) Icon(Icons.Filled.ChevronRight, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingIcon(icon: ImageVector) {
    Box(
        modifier = Modifier.size(36.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun PaletteSettingRow(
    selected: AppPalette,
    onSelect: (AppPalette) -> Unit
) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Column(
        Modifier.fillMaxWidth().padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            SettingIcon(Icons.Filled.Palette)
            Text(stringResource(R.string.settings_palette), style = AppTypography.bodyLarge)
        }
        Row(
            Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AppPalette.available.forEach { palette ->
                val isSelected = palette == selected
                val label = stringResource(
                    when (palette) {
                        AppPalette.INK -> R.string.settings_palette_ink
                        AppPalette.SAKURA -> R.string.settings_palette_sakura
                        AppPalette.DYNAMIC -> R.string.settings_palette_dynamic
                        AppPalette.MONOCHROME -> R.string.settings_palette_monochrome
                    }
                )
                Column(
                    modifier = Modifier
                        .clip(AppShapes.compact)
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { if (!isSelected) onSelect(palette) }
                        )
                        .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                ) {
                    val (accent, base) = paletteSwatch(palette, darkTheme)
                    val fill = if (palette == AppPalette.DYNAMIC) {
                        Brush.sweepGradient(
                            listOf(Color(0xFF4285F4), Color(0xFF34A853), Color(0xFFFBBC05), Color(0xFFEA4335), Color(0xFF4285F4))
                        )
                    } else {
                        Brush.verticalGradient(0f to accent, 0.5f to accent, 0.5f to base, 1f to base)
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .border(
                                width = 2.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = CircleShape
                            )
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(fill)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                                    .padding(2.dp),
                                tint = Color.White
                            )
                        }
                    }
                    Text(
                        label,
                        style = AppTypography.labelMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** The section state and saved scroll positions are shared by both window layouts. */
@Composable
private fun SettingsPaneLayout(
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val wide = appUsesWideLayout()
    Row(modifier) {
        if (wide) {
            Column(
                Modifier.width(AppLayout.settingsNavigationWidth).fillMaxHeight().verticalScroll(rememberScrollState())
                    .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
            ) {
                val sections = listOf(
                    Triple("MAIN", R.string.settings_general_section, Icons.Filled.Settings),
                    Triple("NAVIGATION", R.string.settings_navigation_section, Icons.Filled.Reorder),
                    Triple("READING", R.string.settings_reading_section, Icons.Filled.MenuBook),
                    Triple("DOWNLOAD", R.string.download_settings, Icons.Filled.Download),
                    Triple("STORAGE", R.string.settings_storage_analysis, Icons.Filled.Storage),
                    Triple("ABOUT", R.string.about, Icons.Filled.Info)
                )
                sections.forEach { (id, title, icon) ->
                    NavigationDrawerItem(
                        selected = selected == id,
                        onClick = { onSelected(id) },
                        label = { Text(stringResource(title)) },
                        icon = { Icon(icon, null) }
                    )
                }
            }
            VerticalDivider()
        }
        Box(Modifier.weight(1f).fillMaxHeight()) { content() }
    }
}
