package com.breakyuna.esjzone.ui.component

import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import com.breakyuna.esjzone.database.BookshelfCoverStore
import com.breakyuna.esjzone.database.entity.BookshelfEntry
import com.breakyuna.esjzone.novellibrary.novel.CoverSource
import com.breakyuna.esjzone.ui.designsystem.AppLoadedImage

/** Page-owned handoff between shelf layouts; bound retained images while scrolling. */
internal class BookshelfCoverMemory {
    private val entries = LruCache<List<String>, Snapshot>(32)

    fun snapshot(entry: BookshelfEntry): Snapshot {
        val key = listOf(entry.scope, entry.bookKey, entry.coverUrl)
        return entries.get(key) ?: Snapshot().also { entries.put(key, it) }
    }

    class Snapshot {
        var source: CoverSource? = null
        var image: AppLoadedImage? = null
    }
}

internal class BookshelfCoverPresentation(
    val source: CoverSource,
    val initialImage: AppLoadedImage?,
    val onImageLoaded: (AppLoadedImage) -> Unit
)

@Composable
internal fun rememberBookshelfCover(entry: BookshelfEntry, memory: BookshelfCoverMemory?): BookshelfCoverPresentation {
    val snapshot = remember(memory, entry.scope, entry.bookKey, entry.coverUrl) {
        memory?.snapshot(entry)
    }
    val source = BookshelfCoverStore.rememberCover(entry, snapshot?.source)
    SideEffect { snapshot?.source = source }
    return BookshelfCoverPresentation(source, snapshot?.image) { snapshot?.image = it }
}
