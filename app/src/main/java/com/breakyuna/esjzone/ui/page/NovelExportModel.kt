package com.breakyuna.esjzone.ui.page

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.breakyuna.esjzone.ui.designsystem.GlobalToast as Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.offline.NovelExportController
import com.breakyuna.esjzone.offline.NovelExportOutcome
import com.breakyuna.esjzone.offline.NovelExportTarget
import com.breakyuna.esjzone.util.AppLogger
import com.breakyuna.esjzone.util.LocaleHelper
import com.breakyuna.esjzone.app.PresentationAccess
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns TXT/EPUB export for one novel page.
 *
 * ViewModel scope instead of WorkManager: the SAF grant from `CreateDocument`
 * is a temporary, Activity-bound permission that a worker cannot rely on after
 * process death, and exports take seconds, not minutes. The ViewModel survives
 * rotation; leaving the page clears it, which cancels the export and removes
 * the partial document.
 */
class NovelExportModel(context: Context) : ViewModel() {
    private val appContext = context.applicationContext

    private val controller = NovelExportController(
        scope = viewModelScope,
        ioDispatcher = Dispatchers.IO,
        onOutcome = ::report
    )

    val isExporting: StateFlow<Boolean> = controller.isExporting

    fun start(
        uri: Uri,
        label: String,
        write: (output: OutputStream, checkActive: () -> Unit) -> Unit
    ) {
        if (!controller.isExporting.value) currentLabel = label
        controller.start(SafExportTarget(appContext.contentResolver, uri), write)
    }

    /** The picker created [uri] but there is nothing to export into it any more. */
    fun discard(uri: Uri) {
        viewModelScope.launch {
            val removed = withContext(Dispatchers.IO) {
                SafExportTarget(appContext.contentResolver, uri).delete()
            }
            report(NovelExportOutcome.Failed(removed, IllegalStateException("Novel details unavailable")))
        }
    }

    @Volatile
    private var currentLabel: String = ""

    private fun report(outcome: NovelExportOutcome) {
        val message = when (outcome) {
            NovelExportOutcome.Success -> R.string.novel_export_success
            is NovelExportOutcome.Failed -> {
                AppLogger.e(TAG, "Failed to export $currentLabel", outcome.error)
                if (outcome.removed) R.string.novel_export_failed_removed
                else R.string.novel_export_failed_leftover
            }
            is NovelExportOutcome.Cancelled -> {
                AppLogger.w(TAG, "Export of $currentLabel cancelled; removed=${outcome.removed}")
                if (outcome.removed) R.string.novel_export_cancelled_removed
                else R.string.novel_export_cancelled_leftover
            }
            is NovelExportOutcome.Busy -> R.string.novel_export_busy
        }
        Toast.makeText(localizedContext(), message, Toast.LENGTH_SHORT).show()
    }

    /**
     * The application Context carries the system locale; on Android 10-12 the
     * in-app language only exists in the Activity's localized Context, so
     * rebuild it the same way MainActivity does before resolving the message.
     */
    private fun localizedContext(): Context = runCatching {
        LocaleHelper.createLocalizedContext(appContext, PresentationAccess.settings.language.value)
    }.getOrElse { appContext }

    private companion object {
        const val TAG = "NovelExport"
    }
}

/** SAF document picked by `ActivityResultContracts.CreateDocument`. */
internal class SafExportTarget(
    private val resolver: ContentResolver,
    private val uri: Uri
) : NovelExportTarget {
    override fun openOutputStream(mode: String): OutputStream? = resolver.openOutputStream(uri, mode)

    override fun delete(): Boolean = try {
        DocumentsContract.deleteDocument(resolver, uri)
    } catch (error: Exception) {
        // Provider without FLAG_SUPPORTS_DELETE, revoked grant, or already gone.
        AppLogger.w("NovelExport", "Unable to delete partial export $uri", error)
        false
    }
}
