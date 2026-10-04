@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.breakyuna.esjzone.ui.page

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.backup.BackupCategory
import com.breakyuna.esjzone.backup.LocalBackup
import com.breakyuna.esjzone.database.BookshelfRepository
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.designsystem.accountContentWidth
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import com.breakyuna.esjzone.ui.navigation.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class LocalBackupModel : AppStateViewModel<LocalBackupModel.Status>(Status()) {
    data class Status(val busy: Boolean = false, val result: Int? = null, val archives: List<java.io.File> = emptyList())
    fun refreshArchives() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            mutableState.value = state.value.copy(archives = PresentationAccess.autoBackup.archives())
        }
    }
    fun setAutoBackupDownloads(value: Boolean) {
        viewModelScope.launch {
            try {
                PresentationAccess.autoBackup.setIncludeDownloads(value)
                mutableState.value = state.value.copy(result = null)
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) {
                mutableState.value = state.value.copy(result = R.string.backup_failed)
            }
        }
    }
    fun setAutoBackup(enabled: Boolean, scope: String) {
        viewModelScope.launch {
            try {
                PresentationAccess.autoBackup.setEnabled(enabled, scope)
                mutableState.value = state.value.copy(result = null)
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) {
                mutableState.value = state.value.copy(result = R.string.backup_failed)
            }
        }
    }
    fun deleteArchive(file: java.io.File) {
        if (state.value.busy) return
        mutableState.value = state.value.copy(busy = true, result = null)
        viewModelScope.launch {
            try {
                val archives = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    check(PresentationAccess.autoBackup.deleteArchive(file))
                    PresentationAccess.autoBackup.archives()
                }
                mutableState.value = state.value.copy(busy = false, result = R.string.backup_deleted, archives = archives)
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) {
                mutableState.value = state.value.copy(busy = false, result = R.string.backup_delete_failed)
                refreshArchives()
            }
        }
    }
    fun exportArchive(context: Context, file: java.io.File, uri: Uri) {
        if (state.value.busy) return
        mutableState.value = state.value.copy(busy = true, result = null)
        viewModelScope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    file.inputStream().use { input ->
                        checkNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { input.copyTo(it) }
                    }
                }
                mutableState.value = state.value.copy(busy = false, result = R.string.backup_done)
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) {
                mutableState.value = state.value.copy(busy = false, result = R.string.backup_failed)
            }
        }
    }
    fun run(context: Context, uri: Uri, scope: String, categories: Set<BackupCategory>, restore: Boolean) {
        if (state.value.busy) return
        mutableState.value = state.value.copy(busy = true, result = null)
        viewModelScope.launch {
            try {
                if (restore) LocalBackup.restore(context, uri, PresentationAccess.database, scope, categories)
                else LocalBackup.export(context, uri, PresentationAccess.database, scope, categories)
                mutableState.value = state.value.copy(busy = false, result = R.string.backup_done)
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) {
                mutableState.value = state.value.copy(busy = false, result = R.string.backup_failed)
            }
        }
    }
}

