package com.breakyuna.esjzone.network

import com.google.gson.Gson
import java.io.IOException
import java.util.UUID
import java.util.WeakHashMap
import kotlinx.coroutines.CancellationException

internal data class ParsedListPage<T>(val pageCount: Int, val items: List<T>)
internal class StaleListBatchException : IOException("List data changed; reload the first page")

/** One compressed snapshot per account/query. Replacing page one replaces the entire batch. */
internal object ListPageCache {
    private val gson = Gson()
    private val lock = Any()
    private val batches = WeakHashMap<Batch, Unit>()

    internal data class Page(val fetchedAt: Long, val html: String)
    internal data class Snapshot(val revision: String, val pageCount: Int,
                                 val pages: Map<Int, Page>, val legacyPages: Boolean = false)

    internal class Batch(val key: String, val epoch: Long,
                         @Volatile var snapshot: Snapshot, var reference: String,
                         var active: Boolean = true)

    private fun load(key: String): Pair<Snapshot, String>? = runCatching {
        PageCache.readEntry(key)?.let { entry ->
            gson.fromJson(entry.body, Snapshot::class.java)
                ?.takeIf { it.pageCount > 0 && it.pages[1] != null && it.revision.isNotBlank() }
                ?.let { it to entry.reference }
        }
    }.getOrNull()

    // Called under lock. Live sessions share one decoded snapshot; the index owns no batch.
    private fun restore(key: String): Batch? {
        batches.keys.removeAll { it.epoch != PageCache.currentEpoch }
        val live = batches.keys.firstOrNull { it.key == key && it.active }
        if (live != null && PageCache.isCurrentReference(live.reference)) return live
        return load(key)?.let { (snapshot, reference) ->
            Batch(key, PageCache.currentEpoch, snapshot, reference)
        }
    }

    private fun publish(batch: Batch): Batch {
        val live = batches.keys.firstOrNull { it.key == batch.key && it.active && it.epoch == batch.epoch }
        if (live != null && live.snapshot.revision == batch.snapshot.revision) {
            live.snapshot = batch.snapshot
            live.reference = batch.reference
            return live
        }
        live?.active = false
        batches[batch] = Unit
        return batch
    }

    private fun save(key: String, snapshot: Snapshot, token: PageCache.WriteToken): String {
        if (!PageCache.write(key, expected = token, trim = false) { gson.toJson(snapshot, it) }) {
            throw IOException("Unable to commit list snapshot")
        }
        return requireNotNull(PageCache.reference(key))
    }

