@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.breakyuna.esjzone.ui.page

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.backup.AutoBackup
import com.breakyuna.esjzone.backup.BackupFileNames
import com.breakyuna.esjzone.backup.BackupCategory
import com.breakyuna.esjzone.backup.LocalBackup
import com.breakyuna.esjzone.storage.StorageFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import com.breakyuna.esjzone.database.BookshelfRepository
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.designsystem.accountContentWidth
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import com.breakyuna.esjzone.ui.navigation.*
import com.breakyuna.esjzone.util.copyWithCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class LocalBackupModel : AppStateViewModel<LocalBackupModel.Status>(Status()) {
    data class Status(val busy: Boolean = false, val result: Int? = null, val archives: List<StorageFile> = emptyList())
    fun refreshArchives() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                PresentationAccess.storage.operationMutex.withLock {
                    mutableState.value = state.value.copy(archives = PresentationAccess.autoBackup.archives())
                }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) {
                mutableState.value = state.value.copy(result = R.string.backup_failed)
            }
        }
    }
    fun dismissResult() { mutableState.value = state.value.copy(result = null) }
    fun setIntervalHours(value: Int) {
        viewModelScope.launch {
            try {
                PresentationAccess.autoBackup.setIntervalHours(value)
                mutableState.value = state.value.copy(result = null)
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) {
                mutableState.value = state.value.copy(result = R.string.backup_failed)
            }
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
    fun deleteArchive(file: StorageFile) {
        if (state.value.busy) return
        mutableState.value = state.value.copy(busy = true, result = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val archives = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    PresentationAccess.storage.operationMutex.withLock {
                        check(PresentationAccess.autoBackup.deleteArchive(file))
                        PresentationAccess.autoBackup.archives()
                    }
                }
                mutableState.value = state.value.copy(busy = false, result = R.string.backup_deleted, archives = archives)
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) {
                mutableState.value = state.value.copy(busy = false, result = R.string.backup_delete_failed)
                refreshArchives()
            }
        }
    }
    fun exportArchive(context: Context, file: StorageFile, uri: Uri) {
        if (state.value.busy) return
        mutableState.value = state.value.copy(busy = true, result = null)
        viewModelScope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    PresentationAccess.storage.operationMutex.withLock {
                        val transferContext = currentCoroutineContext()
                        file.inputStream().use { input ->
                            checkNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { output ->
                                input.copyWithCancellation(output) { transferContext.ensureActive() }
                            }
                        }
                    }
                }
                mutableState.value = state.value.copy(busy = false, result = R.string.backup_done)
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) {
                mutableState.value = state.value.copy(busy = false, result = R.string.backup_failed)
            }
        }
    }
    fun run(context: Context, uri: Uri?, scope: String, categories: Set<BackupCategory>, restore: Boolean, archive: StorageFile? = null) {
        if (state.value.busy) return
        mutableState.value = state.value.copy(busy = true, result = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                PresentationAccess.storage.operationMutex.withLock {
                    val document = archive?.uri ?: requireNotNull(uri)
                    if (restore) LocalBackup.restore(context, document, PresentationAccess.database, scope, categories)
                    else LocalBackup.export(context, document, PresentationAccess.database, scope, categories)
                }
                mutableState.value = state.value.copy(busy = false,
                    result = if (restore) R.string.backup_import_done else R.string.backup_done)
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
        val intervalHours by PresentationAccess.autoBackup.intervalHours.collectAsStateWithLifecycle()
        val backupFailed by PresentationAccess.autoBackup.failed.collectAsStateWithLifecycle()
        val storageLocation by PresentationAccess.storage.location.collectAsStateWithLifecycle()
        val autoBackupDownloads by PresentationAccess.autoBackup.includeDownloads.collectAsStateWithLifecycle()
        val snackbarHost = remember { SnackbarHostState() }
        val resultMessage = state.result?.let { stringResource(it) }
        LaunchedEffect(state.result) {
            if (resultMessage != null) {
                snackbarHost.showSnackbar(resultMessage)
                model.dismissResult()
            }
        }
        BackHandler(enabled = state.busy) { }
        LaunchedEffect(completedAt, storageLocation) { model.refreshArchives() }
        var pendingArchive by rememberSaveable { mutableStateOf<String?>(null) }
        var restoreArchive by rememberSaveable { mutableStateOf<String?>(null) }
        var deleteArchive by rememberSaveable { mutableStateOf<String?>(null) }
        val exportArchive = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            val path = pendingArchive
            if (uri != null && path != null) state.archives.firstOrNull { it.absolutePath == path }?.let { model.exportArchive(context, it, uri) }
            pendingArchive = null
        }
        var selectedNames by rememberSaveable {
            mutableStateOf(BackupCategory.entries.filter { it != BackupCategory.DOWNLOADS }.map { it.name })
        }
        var pendingNames by rememberSaveable { mutableStateOf(emptyList<String>()) }
        var pendingScope by rememberSaveable { mutableStateOf("") }
        var confirmImport by rememberSaveable { mutableStateOf(false) }
        val selectedLabels = BackupCategory.entries.filter { it.name in selectedNames }
            .map { backupCategoryLabel(it) }.joinToString("、")
        val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri != null) model.run(context, uri, pendingScope, pendingNames.map { BackupCategory.valueOf(it) }.toSet(), false)
        }
        val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) model.run(context, uri, pendingScope, pendingNames.map { BackupCategory.valueOf(it) }.toSet(), true)
        }
        Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }, topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_backup), style = AppTypography.titleMedium) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background), navigationIcon = {
            IconButton(enabled = !state.busy, onClick = { navigator?.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.reading_stats_back)) }
        }) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).accountContentWidth().verticalScroll(rememberScrollState())
                .padding(start = AppSpacing.lg, end = AppSpacing.lg, top = AppSpacing.sm, bottom = AppSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
                if (backupFailed) Text(stringResource(R.string.backup_failed), color = MaterialTheme.colorScheme.error)
                SettingsSection(title = stringResource(R.string.auto_backup_section)) {
                    BackupToggleRow(stringResource(R.string.auto_backup), autoBackupEnabled, !state.busy) {
                        model.setAutoBackup(it, scope)
                    }
                    ChoiceSettingRow(
                        title = stringResource(R.string.auto_backup_interval),
                        options = AutoBackup.INTERVAL_HOURS.map { stringResource(R.string.backup_interval_hours, it) },
                        selectedIndex = AutoBackup.INTERVAL_HOURS.indexOf(intervalHours),
                        enabled = !state.busy,
                        onSelect = { model.setIntervalHours(AutoBackup.INTERVAL_HOURS[it]) }
                    )
                    BackupToggleRow(
                        title = stringResource(R.string.backup_include_downloads),
                        checked = autoBackupDownloads,
                        enabled = !state.busy,
                        subtitle = stringResource(R.string.backup_include_downloads_warning),
                        onChange = model::setAutoBackupDownloads
                    )
                }
                SettingsSection(title = stringResource(R.string.manual_backup_section)) {
                    BackupCategory.entries.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth()) {
                            pair.forEach { category ->
                                val checked = category.name in selectedNames
                                Row(
                                    Modifier.weight(1f).heightIn(min = 48.dp)
                                        .toggleable(value = checked, enabled = !state.busy, role = Role.Checkbox) {
                                            selectedNames = if (it) selectedNames + category.name else selectedNames - category.name
                                        }.padding(horizontal = AppSpacing.sm),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(checked = checked, enabled = !state.busy, onCheckedChange = null)
                                    Text(backupCategoryLabel(category), style = AppTypography.bodyMedium,
                                        modifier = Modifier.padding(start = AppSpacing.sm))
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(AppSpacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        OutlinedButton(modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = AppShapes.compact,
                            enabled = !state.busy && selectedNames.isNotEmpty(), onClick = { confirmImport = true }) {
                            Text(stringResource(R.string.backup_import))
                        }
                        Button(modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = AppShapes.compact,
                            enabled = !state.busy && selectedNames.isNotEmpty(), onClick = {
                                pendingNames = selectedNames; pendingScope = scope
                                create.launch(BackupFileNames.manual())
                            }) { Text(stringResource(R.string.backup_export)) }
                    }
                }
                SettingsSection(title = stringResource(R.string.backup_archives)) {
                    if (state.archives.isEmpty()) Text(stringResource(R.string.auto_backup_empty),
                        modifier = Modifier.padding(AppSpacing.md), style = AppTypography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.archives.forEach { file ->
                        key(file.absolutePath) {
                            var menu by remember { mutableStateOf(false) }
                            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = AppSpacing.md),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,
                                    java.text.DateFormat.SHORT).format(java.util.Date(
                                    BackupFileNames.automaticTime(file.name) ?: 0L)),
                                    style = AppTypography.bodyMedium, modifier = Modifier.weight(1f))
                                Box {
                                    IconButton(enabled = !state.busy, onClick = { menu = true }) {
                                        Icon(Icons.Filled.MoreVert, stringResource(R.string.backup_archive_actions))
                                    }
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        DropdownMenuItem(text = { Text(stringResource(R.string.backup_import)) },
                                            enabled = selectedNames.isNotEmpty(), onClick = {
                                                menu = false; restoreArchive = file.absolutePath
                                            })
                                        DropdownMenuItem(text = { Text(stringResource(R.string.backup_export)) }, onClick = {
                                            menu = false; pendingArchive = file.absolutePath
                                            exportArchive.launch(BackupFileNames.automatic(requireNotNull(BackupFileNames.automaticTime(file.name))))
                                        })
                                        DropdownMenuItem(text = { Text(stringResource(R.string.backup_delete),
                                            color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                            onClick = { menu = false; deleteArchive = file.absolutePath })
                                    }
                                }
                            }
                        }
                    }
                }
                if (state.busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.backup_working), style = AppTypography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

            }
        }
        if (deleteArchive != null) AlertDialog(onDismissRequest = { deleteArchive = null },
            title = { Text(stringResource(R.string.backup_delete)) },
            text = { Text(stringResource(R.string.backup_delete_confirm)) },
            confirmButton = { TextButton(enabled = !state.busy, onClick = {
                val path = deleteArchive
                deleteArchive = null
                if (path != null) state.archives.firstOrNull { it.absolutePath == path }?.let(model::deleteArchive)
            }) { Text(stringResource(R.string.backup_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteArchive = null }) { Text(stringResource(android.R.string.cancel)) } })
        if (restoreArchive != null) AlertDialog(onDismissRequest = { restoreArchive = null },
            title = { Text(stringResource(R.string.backup_import)) },
            text = { Text(stringResource(R.string.backup_overwrite_confirm) + "\n\n" +
                selectedLabels) },
            confirmButton = { TextButton(onClick = {
                val path = restoreArchive
                restoreArchive = null
                if (path != null) state.archives.firstOrNull { it.absolutePath == path }?.let { file ->
                    model.run(context, null, scope, selectedNames.map { BackupCategory.valueOf(it) }.toSet(), true, file)
                }
            }, enabled = selectedNames.isNotEmpty()) { Text(stringResource(android.R.string.ok)) } },
            dismissButton = { TextButton(onClick = { restoreArchive = null }) { Text(stringResource(android.R.string.cancel)) } })
        if (confirmImport) AlertDialog(onDismissRequest = { confirmImport = false },
            title = { Text(stringResource(R.string.backup_import)) },
            text = { Text(stringResource(R.string.backup_overwrite_confirm) + "\n\n" +
                selectedLabels) },
            confirmButton = { TextButton(onClick = {
                confirmImport = false; pendingNames = selectedNames; pendingScope = scope
                openBackup.launch(arrayOf("application/zip", "application/octet-stream"))
            }) { Text(stringResource(android.R.string.ok)) } },
            dismissButton = { TextButton(onClick = { confirmImport = false }) { Text(stringResource(android.R.string.cancel)) } })
    }
}

@Composable
private fun backupCategoryLabel(category: BackupCategory): String = stringResource(when (category) {
    BackupCategory.UNDERLINES -> R.string.reader_underlines
    BackupCategory.BOOKMARKS -> R.string.bookmarks
    BackupCategory.DOWNLOADS -> R.string.downloads
    BackupCategory.READING -> R.string.backup_reading
    BackupCategory.HISTORY -> R.string.backup_history
    BackupCategory.SEARCH -> R.string.backup_search
    BackupCategory.GROUPS -> R.string.shelf_groups
})

@Composable
private fun BackupToggleRow(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    subtitle: String? = null,
    onChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
        .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
        .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = AppTypography.bodyLarge)
            subtitle?.let {
                Text(it, style = AppTypography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = null)
    }
}
