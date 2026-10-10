package com.breakyuna.esjzone.ui.discovery

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.staticCompositionLocalOf
import com.breakyuna.esjzone.network.wenku8.Wenku8Urls

/** Presentation selection only; never changes a request domain or a book identity. */
enum class LibrarySource { ESJZONE, WENKU8 }

internal val LocalDiscoverySource = staticCompositionLocalOf<MutableState<LibrarySource>> {
    error("Discovery source is not provided")
}

internal enum class WenkuAccountStatus { UNCHECKED, SIGNED_IN, SIGNED_OUT }

internal val LocalWenkuAccountStatus = staticCompositionLocalOf<MutableState<WenkuAccountStatus>> {
    error("Wenku account status is not provided")
}

internal fun librarySourceOf(novelId: String = "", url: String = ""): LibrarySource =
    if (Wenku8Urls.bookId(novelId) != null || Wenku8Urls.detailIdentity(url) != null ||
        Wenku8Urls.chapterIdentity(url) != null || Wenku8Urls.catalogIdentity(url) != null
    ) LibrarySource.WENKU8 else LibrarySource.ESJZONE

internal fun matchesLibrarySource(filter: LibrarySource?, novelId: String = "", url: String = ""): Boolean =
    filter == null || filter == librarySourceOf(novelId, url)
