package com.breakyuna.esjzone.update

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.breakyuna.esjzone.ui.designsystem.GlobalToast as Toast
import androidx.compose.material3.AlertDialog
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import com.breakyuna.esjzone.BuildConfig
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppSpacing

@Composable
internal fun ReleaseUpdateDialog() {
    val update by ReleaseUpdateChecker.update.collectAsState()
    val release = update ?: return
    val context = LocalContext.current
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
                if (release.description.isNotBlank()) {
                    Text(stringResource(R.string.update_release_notes))
                    ReleaseNotesText(release.description)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl))
                            .addCategory(Intent.CATEGORY_BROWSABLE)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    ReleaseUpdateChecker.dismiss()
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.update_browser_unavailable, Toast.LENGTH_SHORT).show()
                } catch (_: SecurityException) {
                    Toast.makeText(context, R.string.update_browser_unavailable, Toast.LENGTH_SHORT).show()
                }
            }) { Text(stringResource(R.string.update_download)) }
        },
        dismissButton = {
            TextButton(onClick = ReleaseUpdateChecker::dismiss) { Text(stringResource(R.string.update_later)) }
        }
    )
}
