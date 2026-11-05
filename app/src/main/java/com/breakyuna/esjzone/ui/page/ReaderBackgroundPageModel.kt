package com.breakyuna.esjzone.ui.page

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import com.breakyuna.esjzone.ui.reader.ReaderImageBackground
import com.breakyuna.esjzone.ui.reader.ReaderImageBackgroundStore
import com.breakyuna.esjzone.util.AppLogger
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal class ReaderBackgroundPageModel(context: Context) :
    AppStateViewModel<ReaderBackgroundPageModel.State>(State()) {
    data class State(
        val source: File? = null,
        val draft: ReaderImageBackground = ReaderImageBackground(),
        val preview: Bitmap? = null,
        val previewBlur: Float? = null,
        val loading: Boolean = true,
        val previewBusy: Boolean = false,
        val saving: Boolean = false,
        val saved: Boolean = false,
        val error: Int? = null
    ) {
        val canApply: Boolean get() = source != null && preview != null && previewBlur == draft.blur &&
            !loading && !previewBusy && !saving
    }

    private val appContext = context.applicationContext
    private var previewJob: Job? = null
    private var saveJob: Job? = null

    private val initialLoad = viewModelScope.launch {
        try {
            val saved = PresentationAccess.readerSettings.readImageBackground()
            val source = withContext(Dispatchers.IO) {
                ReaderImageBackgroundStore.file(appContext, saved)?.takeIf { it.isFile }
            }
            mutableState.value = State(source = source, draft = saved, loading = false)
            refreshPreview()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            mutableState.value = State(loading = false, error = R.string.reader_background_image_failed)
        }
    }

    fun chooseImage(uri: Uri) {
        viewModelScope.launch {
            // A restored picker result can arrive before the saved preferences have loaded.
            initialLoad.join()
            if (state.value.loading || state.value.saving) return@launch
            previewJob?.cancel()
            mutableState.value = state.value.copy(loading = true, previewBusy = false, error = null)
            try {
                val previousSource = state.value.source
                val source = ReaderImageBackgroundStore.importImage(appContext, uri)
                mutableState.value = state.value.copy(source = source, loading = false, preview = null, previewBlur = null)
                refreshPreview()
                withContext(NonCancellable + Dispatchers.IO) {
                    ReaderImageBackgroundStore.deleteDraft(appContext, previousSource)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.w("ReaderBackground", "Unable to import background image", error)
                mutableState.value = state.value.copy(loading = false, error = R.string.reader_background_image_failed)
                refreshPreview()
            }
        }
    }

    fun setBlur(value: Float) {
        if (state.value.loading || state.value.saving) return
        val blur = value.roundToInt().coerceIn(0, 100).toFloat()
        if (blur == state.value.draft.blur) return
        mutableState.value = state.value.copy(draft = state.value.draft.copy(blur = blur))
        refreshPreview()
    }

    fun setTextColor(color: Long) {
        if (state.value.loading || state.value.saving) return
        mutableState.value = state.value.copy(draft = state.value.draft.copy(textColor = color).sanitized())
    }

    private fun refreshPreview() {
        if (previewJob?.isActive == true) return
        val source = state.value.source ?: return
        mutableState.value = state.value.copy(previewBusy = true)
        previewJob = viewModelScope.launch {
            try {
                do {
                    val blur = state.value.draft.blur
                    val bitmap = ReaderImageBackgroundStore.render(source, blur, maxSize = 720)
                    mutableState.value = state.value.copy(preview = bitmap, previewBlur = blur)
                } while (state.value.draft.blur != state.value.previewBlur)
                mutableState.value = state.value.copy(previewBusy = false)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.w("ReaderBackground", "Unable to preview background image", error)
                mutableState.value = state.value.copy(previewBusy = false, error = R.string.reader_background_image_failed)
            }
        }
    }

    fun apply() {
        val selected = state.value
        if (!selected.canApply) return
        mutableState.value = selected.copy(saving = true, error = null)
        saveJob = viewModelScope.launch {
            try {
                withContext(NonCancellable + Dispatchers.IO) {
                    val source = requireNotNull(selected.source)
                    val target = ReaderImageBackgroundStore.persist(appContext, source)
                    try {
                        PresentationAccess.readerSettings.saveImageBackground(selected.draft.copy(fileName = target.name))
                    } catch (error: Exception) {
                        if (target != source) target.delete()
                        throw error
                    }
                    ReaderImageBackgroundStore.removeUnused(target)
                    ReaderImageBackgroundStore.deleteDraft(appContext, source)
                }
                mutableState.value = state.value.copy(saving = false, saved = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.w("ReaderBackground", "Unable to save background image", error)
                mutableState.value = state.value.copy(saving = false, error = R.string.reader_background_save_failed)
            }
        }
    }

    override fun onCleared() {
        val source = state.value.source
        val pendingSave = saveJob
        // The ViewModel scope is already cancelled. Wait for any committed save before removing its draft.
        viewModelScope.launch(NonCancellable + Dispatchers.IO) {
            pendingSave?.join()
            ReaderImageBackgroundStore.deleteDraft(appContext, source)
        }
        super.onCleared()
    }

    fun dismissError() { mutableState.value = state.value.copy(error = null) }
}
