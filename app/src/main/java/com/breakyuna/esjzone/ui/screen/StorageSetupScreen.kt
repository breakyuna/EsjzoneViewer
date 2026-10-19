package com.breakyuna.esjzone.ui.screen

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTouchTarget
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.designsystem.accountContentWidth
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import com.breakyuna.esjzone.ui.page.StorageLocationModel

internal fun canContinueStorageSetup(location: String, state: StorageLocationModel.Status): Boolean =
    location.isNotBlank() && !state.busy &&
        (state.result == null || state.result == R.string.storage_location_done ||
            state.result == R.string.storage_location_cleanup_failed)

/** The root gate keeps underlying destinations inactive until the user continues. */
@Composable
fun StorageSetupScreen(
    model: StorageLocationModel,
    state: StorageLocationModel.Status,
    isUpgrade: Boolean?,
    onContinue: () -> Unit
) {
    val location by PresentationAccess.storage.location.collectAsStateWithLifecycle()
    val folderName by PresentationAccess.storage.label.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) model.select(uri, migrate = true)
    }
    BackHandler { }
    if (isUpgrade == null) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier.weight(1f).accountContentWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
        ) {
            Icon(
                Icons.Filled.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(AppSpacing.xxxl)
            )
            Text(
                stringResource(if (isUpgrade) R.string.storage_setup_upgrade_title else R.string.storage_setup_title),
                style = AppTypography.displayLarge
            )
            Text(
                stringResource(if (isUpgrade) R.string.storage_setup_upgrade_intro else R.string.storage_setup_intro),
                style = AppTypography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.standard,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(Modifier.padding(AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
                    Text(stringResource(if (isUpgrade) R.string.storage_setup_upgrade_message else R.string.storage_setup_message))
                    Text(stringResource(
                        R.string.storage_setup_selected,
                        if (location.isBlank()) stringResource(R.string.storage_setup_unselected)
                        else folderName.ifBlank { location }
                    ))
                    Button(
                        enabled = !state.busy,
                        onClick = { picker.launch(null) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = AppTouchTarget.minimum)
                    ) { Text(stringResource(R.string.storage_location_choose)) }
                    if (state.busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(stringResource(R.string.storage_location_working))
                    } else state.result?.let {
                        Text(
                            stringResource(it),
                            color = if (it == R.string.storage_access_failed || it == R.string.storage_location_failed)
                                MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        HorizontalDivider()
        Button(
            enabled = canContinueStorageSetup(location, state),
            onClick = onContinue,
            modifier = Modifier.accountContentWidth().padding(AppSpacing.lg)
                .heightIn(min = AppTouchTarget.minimum)
        ) { Text(stringResource(R.string.storage_setup_next)) }
    }
}
