package com.breakyuna.esjzone.ui.page

import android.icu.text.Transliterator
import com.breakyuna.esjzone.database.entity.BookshelfEntry
import com.breakyuna.esjzone.database.readingStatisticsBookKey
import com.breakyuna.esjzone.novellibrary.novel.validNovelTitle
import java.text.Normalizer
import java.util.Locale

/**
 * Local bookshelf search. It only reads fields already stored on the device
 * (Room shelf rows, cached detail tags and download manifests), so it never
 * requests the site and never touches the account's view history.
 */
internal object BookshelfSearch {

    /** Normalized searchable fields of one shelf row. */
    data class Key(val title: String, val author: String, val tags: List<String> = emptyList())

    /**
     * Local identity shared by cached detail tags (`reading_stats_tags:`) and
     * download manifests: the ESJ detail id when known, otherwise the page key.
     */
    fun tagKey(novelId: String, url: String): String = readingStatisticsBookKey(novelId, url)

    /**
     * Merges locally known tags per book. Each source is keyed by [tagKey];
     * blank and duplicate tags are dropped while the first-seen order is kept.
     */
    fun mergeTags(vararg sources: Map<String, List<String>>): Map<String, List<String>> {
        val merged = LinkedHashMap<String, LinkedHashSet<String>>()
        sources.forEach { source ->
            source.forEach { (key, tags) ->
                if (key.isBlank()) return@forEach
                tags.map(String::trim).filter(String::isNotEmpty).forEach { tag ->
                    merged.getOrPut(key, ::LinkedHashSet).add(tag)
                }
            }
        }
        return merged.mapValues { it.value.toList() }
    }

    /**
     * Folds Traditional characters to Simplified with Android's ICU so either
     * script matches the other. JVM unit tests have no ICU data and inject a fold.
     */
    private val scriptFold = ThreadLocal.withInitial<Transliterator?> {
        runCatching { Transliterator.getInstance("Traditional-Simplified") }.getOrNull()
    }

    val defaultFold: (String) -> String = { text -> scriptFold.get()?.transliterate(text) ?: text }

    /** Width, case and script insensitive form; whitespace is removed. */
    fun normalize(text: String, fold: (String) -> String = defaultFold): String {
        if (text.isBlank()) return ""
        val compact = Normalizer.normalize(text, Normalizer.Form.NFKC).filterNot(Char::isWhitespace)
        return fold(compact).lowercase(Locale.ROOT)
    }

    /** Whitespace separates terms; every term must match the title, the author or one tag. */
    fun terms(query: String, fold: (String) -> String = defaultFold): List<String> =
        Normalizer.normalize(query, Normalizer.Form.NFKC)
            .split(Regex("\\s+"))
            .map { normalize(it, fold) }
            .filter(String::isNotEmpty)

    fun keyFor(
        entry: BookshelfEntry,
        fold: (String) -> String = defaultFold,
        tags: List<String> = emptyList()
    ): Key = Key(
        title = normalize(validNovelTitle(entry.title, entry.novelId, entry.url), fold),
        author = normalize(entry.author, fold),
        tags = tags.map { normalize(it, fold) }.filter(String::isNotEmpty).distinct()
    )

    fun index(
        entries: List<BookshelfEntry>,
        fold: (String) -> String = defaultFold,
        tags: Map<String, List<String>> = emptyMap()
    ): Map<String, Key> =
        entries.associate { entry ->
            entry.bookKey to keyFor(entry, fold, tags[tagKey(entry.novelId, entry.url)].orEmpty())
        }

    /** A term never spans two tags; it must be contained in a single field. */
    fun matches(key: Key, terms: List<String>): Boolean =
        terms.all { term -> term in key.title || term in key.author || key.tags.any { term in it } }

    /** Keeps the input order; a blank query returns the list unchanged. */
    fun filter(
        entries: List<BookshelfEntry>,
        terms: List<String>,
        index: Map<String, Key>,
        fold: (String) -> String = defaultFold
    ): List<BookshelfEntry> {
        if (terms.isEmpty()) return entries
        return entries.filter { matches(index[it.bookKey] ?: keyFor(it, fold), terms) }
    }
}

/** Select-all toggles only the books currently shown, including a search result. */
internal fun bookshelfSelectAll(selected: Set<String>, shownKeys: Set<String>): Set<String> =
    if (shownKeys.isNotEmpty() && selected.containsAll(shownKeys)) emptySet() else shownKeys