    fun <T> open(
        authorization: Authorization, firstUrl: String, pageUrl: (Int) -> String,
        maxAgeMillis: Long, kind: PageKind, forceRefresh: Boolean,
        allowFallback: Boolean = true, complete: Boolean = false, networkAttempts: Int = 1,
        parse: (String, Int) -> ParsedListPage<T>
    ): Pair<Session<T>, List<T>> {
        val key = "list-snapshot-v1|${EsjzoneClient.pageCacheKey(authorization, firstUrl)}"
        fun parsePage(html: String, page: Int): ParsedListPage<T> {
            if (!PageResponsePolicy.validate(200, html, pageUrl(page), kind = kind).trusted) {
                throw IOException("Invalid cached list page")
            }
            return parse(html, page)
        }
        val (old, initialToken) = synchronized(lock) {
            val stored = restore(key) ?: PageCache.readEntry(EsjzoneClient.pageCacheKey(authorization, firstUrl))?.let { entry ->
                runCatching {
                    val parsed = parsePage(entry.body, 1)
                    Batch(key, PageCache.currentEpoch,
                        Snapshot(UUID.randomUUID().toString(), parsed.pageCount,
                            mapOf(1 to Page(entry.fetchedAt, entry.body)), legacyPages = true), entry.reference)
                }.getOrNull()
            }
            stored to PageCache.currentToken(key)
        }
        fun session(batch: Batch, token: PageCache.WriteToken, failure: LoadFailureKind? = null): Session<T> =
            Session(publish(batch), token, authorization, pageUrl, maxAgeMillis, kind, ::parsePage, failure)
        if (!forceRefresh && !complete && old != null &&
            System.currentTimeMillis() - requireNotNull(old.snapshot.pages[1]).fetchedAt <= maxAgeMillis) {
            val parsed = try {
                parsePage(requireNotNull(old.snapshot.pages[1]).html, 1)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null // An unreadable cached page must get a chance to recover from the network.
            }
            if (parsed != null) {
                val opened = synchronized(lock) {
                    if (!PageCache.commitIfCurrent(key, initialToken) {
                            if (!PageCache.isCurrentReference(old.reference)) throw StaleListBatchException()
                            if (PageCache.reference(key) == null &&
                                PageCache.write(key, expected = initialToken) { gson.toJson(old.snapshot, it) }) {
                                old.reference = requireNotNull(PageCache.reference(key))
                                PageCache.remove(EsjzoneClient.pageCacheKey(authorization, firstUrl))
                            }
                        }) throw StaleListBatchException()
                    session(old, initialToken)
                }
                return opened to parsed.items
            }
        }
        val token = PageCache.beginWrite(key)
        try {
            val pages = linkedMapOf<Int, Page>()
            fun fetch(page: Int): ParsedListPage<T> {
                val html = request(authorization, pageUrl(page), maxAgeMillis, kind, networkAttempts)
                val parsed = parsePage(html, page)
                pages[page] = Page(System.currentTimeMillis(), html)
                return parsed
            }
            val first = fetch(1)
            val items = first.items.toMutableList()
            if (complete) for (page in 2..first.pageCount) items += fetch(page).items
            val snapshot = Snapshot(UUID.randomUUID().toString(), first.pageCount, pages)
            val opened = synchronized(lock) {
                var reference: String? = null
                if (!PageCache.commitIfCurrent(key, token) {
                    reference = save(key, snapshot, token)
                    // Only URLs known from the preceding list are eligible for legacy cleanup.
                    val lastPage = maxOf(old?.snapshot?.pageCount ?: 1, snapshot.pageCount)
                    for (page in 1..lastPage) {
                        PageCache.remove(EsjzoneClient.pageCacheKey(authorization, pageUrl(page)))
                    }
                    PageCache.trim()
                }) throw StaleListBatchException()
                session(Batch(key, token.epoch, snapshot, requireNotNull(reference)), token)
            }
            return opened to items
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (!allowFallback || old == null || error is StaleListBatchException) throw error
            val parsed = parsePage(requireNotNull(old.snapshot.pages[1]).html, 1)
            val opened = synchronized(lock) {
                if (!PageCache.commitIfCurrent(key, token) {
                        if (!PageCache.isCurrentReference(old.reference)) throw StaleListBatchException()
                    }) {
                    throw StaleListBatchException()
                }
                session(old, token, error.loadFailureKind())
            }
            return opened to parsed.items
        }
    }

