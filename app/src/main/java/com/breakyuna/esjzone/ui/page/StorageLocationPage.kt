@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.breakyuna.esjzone.ui.page

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.storage.StorageAccessException
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.accountContentWidth
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import com.breakyuna.esjzone.ui.navigation.AppDestination
import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.rememberAppViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class StorageLocationModel : AppStateViewModel<StorageLocationModel.Status>(Status()) {
    data class Status(val busy: Boolean = false, val result: Int? = null)
    fun select(uri: Uri, migrate: Boolean) = perform {
        if (PresentationAccess.storage.select(uri, migrate)) R.string.storage_location_done
        else R.string.storage_location_cleanup_failed
    }
    fun reindex() = perform {
        PresentationAccess.storage.reindex()
        R.string.storage_reindex_done
    }
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

object StorageLocationPage : AppDestination {
    override val key = "StorageLocationPage"
    private fun readResolve(): Any = StorageLocationPage
    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val model = rememberAppViewModel { StorageLocationModel() }
        val state by model.state.collectAsStateWithLifecycle()
        val location by PresentationAccess.storage.location.collectAsStateWithLifecycle()
        val folderName by PresentationAccess.storage.label.collectAsStateWithLifecycle()
        var pending by rememberSaveable { mutableStateOf<String?>(null) }
        var migrate by rememberSaveable { mutableStateOf(true) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) { pending = uri.toString(); migrate = true }
        }
        BackHandler(enabled = state.busy) { }
        Scaffold(topBar = {
            TopAppBar(title = { Text(stringResource(R.string.storage_location)) }, navigationIcon = {
                IconButton(enabled = !state.busy, onClick = { navigator?.pop() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.reading_stats_back))
                }
            })
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).accountContentWidth().padding(AppSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                Text(if (location.isBlank()) stringResource(R.string.storage_location_private)
                    else folderName)
                Button(enabled = !state.busy, onClick = {
                    picker.launch(location.takeIf(String::isNotBlank)?.let(Uri::parse))
                }) { Text(stringResource(R.string.storage_location_choose)) }
                OutlinedButton(enabled = !state.busy, onClick = model::reindex) {
                    Text(stringResource(R.string.storage_reindex))
                }
                if (state.busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.storage_location_working))
                }
                state.result?.let { Text(stringResource(it)) }
            }
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
}
