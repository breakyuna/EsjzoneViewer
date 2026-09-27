package com.breakyuna.esjzone.ui.page

import androidx.compose.runtime.saveable.Saver
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import com.breakyuna.esjzone.novellibrary.component.ChapterItem
import com.breakyuna.esjzone.novellibrary.component.ChapterListItem
import com.breakyuna.esjzone.novellibrary.component.Item
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.NovelChapterList
import com.breakyuna.esjzone.offline.NovelDownloadStore

internal sealed interface DownloadSelectionRow {
    val id: String
    val depth: Int

    data class Entry(val chapter: Chapter, val ordinal: Int, override val depth: Int) : DownloadSelectionRow {
        override val id: String = "chapter:${chapter.url}"
    }

    data class Group(
        override val id: String,
        val title: String,
        val urls: Set<String>,
        val children: List<DownloadSelectionRow>,
        override val depth: Int
    ) : DownloadSelectionRow
}

internal class DownloadChapterSelection(chapterList: NovelChapterList) {
    val chapters = chapterList.orderedChapters.filter { !it.isExternal }
        .distinctBy { NovelDownloadStore.chapterKey(it.url) }
    val urls = chapters.map { it.url }.toSet()
    private val entriesByKey = chapters.mapIndexed { index, chapter ->
        NovelDownloadStore.chapterKey(chapter.url) to DownloadSelectionRow.Entry(chapter, index + 1, 0)
    }.toMap()
    val rows: List<DownloadSelectionRow>
    val defaultExpandedIds: Set<String>

    init {
        val seen = mutableSetOf<String>()
        val defaultExpanded = mutableSetOf<String>()
        fun build(items: List<Item>, path: String, depth: Int): List<DownloadSelectionRow> = buildList {
            items.forEachIndexed { index, item ->
                when (item) {
                    is ChapterItem -> {
                        val key = NovelDownloadStore.chapterKey(item.chapter.url)
                        val entry = entriesByKey[key]
                        if (entry != null && seen.add(key)) add(entry.copy(depth = depth))
                    }
                    is ChapterListItem -> {
                        val id = "$path/$index"
                        val children = build(item.children, id, depth + 1)
                        val groupUrls = item.chapters.mapNotNull {
                            entriesByKey[NovelDownloadStore.chapterKey(it.url)]?.chapter?.url
                        }.toSet()
                        if (children.isNotEmpty()) {
                            if (item.initiallyExpanded) defaultExpanded.add(id)
                            add(DownloadSelectionRow.Group(id, item.name.text, groupUrls, children, depth))
                        }
                    }
                    else -> Unit
                }
            }
        }
        rows = build(chapterList.items, "group", 0)
        defaultExpandedIds = defaultExpanded
    }

    fun visibleRows(expanded: Set<String>): List<DownloadSelectionRow> = buildList {
        fun append(rows: List<DownloadSelectionRow>) {
            for (row in rows) {
                add(row)
                if (row is DownloadSelectionRow.Group && row.id in expanded) append(row.children)
            }
        }
        append(rows)
    }

    /** Inclusive, one-based positions in the downloadable, deduplicated table of contents. */
    fun range(start: String, end: String): Set<String>? {
        val first = start.toIntOrNull() ?: return null
        val last = end.toIntOrNull() ?: return null
        if (first < 1 || last < first || last > chapters.size) return null
        return chapters.subList(first - 1, last).map { it.url }.toSet()
    }

    fun pendingSelection(selected: Set<String>, saved: Set<String>): Set<String> =
        selected.intersect(urls - saved)

    fun applyRange(selected: Set<String>, saved: Set<String>, range: Set<String>, add: Boolean): Set<String> {
        val pending = pendingSelection(selected, saved)
        val pendingRange = range - saved
        return if (add) pending + pendingRange else pending - pendingRange
    }
}

/** Keep actual URLs across TOC changes without putting thousands of full strings in saved state. */
private const val MAX_SAVED_SELECTION_LENGTH = 32_000

internal val DownloadChapterSelectionSaver = Saver<Set<String>, String>(
    save = { urls ->
        if (urls.isEmpty()) "" else {
            val compressed = ByteArrayOutputStream().also { output ->
                GZIPOutputStream(output).use { it.write(urls.joinToString("\u0000").toByteArray(Charsets.UTF_8)) }
            }.toByteArray()
            Base64.getEncoder().encodeToString(compressed)
                .takeIf { it.length <= MAX_SAVED_SELECTION_LENGTH }.orEmpty()
        }
    },
    restore = { encoded ->
        if (encoded.isEmpty()) emptySet() else runCatching {
            GZIPInputStream(ByteArrayInputStream(Base64.getDecoder().decode(encoded))).use {
                it.readBytes().toString(Charsets.UTF_8).split('\u0000').filter(String::isNotBlank).toSet()
            }
        }.getOrDefault(emptySet())
    }
)