    private fun request(authorization: Authorization, url: String, maxAgeMillis: Long,
                        kind: PageKind, attempts: Int = 1): String {
        repeat(attempts) { attempt ->
            try {
                return EsjzoneClient.getPage(authorization, url, maxAgeMillis,
                    forceRefresh = true, pageKind = kind, allowStaleOnError = false,
                    coalesceRequests = false, cacheResponse = false)
            } catch (error: IOException) {
                if (error.loadFailureKind() != LoadFailureKind.NETWORK || attempt == attempts - 1) throw error
                try { Thread.sleep(400L * (attempt + 1)) }
                catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw CancellationException("List request interrupted").apply { initCause(interrupted) }
                }
            }
        }
        error("No list request attempted")
    }

    internal class Session<T>(
        private val batch: Batch,
        private val token: PageCache.WriteToken, private val authorization: Authorization,
        private val pageUrl: (Int) -> String, private val maxAgeMillis: Long,
        private val kind: PageKind, private val parse: (String, Int) -> ParsedListPage<T>,
        val fallbackFailure: LoadFailureKind?
    ) {
        private val key: String get() = batch.key
        val pageCount: Int get() = batch.snapshot.pageCount

        fun isCurrent(): Boolean = synchronized(lock) {
            if (!batch.active || token.epoch != PageCache.currentEpoch) return@synchronized false
            if (PageCache.isCurrentReference(batch.reference)) return@synchronized true
            val stored = load(key)
            if (stored == null || stored.first.revision != batch.snapshot.revision) {
                batch.active = false
                return@synchronized false
            }
            batch.snapshot = stored.first
            batch.reference = stored.second
            true
        }

        fun more(page: Int): List<T> {
            require(page in 1..pageCount)
            val cached = synchronized(lock) {
                if (!isCurrent()) throw StaleListBatchException()
                val snapshot = batch.snapshot
                snapshot.pages[page] ?: if (snapshot.legacyPages) {
                    PageCache.readEntry(EsjzoneClient.pageCacheKey(authorization, pageUrl(page)))
                        ?.let { Page(it.fetchedAt, it.body) }
                } else null
            }
            if (cached != null && System.currentTimeMillis() - cached.fetchedAt <= maxAgeMillis) {
                val parsed = parse(cached.html, page)
                synchronized(lock) {
                    if (!isCurrent()) throw StaleListBatchException()
                    if (batch.snapshot.pages[page] == null) {
                        val migrationToken = PageCache.currentToken(key)
                        if (!PageCache.commitIfCurrent(key, migrationToken) {
                            val latest = batch.snapshot
                            val updated = latest.copy(pages = latest.pages + (page to cached))
                            if (PageCache.write(key, expected = migrationToken, trim = false) { gson.toJson(updated, it) }) {
                                batch.snapshot = updated
                                batch.reference = requireNotNull(PageCache.reference(key))
                                PageCache.remove(EsjzoneClient.pageCacheKey(authorization, pageUrl(page)))
                                PageCache.trim()
                            }
                        }) throw StaleListBatchException()
                    }
                }
                return parsed.items
            }
            val requestToken = PageCache.currentToken(key)
            // Failed updates leave cached pages intact and let callers retry the same page.
            val html = EsjzoneClient.getPage(authorization, pageUrl(page), maxAgeMillis,
                forceRefresh = true, pageKind = kind, allowStaleOnError = false,
                coalesceRequests = false, cacheResponse = false)
            val parsed = parse(html, page)
            synchronized(lock) {
                if (!isCurrent()) throw StaleListBatchException()
                if (!PageCache.commitIfCurrent(key, requestToken) {
                    val latest = batch.snapshot
                    val count = parsed.pageCount.coerceAtLeast(1)
                    val pages = latest.pages.filterKeys { it <= count }.toMutableMap()
                    if (page <= count) pages[page] = Page(System.currentTimeMillis(), html)
                    val updated = latest.copy(pageCount = count, pages = pages)
                    val reference = save(key, updated, requestToken)
                    batch.snapshot = updated
                    batch.reference = reference
                    PageCache.remove(EsjzoneClient.pageCacheKey(authorization, pageUrl(page)))
                    PageCache.trim()
                }) throw StaleListBatchException()
            }
            return if (page <= pageCount) parsed.items else emptyList()
        }
    }

    fun invalidate(authorization: Authorization, firstUrl: String) = synchronized(lock) {
        val key = "list-snapshot-v1|${EsjzoneClient.pageCacheKey(authorization, firstUrl)}"
        batches.keys.filter { it.key == key }.forEach { it.active = false }
        val token = PageCache.beginWrite(key)
        load(key)?.first?.let { snapshot ->
            val first = requireNotNull(snapshot.pages[1])
            val updated = snapshot.copy(pages = snapshot.pages + (1 to first.copy(fetchedAt = 0L)))
            PageCache.write(key, expected = token) { gson.toJson(updated, it) }
        }
    }
}
