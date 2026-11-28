package com.breakyuna.esjzone.network

import com.breakyuna.esjzone.data.reader.ChapterBody
import com.breakyuna.esjzone.data.reader.hasChapterFormattingMarkup
import com.breakyuna.esjzone.data.reader.withFormatting
import com.breakyuna.esjzone.data.reader.withStructuredBody
import com.breakyuna.esjzone.domain.reader.ReaderChapterFormatting
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import com.google.gson.Gson
import java.security.MessageDigest

/** Shares PageCache's account namespace, timestamps, quota, clearing and atomic writes. */
internal object StructuredChapterCache {
    private val gson = Gson()
    private data class FormattingKey(
        val fingerprint: String,
        val sourceUrl: String,
        val htmlDigest: String,
        val savedAlignments: Map<Int, String>
    )
    private data class FormattingResult(val value: ReaderChapterFormatting?)
    private val recoveredFormatting = object : LinkedHashMap<FormattingKey, FormattingResult>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<FormattingKey, FormattingResult>?) = size > 64
    }
    private data class Snapshot(val name: String, val sourceUrl: String, val body: ChapterBody,
        val html: String?, val previous: Chapter?, val next: Chapter?)
    internal data class ReadResult(val chapter: DetailedChapter, val isFresh: Boolean)

    fun read(key: String, url: String, maxAgeMillis: Long = PageCacheTtl.CHAPTER): DetailedChapter? {
        if (maxAgeMillis <= 0L) {
            CacheAnalysis.lookup(CacheLayer.CHAPTER, fresh = false)
            return null
        }
        return readWithFreshness(key, url, maxAgeMillis)?.takeIf { it.isFresh }?.chapter
    }

    internal fun readWithFreshness(
        key: String,
        url: String,
        maxAgeMillis: Long = PageCacheTtl.CHAPTER
    ): ReadResult? {
        val result = PageCache.readWithFreshness(
            PageCache.structuredKey(key),
            maxAgeMillis,
            sourceKey = key,
            touchIfStale = true
        ) ?: run {
            CacheAnalysis.lookup(CacheLayer.CHAPTER, fresh = false)
            return null
        }
        val entry = result.entry
        val encoded = entry.body
        return runCatching {
            val stored = requireNotNull(gson.fromJson(encoded, Snapshot::class.java))
            require(stored.sourceUrl == url) // Absolute resources belong to the acquisition mirror.
            val html = stored.html
            val checked = stored.body.validate()
                .rebuildFromHtmlIfOutdated(html, stored.sourceUrl)
                .recoverLeadingBreakText(html, stored.sourceUrl)
            val needsFormattingRecovery = (checked.formatting?.parserVersion ?: 0) < ReaderChapterFormatting.CURRENT_PARSER_VERSION
            val formatKey = if (needsFormattingRecovery && !html.isNullOrBlank() && html.hasChapterFormattingMarkup()) {
                val digest = MessageDigest.getInstance("SHA-256").digest(html.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
                FormattingKey(checked.fingerprint, stored.sourceUrl, digest, checked.formatting?.alignments.orEmpty())
            } else null
            val cached = formatKey?.let { synchronized(recoveredFormatting) { recoveredFormatting[it] } }
            val body = if (cached != null) cached.value?.let { checked.copy(formatting = it) } ?: checked
                else checked.withFormatting(html, stored.sourceUrl)
            if (cached == null && formatKey != null) {
                synchronized(recoveredFormatting) { recoveredFormatting[formatKey] = FormattingResult(body.formatting) }
            }
            val chapter = DetailedChapter(stored.name, body.components(), stored.previous, stored.next,
                stored.html, stored.sourceUrl, body, sourceCacheReference = entry.reference)
            if (!result.isFresh) {
                CacheAnalysis.lookup(CacheLayer.CHAPTER, fresh = false)
                CacheDiagnostics.record(CacheEventType.HIT, CacheLayer.CHAPTER, CacheEventReason.STALE, PageCache.structuredKey(key))
            } else {
                val freshForTtl = System.currentTimeMillis() - entry.fetchedAt <= PageCacheTtl.CHAPTER
                CacheAnalysis.lookup(CacheLayer.CHAPTER, fresh = freshForTtl)
                val reason = if (freshForTtl) CacheEventReason.FRESH else CacheEventReason.STALE
                CacheDiagnostics.record(CacheEventType.HIT, CacheLayer.CHAPTER, reason, PageCache.structuredKey(key))
            }
            ReadResult(chapter, result.isFresh)
        }.getOrElse {
            CacheAnalysis.lookup(CacheLayer.CHAPTER, fresh = false)
            PageCache.remove(PageCache.structuredKey(key), CacheEventReason.INVALID_DATA)
            null
        }
    }

    fun write(key: String, fetchedAt: Long, sourceReference: String, detail: DetailedChapter): String? {
        val ready = detail.withStructuredBody()
        if (ready.content.isEmpty()) return null
        val json = gson.toJson(Snapshot(ready.name, ready.sourceUrl.orEmpty(), requireNotNull(ready.body),
            ready.contentHtml, ready.previous, ready.next))
        return PageCache.writeDerived(key, fetchedAt, sourceReference, json)
    }

    fun write(key: String, sourceHtml: String, detail: DetailedChapter): String? {
        val ready = detail.withStructuredBody()
        if (ready.content.isEmpty()) return null
        val json = gson.toJson(Snapshot(ready.name, ready.sourceUrl.orEmpty(), requireNotNull(ready.body),
            ready.contentHtml, ready.previous, ready.next))
        return PageCache.writeDerived(key, sourceHtml, json)
    }
}