object LocalBackupPage : AppDestination {
    private fun readResolve(): Any = LocalBackupPage
    override val key = "LocalBackupPage"
    @Composable
    override fun Content() {
        val context = LocalContext.current.applicationContext
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val scope = BookshelfRepository.scopeFor(authorization)
        val model = rememberAppViewModel { LocalBackupModel() }
        val state by model.state.collectAsStateWithLifecycle()
        val completedAt by PresentationAccess.autoBackup.completedAt.collectAsStateWithLifecycle()
        val autoBackupEnabled by PresentationAccess.autoBackup.enabled.collectAsStateWithLifecycle()
        val autoBackupDownloads by PresentationAccess.autoBackup.includeDownloads.collectAsStateWithLifecycle()
        LaunchedEffect(completedAt) { model.refreshArchives() }
        var pendingArchive by rememberSaveable { mutableStateOf<String?>(null) }
        var restoreArchive by rememberSaveable { mutableStateOf<String?>(null) }
        var deleteArchive by rememberSaveable { mutableStateOf<String?>(null) }
        val exportArchive = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            val path = pendingArchive
            if (uri != null && path != null) model.exportArchive(context, java.io.File(path), uri)
            pendingArchive = null
        }
        var selectedNames by rememberSaveable {
            mutableStateOf(BackupCategory.entries.filter { it != BackupCategory.DOWNLOADS }.map { it.name })
        }
        var pendingNames by rememberSaveable { mutableStateOf(emptyList<String>()) }
        var pendingScope by rememberSaveable { mutableStateOf("") }
        var confirmImport by rememberSaveable { mutableStateOf(false) }
        val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri != null) model.run(context, uri, pendingScope, pendingNames.map { BackupCategory.valueOf(it) }.toSet(), false)
        }
        val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) model.run(context, uri, pendingScope, pendingNames.map { BackupCategory.valueOf(it) }.toSet(), true)
        }
        Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.local_backup), style = AppTypography.titleMedium) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background), navigationIcon = {
            IconButton(onClick = { navigator?.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.reading_stats_back)) }
        }) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).accountContentWidth().verticalScroll(rememberScrollState())
                .padding(start = AppSpacing.lg, end = AppSpacing.lg, top = AppSpacing.sm, bottom = AppSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
                Text(stringResource(R.string.backup_description), style = AppTypography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = AppSpacing.xs))
                SettingsSection {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.auto_backup), style = AppTypography.bodyMedium,
                            modifier = Modifier.weight(1f))
                        Switch(checked = autoBackupEnabled, enabled = !state.busy,
                            onCheckedChange = { model.setAutoBackup(it, scope) })
                    }
                    Text(stringResource(R.string.auto_backup_policy), style = AppTypography.bodySmall,
                        modifier = Modifier.padding(AppSpacing.md), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = AppSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = autoBackupDownloads, enabled = !state.busy,
                            onCheckedChange = model::setAutoBackupDownloads)
                        Text(stringResource(R.string.downloads), style = AppTypography.bodyMedium,
                            modifier = Modifier.padding(end = AppSpacing.md))
                        Text(stringResource(R.string.backup_downloads_size_warning), style = AppTypography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f).padding(end = AppSpacing.md, top = AppSpacing.sm, bottom = AppSpacing.sm))
                    }
                    if (state.archives.isEmpty()) Text(stringResource(R.string.auto_backup_empty),
                        modifier = Modifier.padding(AppSpacing.md), style = AppTypography.bodyMedium)
                    state.archives.forEach { file ->
                        Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,
                                    java.text.DateFormat.SHORT).format(java.util.Date(
                                    file.name.removePrefix("esjzone-auto-").removeSuffix(".zip").toLongOrNull() ?: 0L)),
                                    style = AppTypography.bodyMedium, modifier = Modifier.weight(1f))
                                IconButton(enabled = !state.busy, onClick = { deleteArchive = file.absolutePath }) {
                                    Icon(Icons.Outlined.Delete, stringResource(R.string.backup_delete),
                                        tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                                TextButton(enabled = !state.busy, onClick = { restoreArchive = file.absolutePath }) {
                                    Text(stringResource(R.string.backup_import))
                                }
                                TextButton(enabled = !state.busy, onClick = {
                                    pendingArchive = file.absolutePath; exportArchive.launch(file.name)
                                }) { Text(stringResource(R.string.backup_export)) }
                            }
                        }
                    }
                }
                SettingsSection {
                    BackupCategory.entries.forEach { category ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = AppSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = category.name in selectedNames, enabled = !state.busy, onCheckedChange = { checked ->
                                selectedNames = if (checked) selectedNames + category.name else selectedNames - category.name
                            })
                            Text(stringResource(when (category) {
                                BackupCategory.UNDERLINES -> R.string.reader_underlines
                                BackupCategory.BOOKMARKS -> R.string.bookmarks
                                BackupCategory.DOWNLOADS -> R.string.downloads
                                BackupCategory.READING -> R.string.backup_reading
                                BackupCategory.HISTORY -> R.string.backup_history
                                BackupCategory.SEARCH -> R.string.backup_search
                                BackupCategory.GROUPS -> R.string.shelf_groups
                            }), style = AppTypography.bodyMedium, modifier = Modifier.padding(end = AppSpacing.md))
                            if (category == BackupCategory.DOWNLOADS) {
                                Text(stringResource(R.string.backup_downloads_size_warning), style = AppTypography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1f).padding(end = AppSpacing.md, top = AppSpacing.sm, bottom = AppSpacing.sm))
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    Button(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = AppShapes.compact,
                        enabled = !state.busy && selectedNames.isNotEmpty(), onClick = {
                        pendingNames = selectedNames; pendingScope = scope
                        create.launch("esjzone-backup-${java.time.LocalDate.now()}.zip")
                    }) { Text(stringResource(R.string.backup_export)) }
                    OutlinedButton(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = AppShapes.compact,
                        enabled = !state.busy && selectedNames.isNotEmpty(), onClick = { confirmImport = true }) {
                        Text(stringResource(R.string.backup_import))
                    }
                }
                if (state.busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.backup_working), style = AppTypography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.result?.let {
                    Surface(shape = AppShapes.standard, color = MaterialTheme.colorScheme.surface) {
                        Text(stringResource(it), style = AppTypography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().padding(AppSpacing.lg))
                    }
                }
            }
        }
        if (deleteArchive != null) AlertDialog(onDismissRequest = { deleteArchive = null },
            title = { Text(stringResource(R.string.backup_delete)) },
            text = { Text(stringResource(R.string.backup_delete_confirm)) },
            confirmButton = { TextButton(enabled = !state.busy, onClick = {
                val path = deleteArchive
                deleteArchive = null
                if (path != null) model.deleteArchive(java.io.File(path))
            }) { Text(stringResource(R.string.backup_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteArchive = null }) { Text(stringResource(android.R.string.cancel)) } })
        if (restoreArchive != null) AlertDialog(onDismissRequest = { restoreArchive = null },
            title = { Text(stringResource(R.string.backup_import)) },
            text = { Text(stringResource(R.string.backup_merge_hint)) },
            confirmButton = { TextButton(onClick = {
                val path = restoreArchive
                restoreArchive = null
                if (path != null) model.run(context, Uri.fromFile(java.io.File(path)), scope,
                    selectedNames.map { BackupCategory.valueOf(it) }.toSet(), true)
            }, enabled = selectedNames.isNotEmpty()) { Text(stringResource(android.R.string.ok)) } },
            dismissButton = { TextButton(onClick = { restoreArchive = null }) { Text(stringResource(android.R.string.cancel)) } })
        if (confirmImport) AlertDialog(onDismissRequest = { confirmImport = false },
            title = { Text(stringResource(R.string.backup_import)) },
            text = { Text(stringResource(R.string.backup_merge_hint)) },
            confirmButton = { TextButton(onClick = {
                confirmImport = false; pendingNames = selectedNames; pendingScope = scope
                open.launch(arrayOf("application/zip", "application/octet-stream"))
            }) { Text(stringResource(android.R.string.ok)) } },
            dismissButton = { TextButton(onClick = { confirmImport = false }) { Text(stringResource(android.R.string.cancel)) } })
    }
}
