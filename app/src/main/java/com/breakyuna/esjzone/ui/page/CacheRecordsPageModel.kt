package com.breakyuna.esjzone.ui.page

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.network.CacheAnalysis
import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class CacheRecordsPageModel : AppStateViewModel<CacheRecordsPageModel.State>(State()) {
    data class State(val busy: Boolean = false, val failed: Boolean = false, val exported: Boolean = false)

    init { CacheAnalysis.requestCheckpoint() }

    fun saveLimit(value: Int) = perform {
        PresentationAccess.setCacheLimitMiB(value)
        if (!CacheAnalysis.checkpoint()) throw IOException("Cache analysis could not be saved")
    }

    fun export(context: Context, uri: Uri) = perform(exporting = true) {
        requireNotNull(context.contentResolver.openOutputStream(uri)).use { CacheAnalysis.export(it) }
    }

    private fun perform(exporting: Boolean = false, action: suspend () -> Unit) {
        if (mutableState.value.busy) return
        mutableState.value = State(busy = true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                action()
                mutableState.value = State(exported = exporting)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.value = State(failed = true)
            } finally {
                mutableState.value = mutableState.value.copy(busy = false)
            }
        }
    }
}
