package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.NoAdultContent
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.AppLanguage
import com.breakyuna.esjzone.BuildConfig
import com.breakyuna.esjzone.Constants
import com.breakyuna.esjzone.update.ReleaseCheckState
import com.breakyuna.esjzone.update.ReleaseUpdateChecker
import com.breakyuna.esjzone.ui.designsystem.AccountIconBadge
import com.breakyuna.esjzone.ui.designsystem.accountContentWidth
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.ui.navigation.AppDestination
import com.breakyuna.esjzone.ui.navigation.LocalAppNavigator
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.rememberAppViewModel
import com.breakyuna.esjzone.ui.screen.LoginScreen
import com.breakyuna.esjzone.ui.designsystem.AppThemeVariant
import com.breakyuna.esjzone.util.AppLogger
import com.breakyuna.esjzone.util.LocaleHelper

/** Account preferences and local maintenance; every write retains existing semantics. */
object SettingsPage : AppDestination {
    private fun readResolve(): Any = SettingsPage

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val rootNavigator = LocalAppNavigator.current
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val context = LocalContext.current
        val model = rememberAppViewModel { SettingsPageModel() }
        val state by model.state.collectAsState()
        val adult by PresentationAccess.settings.adult
        val theme by PresentationAccess.settings.theme
        val domain by PresentationAccess.settings.domain
        val language by PresentationAccess.settings.language
        val autoSave by PresentationAccess.settings.readerAutoSave
        val readerSettings by PresentationAccess.readerSettings.settings.collectAsState()
        val crashReport by AppLogger.crashReportFlow.collectAsState()
        val checkState by ReleaseUpdateChecker.status.collectAsState()
        val autoCheck by ReleaseUpdateChecker.autoCheck.collectAsState()
        var showLogout by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            model.refreshCacheStats()
            AppLogger.refreshCrashReport()
            ReleaseUpdateChecker.initialize(context)
        }
        LaunchedEffect(state.logoutCompleted) { if (state.logoutCompleted) rootNavigator?.replaceAll(LoginScreen) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_screen_title), style = com.breakyuna.esjzone.ui.designsystem.AppTypography.titleLarge) },
                    navigationIcon = { BackIconButton { navigator?.pop() } }
                )
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().accountContentWidth().padding(padding).verticalScroll(rememberScrollState()).padding(com.breakyuna.esjzone.ui.designsystem.AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(com.breakyuna.esjzone.ui.designsystem.AppSpacing.lg)) {
                SettingsSection(Icons.Filled.Dns, stringResource(R.string.settings_network_section)) {
                    Text(stringResource(R.string.settings_active_mirror), style = com.breakyuna.esjzone.ui.designsystem.AppTypography.labelLarge)
                    PresentationAccess.settings.DOMAINS.forEach { candidate ->
                        ChoiceRow(
                            title = candidate,
                            subtitle = stringResource(if (candidate.contains(".one")) R.string.settings_backup_description else R.string.settings_primary_description),
                            selected = candidate == domain,
                            onClick = { PresentationAccess.settings.setDomain(candidate); model.clearPageCache(); model.persist("domain", candidate) }
                        )
                    }
                    Text(stringResource(R.string.settings_mirror_note), style = com.breakyuna.esjzone.ui.designsystem.AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = com.breakyuna.esjzone.ui.designsystem.AppSpacing.sm))
                }

                SettingsSection(Icons.Filled.ColorLens, stringResource(R.string.settings_appearance_section)) {
                    Text(stringResource(R.string.settings_theme_description), style = com.breakyuna.esjzone.ui.designsystem.AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ThemeSwatches(
                    label = stringResource(R.string.settings_theme),
                    themes = AppThemeVariant.entries,
                        selected = theme
                    ) {
                        PresentationAccess.settings.setTheme(it)
                        model.persist("theme", it.name)
                    }
                }

                SettingsSection(Icons.Filled.Language, stringResource(R.string.settings_language_section)) {
                    Text(stringResource(R.string.settings_language_description), style = com.breakyuna.esjzone.ui.designsystem.AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AppLanguage.entries.forEach { candidate ->
                        ChoiceRow(stringResource(candidate.titleRes), stringResource(candidate.subtitleRes), candidate == language) { PresentationAccess.settings.setLanguage(candidate); LocaleHelper.syncSystemLocale(context, candidate); model.persist("language", candidate.code) }
                    }
                }

                SettingsSection(Icons.Filled.NoAdultContent, stringResource(R.string.settings_content_section)) {
                    ToggleRow(stringResource(R.string.settings_showadultcontent), stringResource(R.string.settings_adult_description), adult) { PresentationAccess.settings.setAdult(it); model.persist("show_adult", it.toString()) }
                }

                SettingsSection(Icons.Filled.MenuBook, stringResource(R.string.settings_reader_section)) {
                    ToggleRow(
                        stringResource(R.string.settings_volume_key_paging),
                        stringResource(R.string.settings_volume_key_paging_description),
                        readerSettings.volumeKeyPaging
                    ) { enabled ->
                        PresentationAccess.readerSettings.saveInBackground(
                            readerSettings.copy(volumeKeyPaging = enabled)
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = com.breakyuna.esjzone.ui.designsystem.AppSpacing.xs))
                    ToggleRow(
                        stringResource(R.string.download_auto_save),
                        stringResource(R.string.download_auto_save_description),
                        autoSave
                    ) { enabled ->
                        PresentationAccess.settings.setReaderAutoSave(enabled)
                        model.persist(PresentationAccess.settings.READER_AUTO_SAVE_KEY, enabled.toString())
                    }
                }

                SettingsSection(Icons.Filled.Storage, stringResource(R.string.settings_storage_section)) {
                    val cache = state.cacheStats
                    CacheRow(stringResource(R.string.settings_page_cache), when { state.cacheStatsError -> stringResource(R.string.local_cache_stats_failed); cache == null -> stringResource(R.string.local_cache_loading); else -> stringResource(R.string.local_cache_pages, formatBytes(cache.pageBytes), cache.pageEntries) }, state.cacheOperation != null, stringResource(R.string.local_cache_clear_pages)) { model.clearPageCache() }
                    HorizontalDivider()
                    CacheRow(stringResource(R.string.settings_image_cache), when { state.cacheStatsError -> stringResource(R.string.local_cache_stats_failed); cache == null -> stringResource(R.string.local_cache_loading); else -> formatBytes(cache.imageBytes) }, state.cacheOperation != null, stringResource(R.string.local_cache_clear_images)) { model.clearImageCache() }
                    Text(stringResource(R.string.settings_cache_note), style = com.breakyuna.esjzone.ui.designsystem.AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.cacheOperation != null) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    if (state.cacheStatsError) TextButton(onClick = model::refreshCacheStats) { Text(stringResource(R.string.retry)) }
                    if (state.cacheClearError) Text(stringResource(R.string.local_cache_clear_failed), color = MaterialTheme.colorScheme.error)
                }

                SettingsSection(Icons.Filled.BugReport, stringResource(R.string.settings_diagnostics_section)) {
                    LinkRow(Icons.Filled.BugReport, stringResource(R.string.system_logs), stringResource(R.string.settings_logs_description)) { navigator?.pushIfNotCurrent(LogsPage) }
                    LinkRow(Icons.Filled.Info, stringResource(R.string.logs_last_crash_title), if (crashReport == null) stringResource(R.string.settings_crash_none) else stringResource(R.string.settings_crash_available), enabled = crashReport != null) { navigator?.pushIfNotCurrent(LogsPage) }
                }
                SettingsSection(Icons.Filled.Info, stringResource(R.string.about)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { ReleaseUpdateChecker.checkNow(context) }
                            .padding(com.breakyuna.esjzone.ui.designsystem.AppSpacing.sm),
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
                    ToggleRow(
                        title = stringResource(R.string.update_auto_check),
                        subtitle = stringResource(R.string.update_auto_check_description),
                        checked = autoCheck,
                        onCheckedChange = { ReleaseUpdateChecker.setAutoCheck(context, it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = com.breakyuna.esjzone.ui.designsystem.AppSpacing.xs))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(com.breakyuna.esjzone.ui.designsystem.AppSpacing.sm),
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
                            .padding(com.breakyuna.esjzone.ui.designsystem.AppSpacing.sm),
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
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = com.breakyuna.esjzone.ui.designsystem.AppShapes.standard) {
                    LinkRow(Icons.AutoMirrored.Filled.Logout, stringResource(R.string.settings_logout_title), stringResource(R.string.logout_consequence), destructive = true) { if (!state.logoutInProgress) showLogout = true }
                }
            }
        }
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
private fun SettingsSection(icon: ImageVector, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(com.breakyuna.esjzone.ui.designsystem.AppSpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(com.breakyuna.esjzone.ui.designsystem.AppSpacing.sm)) {
            AccountIconBadge(icon)
            Text(title, style = com.breakyuna.esjzone.ui.designsystem.AppTypography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = com.breakyuna.esjzone.ui.designsystem.AppShapes.standard) {
            Column(Modifier.fillMaxWidth().padding(com.breakyuna.esjzone.ui.designsystem.AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(com.breakyuna.esjzone.ui.designsystem.AppSpacing.sm), content = content)
        }
    }
}

@Composable
private fun ChoiceRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, shape = com.breakyuna.esjzone.ui.designsystem.AppShapes.compact, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(com.breakyuna.esjzone.ui.designsystem.AppSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, style = com.breakyuna.esjzone.ui.designsystem.AppTypography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(subtitle, style = com.breakyuna.esjzone.ui.designsystem.AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ThemeSwatches(label: String, themes: List<AppThemeVariant>, selected: AppThemeVariant, onSelect: (AppThemeVariant) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(com.breakyuna.esjzone.ui.designsystem.AppSpacing.xs)) {
        Text(label, style = com.breakyuna.esjzone.ui.designsystem.AppTypography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(com.breakyuna.esjzone.ui.designsystem.AppSpacing.sm)) {
            themes.forEach { type ->
                val themeLabel = stringResource(type.labelRes)
                Surface(
                    onClick = { onSelect(type) },
                    shape = CircleShape,
                    color = type.lightAccent,
                    modifier = Modifier
                        .size(40.dp)
                        .semantics {
                            contentDescription = themeLabel
                        }
                ) {
                    if (selected == type) {
                        Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.padding(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(com.breakyuna.esjzone.ui.designsystem.AppSpacing.sm), horizontalArrangement = Arrangement.spacedBy(com.breakyuna.esjzone.ui.designsystem.AppSpacing.md), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, style = com.breakyuna.esjzone.ui.designsystem.AppTypography.labelLarge); Text(subtitle, style = com.breakyuna.esjzone.ui.designsystem.AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked, onCheckedChange) }
}

@Composable
private fun CacheRow(title: String, value: String, busy: Boolean, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(com.breakyuna.esjzone.ui.designsystem.AppSpacing.sm), horizontalArrangement = Arrangement.spacedBy(com.breakyuna.esjzone.ui.designsystem.AppSpacing.md), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, style = com.breakyuna.esjzone.ui.designsystem.AppTypography.labelLarge); Text(value, style = com.breakyuna.esjzone.ui.designsystem.AppTypography.bodySmall, color = MaterialTheme.colorScheme.primary) }; Button(onClick = onClick, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) { Text(action) } }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, subtitle: String, enabled: Boolean = true, destructive: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(com.breakyuna.esjzone.ui.designsystem.AppSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f).padding(horizontal = com.breakyuna.esjzone.ui.designsystem.AppSpacing.md)) { Text(title, style = com.breakyuna.esjzone.ui.designsystem.AppTypography.labelLarge, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface); Text(subtitle, style = com.breakyuna.esjzone.ui.designsystem.AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            if (enabled) Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
