package com.breakyuna.esjzone.ui.page

import com.breakyuna.esjzone.novellibrary.novel.CoverFailure
import com.breakyuna.esjzone.novellibrary.novel.CoverSource
import com.breakyuna.esjzone.novellibrary.novel.CoverState
import com.breakyuna.esjzone.ui.designsystem.AppLoadedImage

internal data class LocalHistoryCover(
    val source: CoverSource,
    val image: AppLoadedImage? = null
)

internal data class HistoryShelfCover(val local: String?, val remote: String)

/** Probe local sources before allowing any image or detail request to use the network. */
internal suspend fun resolveLocalHistoryCover(
    stored: String,
    shelf: suspend () -> HistoryShelfCover?,
    downloadedFile: suspend () -> String?,
    downloadedUrl: suspend () -> String?,
    cachedImage: suspend (String) -> AppLoadedImage?,
    remoteImage: (suspend (String) -> LocalHistoryCover)?,
    detail: suspend () -> CoverSource
): LocalHistoryCover {
    val checked = mutableSetOf<String>()
    suspend fun probe(model: String?): LocalHistoryCover? {
        if (model.isNullOrBlank() || !checked.add(model)) return null
        return cachedImage(model)?.let { LocalHistoryCover(CoverSource(), it) }
    }

    probe(stored)?.let { return it }
    val shelfCover = shelf()
    probe(shelfCover?.local)?.let { return it }
    probe(shelfCover?.remote)?.let { return it }
    // A warm Coil hit can display immediately without touching the download provider.
    probe(downloadedFile())?.let { return it }
    val manifestUrl = downloadedUrl()
    probe(manifestUrl)?.let { return it }

    if (remoteImage == null) {
        return LocalHistoryCover(CoverSource(state = CoverState.Failed(CoverFailure.UNAVAILABLE)))
    }

    val candidates = listOfNotNull(stored, shelfCover?.remote, manifestUrl).filter {
        it.startsWith("https://") || it.startsWith("http://")
    }.distinct()
    if (candidates.isEmpty()) {
        val source = detail()
        return if (source.url.isBlank()) LocalHistoryCover(source) else remoteImage(source.url)
    }
    var failure = LocalHistoryCover(CoverSource(state = CoverState.Failed(CoverFailure.UNAVAILABLE)))
    for (url in candidates) {
        val result = remoteImage(url)
        if (result.image != null) return result
        failure = result
    }
    return failure
}
