package com.breakyuna.esjzone.ui.page

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.breakyuna.esjzone.EsjzoneApplication
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.offline.NovelExportController
import com.breakyuna.esjzone.offline.NovelExportOutcome
import com.breakyuna.esjzone.offline.NovelExportTarget
import com.breakyuna.esjzone.util.AppLogger
import com.breakyuna.esjzone.util.LocaleHelper
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.breakyuna.esjzone.ui.designsystem.GlobalToast as Toast

/** Keeps TXT and EPUB exports running across navigation within the app process. */
internal object NovelExport {
    private const val TAG = "NovelExport"
    private val appContext: Context
        get() = EsjzoneApplication.instance
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val controller = NovelExportController(
        scope = scope,
        ioDispatcher = Dispatchers.IO,
        onOutcome = { report(it, currentLabel) }
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
    fun discard(uri: Uri, label: String) {
        scope.launch {
            val removed = withContext(Dispatchers.IO) {
                SafExportTarget(appContext.contentResolver, uri).delete()
            }
            report(NovelExportOutcome.Failed(removed, IllegalStateException("Novel details unavailable")), label)
        }
    }

    @Volatile
    private var currentLabel: String = ""

    private fun report(outcome: NovelExportOutcome, label: String) {
        val message = when (outcome) {
            NovelExportOutcome.Success -> R.string.novel_export_success
            is NovelExportOutcome.Failed -> {
                AppLogger.e(TAG, "Failed to export $label", outcome.error)
                if (outcome.removed) R.string.novel_export_failed_removed
                else R.string.novel_export_failed_leftover
            }
            is NovelExportOutcome.Cancelled -> {
                AppLogger.w(TAG, "Export of $label cancelled; removed=${outcome.removed}")
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
