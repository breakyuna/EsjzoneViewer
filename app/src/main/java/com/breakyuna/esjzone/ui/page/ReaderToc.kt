package com.breakyuna.esjzone.ui.page

import com.breakyuna.esjzone.novellibrary.component.ChapterItem
import com.breakyuna.esjzone.novellibrary.component.ChapterListItem
import com.breakyuna.esjzone.novellibrary.component.Item
import com.breakyuna.esjzone.novellibrary.component.TextItem
import com.breakyuna.esjzone.novellibrary.novel.Chapter

/** One volume as the detail page lists it; [title] is blank for chapters before any heading. */
internal data class ReaderTocSourceVolume(val title: String, val chapters: List<Chapter>)

/**
 * Reads volumes from a parsed detail table of contents. A top-level `<details>` group is a
 * volume (nested groups fold into it); a text heading starts a volume for the loose chapters
 * that follow it. Empty volumes are dropped.
 */
internal fun readerTocSourceVolumes(items: List<Item>): List<ReaderTocSourceVolume> {
    val volumes = mutableListOf<ReaderTocSourceVolume>()
    var heading = ""
    val loose = mutableListOf<Chapter>()
    fun flushLoose() {
        if (loose.isNotEmpty()) volumes += ReaderTocSourceVolume(heading, loose.toList())
        loose.clear()
    }
    for (item in items) {
        when (item) {
            is ChapterListItem -> {
                flushLoose()
                heading = ""
                if (item.chapters.isNotEmpty()) {
                    volumes += ReaderTocSourceVolume(item.name.text.trim(), item.chapters)
                }
            }
            is TextItem -> {
                flushLoose()
                heading = item.text.trim()
            }
            is ChapterItem -> loose += item.chapter
        }
    }
    flushLoose()
    return volumes
}

/** A contiguous run of reader chapters in one volume; [title] null means no heading. */
internal data class ReaderTocVolume(val key: String, val title: String?, val chapters: List<Chapter>)

/** [grouped] is false when no volume headings are known, so the drawer shows a flat list. */
internal data class ReaderToc(val volumes: List<ReaderTocVolume>) {
    val grouped: Boolean get() = volumes.any { it.title != null }
}

/**
 * Groups the reader's authoritative chapter order by volume. The source TOC only supplies
 * headings: chapters it does not know (local-only or newer than the cached page) stay in the
 * volume of the chapter before them, so the reading order is never changed.
 */
internal fun buildReaderToc(
    chapters: List<Chapter>,
    sources: List<ReaderTocSourceVolume>,
    keyOf: (Chapter) -> String = ::chapterIdentity
): ReaderToc {
    if (chapters.isEmpty()) return ReaderToc(emptyList())
    val volumeOf = HashMap<String, Int>()
    sources.forEachIndexed { index, volume ->
        volume.chapters.forEach { volumeOf.putIfAbsent(keyOf(it), index) }
    }
    val hasHeadings = sources.any { it.title.isNotBlank() }
    if (!hasHeadings || chapters.none { keyOf(it) in volumeOf }) {
        return ReaderToc(listOf(ReaderTocVolume("all", null, chapters)))
    }
    val result = mutableListOf<ReaderTocVolume>()
    val occurrences = HashMap<Int, Int>()
    var runIndex: Int? = null
    var run = mutableListOf<Chapter>()
    fun flush() {
        val index = runIndex ?: return
        if (run.isEmpty()) return
        val seen = occurrences.merge(index, 1, Int::plus) ?: 1
        val title = sources.getOrNull(index)?.title?.takeIf(String::isNotBlank)
        result += ReaderTocVolume("v$index#$seen", title, run)
        run = mutableListOf()
    }
    var last = -1
    for (chapter in chapters) {
        val index = volumeOf[keyOf(chapter)] ?: last
        if (index != runIndex) {
            flush()
            runIndex = index
        }
        run += chapter
        last = index
    }
    flush()
    return ReaderToc(result)
}

/** Descending order reverses both the volumes and the chapters inside each volume. */
internal fun ReaderToc.ordered(descending: Boolean): ReaderToc =
    if (!descending) this
    else ReaderToc(volumes.asReversed().map { it.copy(chapters = it.chapters.asReversed()) })

internal fun ReaderToc.volumeKeyOf(currentKey: String, keyOf: (Chapter) -> String = ::chapterIdentity): String? =
    volumes.firstOrNull { volume -> volume.chapters.any { keyOf(it) == currentKey } }?.key

internal sealed interface ReaderTocRow {
    val key: String
    data class Volume(val volume: ReaderTocVolume, val count: Int, val expanded: Boolean) : ReaderTocRow {
        override val key: String get() = "volume:${volume.key}"
    }
    data class Entry(val chapter: Chapter, val volumeKey: String, val chapterKey: String) : ReaderTocRow {
        override val key: String get() = "chapter:$chapterKey"
    }
}

/**
 * Lazy-list rows for the drawer. A non-blank [query] filters chapter names, hides volumes
 * without matches and expands the rest; a flat TOC has no volume rows.
 */
internal fun readerTocRows(
    toc: ReaderToc,
    descending: Boolean,
    expanded: Set<String>,
    query: String = "",
    keyOf: (Chapter) -> String = ::chapterIdentity
): List<ReaderTocRow> {
    val term = query.trim()
    val rows = mutableListOf<ReaderTocRow>()
    for (volume in toc.ordered(descending).volumes) {
        val chapters = if (term.isEmpty()) volume.chapters
        else volume.chapters.filter { it.name.contains(term, ignoreCase = true) }
        if (chapters.isEmpty()) continue
        val open = !toc.grouped || term.isNotEmpty() || volume.key in expanded
        if (toc.grouped) rows += ReaderTocRow.Volume(volume, chapters.size, open)
        if (open) chapters.mapTo(rows) { ReaderTocRow.Entry(it, volume.key, keyOf(it)) }
    }
    return rows
}

/** Row to scroll to: the current chapter, or its volume header while that volume is collapsed. */
internal fun readerTocIndexOf(rows: List<ReaderTocRow>, currentKey: String, currentVolumeKey: String?): Int {
    val entry = rows.indexOfFirst { it is ReaderTocRow.Entry && it.chapterKey == currentKey }
    if (entry >= 0 || currentVolumeKey == null) return entry
    return rows.indexOfFirst { it is ReaderTocRow.Volume && it.volume.key == currentVolumeKey }
}
