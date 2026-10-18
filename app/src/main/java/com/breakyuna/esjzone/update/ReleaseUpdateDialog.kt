package com.breakyuna.esjzone.update

import android.content.ActivityNotFoundException
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.breakyuna.esjzone.ui.designsystem.GlobalToast as Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import com.breakyuna.esjzone.BuildConfig
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.util.AppLogger
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppSpacing

@Composable
internal fun ReleaseUpdateDialog() {
    val update by ReleaseUpdateChecker.update.collectAsState()
    val release = update ?: return
    val visible by ReleaseUpdateChecker.dialogVisible.collectAsState()
    val context = LocalContext.current
    val activity = LocalActivity.current ?: return
    val download by release.downloader.state.collectAsStateWithLifecycle()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    var autoInstallRequested by rememberSaveable(release.version) { mutableStateOf(false) }
    var installError by rememberSaveable(release.version) { mutableStateOf<Int?>(null) }
    val notificationsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Notification permission affects visibility of notifications, not permission to download.
        ReleaseUpdateChecker.startDownload(context)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) {
            // Launch only after the Activity has resumed, including after recreation in Settings.
            autoInstallRequested = false
            installError = null
        } else {
            installError = R.string.update_install_permission_required
        }
    }
    val installUpdate: () -> Unit = {
        val ready = download as? ReleaseDownloadState.Ready
        installError = null
        if (ready != null) {
            if (!ready.file.isFile) {
                release.downloader.discardMissingFile()
            } else {
                try {
                    if (!context.packageManager.canRequestPackageInstalls()) {
                        permissionLauncher.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.fromParts("package", context.packageName, null)))
                    } else {
                        val uri = FileProvider.getUriForFile(context,
                            "${context.packageName}.app-updates", ready.file)
                        activity.startActivity(Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    }
                } catch (error: ActivityNotFoundException) {
                    AppLogger.e("ReleaseUpdateDialog", "Update installation activity is unavailable", error)
                    installError = R.string.update_installer_unavailable
                } catch (error: SecurityException) {
                    AppLogger.e("ReleaseUpdateDialog", "Update installation launch was denied", error)
                    installError = R.string.update_installer_unavailable
                } catch (error: IllegalArgumentException) {
                    AppLogger.e("ReleaseUpdateDialog", "Invalid update installation request", error)
                    installError = R.string.update_installer_unavailable
                }
            }
        }
    }
    LaunchedEffect(download, lifecycleState, autoInstallRequested) {
        if (download is ReleaseDownloadState.Ready && !autoInstallRequested &&
            lifecycleState == Lifecycle.State.RESUMED) {
            ReleaseUpdateChecker.showDialog()
            autoInstallRequested = true
            installUpdate()
        }
    }
    val openReleasePage: () -> Unit = {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl))
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.update_browser_unavailable, Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, R.string.update_browser_unavailable, Toast.LENGTH_SHORT).show()
        }
    }
    if (!visible) return
    AlertDialog(
        onDismissRequest = ReleaseUpdateChecker::dismiss,
        shape = AppShapes.prominent,
        title = { Text(stringResource(R.string.update_available_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
            ) {
                Text(stringResource(R.string.update_available_message, BuildConfig.VERSION_NAME, release.version))
                when (val state = download) {
                    is ReleaseDownloadState.Downloading -> {
                        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.update_download_progress, (state.progress * 100).toInt()))
                    }
                    ReleaseDownloadState.Failed -> Text(stringResource(R.string.update_download_failed),
                        color = MaterialTheme.colorScheme.error)
                    is ReleaseDownloadState.Ready -> Text(stringResource(R.string.update_download_complete))
                    ReleaseDownloadState.Idle -> if (release.apk == null) {
                        Text(stringResource(R.string.update_apk_unavailable))
                    }
                }
                installError?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                if (release.description.isNotBlank()) {
                    Text(stringResource(R.string.update_release_notes))
                    ReleaseNotesText(release.description)
                }
            }
        },
        confirmButton = {
            when {
                download is ReleaseDownloadState.Downloading -> Unit
                download is ReleaseDownloadState.Ready -> TextButton(onClick = installUpdate) {
                    Text(stringResource(R.string.update_install))
                }
                release.apk != null -> TextButton(onClick = {
                    autoInstallRequested = false
                    installError = null
                    // Keep the release available while the notification permission result is pending.
                    ReleaseUpdateChecker.hideDialog()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        ReleaseUpdateChecker.startDownload(context)
                    }
                }) { Text(stringResource(R.string.update_download)) }
                else -> TextButton(onClick = openReleasePage) { Text(stringResource(R.string.update_open_release)) }
            }
        },
        dismissButton = {
            if (download is ReleaseDownloadState.Downloading) {
                TextButton(onClick = { ReleaseUpdateChecker.cancelDownload(context) }) {
                    Text(stringResource(R.string.update_cancel_download))
                }
            } else {
                TextButton(onClick = ReleaseUpdateChecker::dismiss) { Text(stringResource(R.string.update_later)) }
            }
        }
    )
}
