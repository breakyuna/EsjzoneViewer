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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
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
    data class Status(val busy: Boolean = false, val result: Int? = null)
    fun run(context: Context, uri: Uri, scope: String, categories: Set<BackupCategory>, restore: Boolean) {
        if (state.value.busy) return
        mutableState.value = Status(busy = true)
        viewModelScope.launch {
            try {
                if (restore) LocalBackup.restore(context, uri, PresentationAccess.database, scope, categories)
                else LocalBackup.export(context, uri, PresentationAccess.database, scope, categories)
                mutableState.value = Status(result = R.string.backup_done)
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) {
                mutableState.value = Status(result = R.string.backup_failed)
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
        var selectedNames by rememberSaveable { mutableStateOf(BackupCategory.entries.map { it.name }) }
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
                    BackupCategory.entries.forEach { category ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = AppSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = category.name in selectedNames, enabled = !state.busy, onCheckedChange = { checked ->
                                selectedNames = if (checked) selectedNames + category.name else selectedNames - category.name
                            })
                            Text(stringResource(when (category) {
                                BackupCategory.BOOKMARKS -> R.string.bookmarks
                                BackupCategory.DOWNLOADS -> R.string.downloads
                                BackupCategory.READING -> R.string.backup_reading
                                BackupCategory.HISTORY -> R.string.backup_history
                                BackupCategory.SEARCH -> R.string.backup_search
                                BackupCategory.GROUPS -> R.string.shelf_groups
                            }), style = AppTypography.bodyMedium, modifier = Modifier.padding(end = AppSpacing.md))
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
