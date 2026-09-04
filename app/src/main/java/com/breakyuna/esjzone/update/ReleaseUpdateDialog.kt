package com.breakyuna.esjzone.update

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.breakyuna.esjzone.BuildConfig
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.ui.theme.QuietEditorial

@Composable
internal fun ReleaseUpdateDialog() {
    val update by ReleaseUpdateChecker.update.collectAsState()
    val release = update ?: return
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = ReleaseUpdateChecker::dismiss,
        shape = QuietEditorial.dialogShape,
        title = { Text(stringResource(R.string.update_available_title)) },
        text = { Text(stringResource(R.string.update_available_message, BuildConfig.VERSION_NAME, release.version)) },
        confirmButton = {
            TextButton(onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl)))
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
