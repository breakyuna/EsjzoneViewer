@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.breakyuna.esjzone.ui.page

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.breakyuna.esjzone.storage.StorageTransferProgress
import com.breakyuna.esjzone.storage.StorageTransferPhase
import com.breakyuna.esjzone.storage.message
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.storage.StorageAccessException
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import com.breakyuna.esjzone.ui.navigation.rememberAppViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class StorageLocationModel : AppStateViewModel<StorageLocationModel.Status>(Status()) {
    data class Status(val busy: Boolean = false, val result: Int? = null)
    fun select(uri: Uri, migrate: Boolean) = perform {
        PresentationAccess.storage.select(uri, migrate)
        if (PresentationAccess.storage.transfer.value.running) R.string.storage_transfer_started
        else R.string.storage_location_done
    }
    fun moveToPrivate() = perform {
        PresentationAccess.storage.moveToPrivate()
        R.string.storage_transfer_started
    }
    fun retryTransfer() = perform {
        PresentationAccess.storage.retryTransfer()
        R.string.storage_transfer_started
    }
    fun reindex() = perform {
        PresentationAccess.storage.reindex()
        R.string.storage_reindex_done
    }
    fun dismissResult() { mutableState.value = state.value.copy(result = null) }
    private fun perform(action: suspend () -> Int) {
        if (state.value.busy) return
        mutableState.value = Status(busy = true)
        viewModelScope.launch {
            try { mutableState.value = Status(result = action())
            } catch (error: CancellationException) { throw error
            } catch (_: StorageAccessException) { mutableState.value = Status(result = R.string.storage_access_failed)
            } catch (_: Exception) { mutableState.value = Status(result = R.string.storage_location_failed) }
        }
    }
}

/** Inline directory selection shared by the settings entry. */
@Composable
internal fun StorageLocationSettingRow() {
    val model = rememberAppViewModel { StorageLocationModel() }
    val state by model.state.collectAsStateWithLifecycle()
    val transfer by PresentationAccess.storage.transfer.collectAsStateWithLifecycle()
    val location by PresentationAccess.storage.location.collectAsStateWithLifecycle()
    val folderName by PresentationAccess.storage.label.collectAsStateWithLifecycle()
    var chooseLocation by rememberSaveable { mutableStateOf(false) }
    var confirmPrivate by rememberSaveable { mutableStateOf(false) }
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var migrate by rememberSaveable { mutableStateOf(true) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) { pending = uri.toString(); migrate = true }
    }
    val path = remember(location, folderName) {
        if (location.isBlank() || location == com.breakyuna.esjzone.storage.UserStorage.PRIVATE_LOCATION) null else runCatching {
            val uri = Uri.parse(location)
            val id = android.provider.DocumentsContract.getTreeDocumentId(uri)
            if (uri.authority == "com.android.externalstorage.documents" && id.startsWith("primary:")) {
                android.os.Environment.getExternalStorageDirectory().absolutePath + "/" + id.removePrefix("primary:")
            } else {
                id.replace(":", ":/")
            }
        }.getOrElse { folderName.ifBlank { Uri.decode(location) } }
    }
    BackHandler(enabled = state.busy) { }
    LinkRow(
        icon = Icons.Filled.FolderOpen,
        title = stringResource(R.string.storage_location),
        subtitle = path ?: stringResource(R.string.storage_location_private),
        enabled = !state.busy && !transfer.running
    ) { chooseLocation = true }
    if (chooseLocation) {
        AlertDialog(
            onDismissRequest = { chooseLocation = false },
            title = { Text(stringResource(R.string.storage_location)) },
            text = {
                Column {
                    TextButton(onClick = {
                        chooseLocation = false
                        picker.launch(location.takeIf { it.isNotBlank() &&
                            it != com.breakyuna.esjzone.storage.UserStorage.PRIVATE_LOCATION }?.let(Uri::parse))
                    }) { Text(stringResource(R.string.storage_location_choose)) }
                    if (path != null) TextButton(onClick = {
                        chooseLocation = false
                        confirmPrivate = true
                    }) { Text(stringResource(R.string.storage_move_private)) }
                }
            },
            confirmButton = { TextButton(onClick = { chooseLocation = false }) {
                Text(stringResource(android.R.string.cancel))
            } }
        )
    }
    if (confirmPrivate) {
        AlertDialog(
            onDismissRequest = { confirmPrivate = false },
            title = { Text(stringResource(R.string.storage_move_private)) },
            text = { Text(stringResource(R.string.storage_move_private_confirm)) },
            confirmButton = { TextButton(onClick = {
                confirmPrivate = false
                model.moveToPrivate()
            }) { Text(stringResource(android.R.string.ok)) } },
            dismissButton = { TextButton(onClick = { confirmPrivate = false }) {
                Text(stringResource(android.R.string.cancel))
            } }
        )
    }
    StorageTransferStatus(transfer, onRetry = model::retryTransfer)
    if (state.busy || state.result != null) {
        AlertDialog(
            onDismissRequest = { if (!state.busy) model.dismissResult() },
            title = { Text(stringResource(R.string.storage_location)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(if (state.busy) R.string.storage_location_working else state.result!!))
                }
            },
            confirmButton = {
                if (!state.busy) TextButton(onClick = model::dismissResult) { Text(stringResource(android.R.string.ok)) }
            }
        )
    }
    pending?.let { selected ->
        AlertDialog(onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.storage_location)) },
            text = {
                Column {
                    Text(stringResource(R.string.storage_location_confirm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = migrate, onCheckedChange = { migrate = it })
                        Text(stringResource(R.string.storage_location_migrate))
                    }
                }
            },
            confirmButton = { TextButton(onClick = {
                pending = null
                model.select(Uri.parse(selected), migrate)
            }) { Text(stringResource(android.R.string.ok)) } },
            dismissButton = { TextButton(onClick = { pending = null }) {
                Text(stringResource(android.R.string.cancel))
            } })
    }
}

/** Shared progress in settings, onboarding and downloads; Activity recreation cannot lose it. */
@Composable
internal fun StorageTransferStatus(progress: StorageTransferProgress, onRetry: () -> Unit) {
    if (progress.phase == StorageTransferPhase.IDLE) return
    Column(Modifier.fillMaxWidth().padding(vertical = AppSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        if (progress.running) {
            if (progress.total > 0) {
                LinearProgressIndicator(progress = { progress.completed.toFloat() / progress.total },
                    modifier = Modifier.fillMaxWidth())
            } else LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        val failed = progress.phase == StorageTransferPhase.FAILED || progress.phase == StorageTransferPhase.CLEANUP_FAILED
        Text(progress.message(LocalContext.current),
            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        if (failed) TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}
