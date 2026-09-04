package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NoAdultContent
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import coil.annotation.ExperimentalCoilApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.breakyuna.esjzone.AppLanguage
import com.breakyuna.esjzone.GlobalSettings
import com.breakyuna.esjzone.MainActivity
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.database.dao.put
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.features.logout
import com.breakyuna.esjzone.ui.navigation.LocalAppNavigator
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.pushIfNotCurrent
import com.breakyuna.esjzone.ui.screen.LoginScreen
import com.breakyuna.esjzone.ui.theme.QuietEditorial
import com.breakyuna.esjzone.ui.theme.catppuccin.CatppuccinThemeType
import com.breakyuna.esjzone.util.AppLogger
import com.breakyuna.esjzone.util.LocaleHelper

/** Quiet Editorial settings document, preserving the existing preference and session behavior. */
object SettingsPage : Screen {
    private fun readResolve(): Any = SettingsPage

    @OptIn(ExperimentalCoilApi::class)
    @Composable
    override fun Content() {
        val appNavigator = LocalAppNavigator.current
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val adult by GlobalSettings.adult
        val theme by GlobalSettings.theme
        val domain by GlobalSettings.domain
        val language by GlobalSettings.language
        var showLogoutConfirmation by remember { mutableStateOf(false) }
        var logoutInProgress by remember { mutableStateOf(false) }
        var cacheOperation by remember { mutableStateOf<String?>(null) }
        var cacheStatsError by remember { mutableStateOf(false) }
        var cacheClearError by remember { mutableStateOf(false) }
        var localCacheStats by remember { mutableStateOf<LocalCacheStats?>(null) }
        val crashReport by AppLogger.crashReportFlow.collectAsState()

        fun persistCache(key: String, value: String) {
            scope.launch(Dispatchers.IO) {
                try {
                    MainActivity.database.cacheDao().put(key, value)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.e("SettingsPage", "Failed to persist setting: $key", e)
                }
            }
        }

        fun refreshLocalCacheStats() {
            cacheStatsError = false
            scope.launch(Dispatchers.IO) {
                try {
                    val pageStats = EsjzoneClient.pageCacheStats()
                    val imageBytes = MainActivity.imageLoader.diskCache?.size ?: 0L
                    withContext(Dispatchers.Main) {
                        localCacheStats = LocalCacheStats(pageStats.sizeBytes, pageStats.entryCount, imageBytes)
                        cacheStatsError = false
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.e("SettingsPage", "Failed to calculate cache statistics", e)
                    withContext(Dispatchers.Main) { cacheStatsError = true }
                }
            }
        }

        LaunchedEffect(Unit) {
            refreshLocalCacheStats()
            AppLogger.refreshCrashReport()
        }

        Column(modifier = Modifier.fillMaxSize()) {
            SettingsHeader(onBack = { navigator?.pop() })
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = QuietEditorial.pagePadding, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingsSection(Icons.Filled.Dns, stringResource(R.string.settings_network_section)) {
                    SettingsLabel(stringResource(R.string.settings_active_mirror))
                    GlobalSettings.DOMAINS.forEach { candidate ->
                        MirrorRow(
                            domain = candidate,
                            selected = candidate == domain,
                            backup = candidate != GlobalSettings.DOMAINS.first(),
                            onClick = {
                                GlobalSettings.setDomain(candidate)
                                scope.launch(Dispatchers.IO) { EsjzoneClient.clearPageCache() }
                                persistCache("domain", candidate)
                            }
                        )
                    }
                    Text(
                        stringResource(R.string.settings_mirror_note),
                        style = QuietEditorial.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                SettingsSection(Icons.Filled.ColorLens, stringResource(R.string.settings_appearance_section)) {
                    Text(
                        stringResource(R.string.settings_theme_description),
                        style = QuietEditorial.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ThemeFamily(stringResource(R.string.settings_theme_frappe), CatppuccinThemeType.frappes(), theme) {
                        GlobalSettings.setTheme(it)
                        persistCache("theme", it.name)
                    }
                    ThemeFamily(stringResource(R.string.settings_theme_latte), CatppuccinThemeType.lattes(), theme) {
                        GlobalSettings.setTheme(it)
                        persistCache("theme", it.name)
                    }
                    ThemeFamily(stringResource(R.string.settings_theme_macchiato), CatppuccinThemeType.macchiatos(), theme) {
                        GlobalSettings.setTheme(it)
                        persistCache("theme", it.name)
                    }
                    ThemeFamily(stringResource(R.string.settings_theme_mocha), CatppuccinThemeType.mochas(), theme) {
                        GlobalSettings.setTheme(it)
                        persistCache("theme", it.name)
                    }
                }

                SettingsSection(Icons.Filled.Translate, stringResource(R.string.settings_language_section)) {
                    Text(
                        stringResource(R.string.settings_language_description),
                        style = QuietEditorial.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    for (candidate in AppLanguage.entries) {
                        LanguageRow(
                            title = stringResource(candidate.titleRes),
                            description = stringResource(candidate.subtitleRes),
                            selected = candidate == language,
                            onClick = {
                                GlobalSettings.setLanguage(candidate)
                                LocaleHelper.syncSystemLocale(context, candidate)
                                persistCache("language", candidate.code)
                            }
                        )
                    }
                }

                SettingsSection(Icons.Filled.NoAdultContent, stringResource(R.string.settings_content_section)) {
                    SettingToggleRow(
                        title = stringResource(R.string.settings_showadultcontent),
                        summary = stringResource(R.string.settings_adult_description),
                        badge = stringResource(R.string.adult_badge),
                        checked = adult,
                        onCheckedChange = {
                            GlobalSettings.setAdult(it)
                            persistCache("show_adult", it.toString())
                        }
                    )
                }

                SettingsSection(Icons.Filled.Storage, stringResource(R.string.settings_storage_section)) {
                    val cacheStats = localCacheStats
                    CacheRow(
                        title = stringResource(R.string.settings_page_cache),
                        value = when {
                            cacheStatsError -> stringResource(R.string.local_cache_stats_failed)
                            cacheStats == null ->
                            stringResource(R.string.local_cache_loading)
                            else -> {
                            stringResource(
                                R.string.local_cache_pages,
                                formatBytes(cacheStats.pageBytes),
                                cacheStats.pageEntries
                            )
                            }
                        },
                        accent = MaterialTheme.colorScheme.primary,
                        actionLabel = stringResource(R.string.local_cache_clear_pages),
                        busy = cacheOperation != null,
                        onAction = {
                            cacheOperation = "pages"
                            cacheClearError = false
                            scope.launch(Dispatchers.IO) {
                                try {
                                    EsjzoneClient.clearPageCache()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    AppLogger.e("SettingsPage", "Failed to clear page cache", e)
                                    withContext(Dispatchers.Main) { cacheClearError = true }
                                } finally {
                                    withContext(Dispatchers.Main) {
                                        cacheOperation = null
                                        refreshLocalCacheStats()
                                    }
                                }
                            }
                        }
                    )
                    HorizontalDivider(color = quietRuleColor())
                    CacheRow(
                        title = stringResource(R.string.settings_image_cache),
                        value = when {
                            cacheStatsError -> stringResource(R.string.local_cache_stats_failed)
                            cacheStats == null -> stringResource(R.string.local_cache_loading)
                            else -> formatBytes(cacheStats.imageBytes)
                        },
                        accent = MaterialTheme.colorScheme.tertiary,
                        actionLabel = stringResource(R.string.local_cache_clear_images),
                        busy = cacheOperation != null,
                        onAction = {
                            cacheOperation = "images"
                            cacheClearError = false
                            scope.launch(Dispatchers.IO) {
                                try {
                                    MainActivity.imageLoader.memoryCache?.clear()
                                    MainActivity.imageLoader.diskCache?.clear()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    AppLogger.e("SettingsPage", "Failed to clear image cache", e)
                                    withContext(Dispatchers.Main) { cacheClearError = true }
                                } finally {
                                    withContext(Dispatchers.Main) {
                                        cacheOperation = null
                                        refreshLocalCacheStats()
                                    }
                                }
                            }
                        }
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                        shape = QuietEditorial.controlShape,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(
                                stringResource(R.string.settings_cache_note),
                                style = QuietEditorial.body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                    if (cacheOperation != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                stringResource(R.string.local_cache_clearing),
                                style = QuietEditorial.body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                    if (cacheStatsError) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.local_cache_stats_failed),
                                style = QuietEditorial.body,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = ::refreshLocalCacheStats) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                    if (cacheClearError) {
                        Text(
                            stringResource(R.string.local_cache_clear_failed),
                            style = QuietEditorial.body,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                SettingsSection(Icons.Filled.BugReport, stringResource(R.string.settings_diagnostics_section)) {
                    SettingsLinkRow(
                        icon = Icons.Filled.BugReport,
                        title = stringResource(R.string.system_logs),
                        summary = stringResource(R.string.settings_logs_description),
                        tag = "S13",
                        onClick = { navigator?.pushIfNotCurrent(LogsPage) }
                    )
                    HorizontalDivider(color = quietRuleColor())
                    SettingsLinkRow(
                        icon = Icons.Filled.Info,
                        title = stringResource(R.string.logs_last_crash_title),
                        summary = stringResource(
                            if (crashReport == null) R.string.settings_crash_none
                            else R.string.settings_crash_available
                        ),
                        tag = "M04",
                        enabled = crashReport != null,
                        onClick = { navigator?.pushIfNotCurrent(LogsPage) }
                    )
                }

                SettingsSection(Icons.Filled.Info, stringResource(R.string.settings_about_section)) {
                    SettingsLinkRow(
                        icon = Icons.Filled.Info,
                        title = stringResource(R.string.about),
                        summary = stringResource(R.string.profile_about_description),
                        tag = "S14",
                        onClick = { navigator?.pushIfNotCurrent(AboutPage) }
                    )
                }
                Surface(
                    shape = QuietEditorial.largeShape,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SettingsLinkRow(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        title = stringResource(R.string.settings_logout_title),
                        summary = stringResource(R.string.logout_consequence),
                        tag = "M02",
                        destructive = true,
                        onClick = { if (!logoutInProgress) showLogoutConfirmation = true }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (showLogoutConfirmation) {
            AlertDialog(
                onDismissRequest = { if (!logoutInProgress) showLogoutConfirmation = false },
                shape = QuietEditorial.dialogShape,
                title = { Text(stringResource(R.string.logout_confirm_message)) },
                text = { Text(stringResource(R.string.logout_consequence)) },
                dismissButton = {
                    TextButton(
                        onClick = { showLogoutConfirmation = false },
                        enabled = !logoutInProgress
                    ) { Text(stringResource(R.string.logout_cancel)) }
                },
                confirmButton = {
                    TextButton(
                        enabled = !logoutInProgress,
                        onClick = {
                            logoutInProgress = true
                            scope.launch(Dispatchers.IO) {
                                try {
                                    EsjzoneClient.logout(authorization)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    AppLogger.w("SettingsPage", "Server logout failed; clearing local session", e)
                                }
                                try {
                                    EsjzoneClient.clearSession(
                                        authorization.domain.ifBlank { GlobalSettings.domain.value }
                                    )
                                    val dao = MainActivity.database.cacheDao()
                                    dao.deleteByKey("ews_key")
                                    dao.deleteByKey("ews_token")
                                    dao.getAll()
                                        .filter { it.key.startsWith("profile:") }
                                        .forEach { dao.delete(it) }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    AppLogger.e("SettingsPage", "Failed to clear local session", e)
                                }
                                withContext(Dispatchers.Main) {
                                    logoutInProgress = false
                                    showLogoutConfirmation = false
                                    appNavigator?.replaceAll(LoginScreen)
                                }
                            }
                        }
                    ) {
                        if (logoutInProgress) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.logout_confirm))
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun quietRuleColor(): Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .padding(top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reader_back))
                }
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(stringResource(R.string.settings_screen_title), style = QuietEditorial.display)
                    Text(
                        stringResource(R.string.settings_screen_subtitle),
                        style = QuietEditorial.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(thickness = QuietEditorial.hairline, color = quietRuleColor())
        }
    }
}

@Composable
private fun SettingsSection(
    icon: ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Text(
                text = title,
                style = QuietEditorial.sectionTitle,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = QuietEditorial.largeShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                content = content
            )
        }
    }
}

@Composable
private fun SettingsLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun LanguageRow(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = QuietEditorial.cardShape,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else MaterialTheme.colorScheme.surface.copy(alpha = 0.34f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = QuietEditorial.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    description,
                    style = QuietEditorial.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RadioMark(selected)
        }
    }
}

@Composable
private fun MirrorRow(domain: String, selected: Boolean, backup: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = QuietEditorial.cardShape,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else MaterialTheme.colorScheme.surface.copy(alpha = 0.34f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        domain,
                        style = QuietEditorial.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (backup) QuietBadge(stringResource(R.string.settings_backup_mirror))
                }
                Text(
                    stringResource(if (backup) R.string.settings_backup_description else R.string.settings_primary_description),
                    style = QuietEditorial.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RadioMark(selected)
        }
    }
}

@Composable
private fun ThemeFamily(
    label: String,
    themes: List<CatppuccinThemeType>,
    selected: CatppuccinThemeType,
    onSelect: (CatppuccinThemeType) -> Unit
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            themes.forEach { type ->
                Surface(
                    onClick = { onSelect(type) },
                    shape = QuietEditorial.controlShape,
                    color = type.baseColor,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (selected == type) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    summary: String,
    badge: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = QuietEditorial.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                QuietBadge(badge, destructive = true)
            }
            Text(summary, style = QuietEditorial.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CacheRow(
    title: String,
    value: String,
    accent: Color,
    actionLabel: String,
    busy: Boolean,
    onAction: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = QuietEditorial.title)
            Text(value, style = QuietEditorial.body, color = accent)
        }
        Button(
            onClick = onAction,
            enabled = !busy,
            shape = QuietEditorial.largeShape,
            contentPadding = ButtonDefaults.ContentPadding,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                contentColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.widthIn(min = 96.dp)
        ) { Text(actionLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun SettingsLinkRow(
    icon: ImageVector,
    title: String,
    summary: String,
    tag: String,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Surface(onClick = onClick, enabled = enabled, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = if (destructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp)) }
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = QuietEditorial.title,
                        color = color,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    QuietBadge(tag, destructive = destructive)
                }
                Text(
                    summary,
                    style = QuietEditorial.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (enabled) Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = color.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun QuietBadge(text: String, destructive: Boolean = false) {
    Surface(
        shape = QuietEditorial.badgeShape,
        color = if (destructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(start = 8.dp)
    ) {
        Text(
            text,
            style = QuietEditorial.smallLabel,
            color = if (destructive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun RadioMark(selected: Boolean) {
    Box(
        modifier = Modifier.size(32.dp).clip(CircleShape).background(
            if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
        ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .then(
                    if (selected) Modifier.background(MaterialTheme.colorScheme.primary)
                    else Modifier.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
        ) {
            if (selected) {
                Box(
                    modifier = Modifier.size(10.dp).align(Alignment.Center).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimary)
                )
            }
        }
    }
}

private data class LocalCacheStats(val pageBytes: Long, val pageEntries: Int, val imageBytes: Long)

private fun formatBytes(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    val kib = 1024.0
    val mib = kib * 1024.0
    return when {
        safeBytes >= mib -> String.format("%.1f MiB", safeBytes / mib)
        safeBytes >= kib -> String.format("%.1f KiB", safeBytes / kib)
        else -> "$safeBytes B"
    }
}
