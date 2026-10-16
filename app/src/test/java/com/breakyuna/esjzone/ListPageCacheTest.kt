package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.ListPageCache
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.PageCache
import com.breakyuna.esjzone.network.PageKind
import com.breakyuna.esjzone.network.ParsedListPage
import com.breakyuna.esjzone.network.StaleListBatchException
import com.breakyuna.esjzone.network.features.ChapterPasswordRequiredException
import com.breakyuna.esjzone.network.features.pageCount
import com.google.gson.Gson
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test
import org.jsoup.Jsoup

class ListPageCacheTest {
    private val authorization = Authorization()
    private val url = "https://www.esjzone.cc/list-01"
    private fun pageUrl(page: Int) = if (page == 1) url else "$url/$page.html"
    private fun html(page: Int, pages: Int = 2) = "<html><head><title>ESJZone</title></head>" +
        "<body><main><a href='/detail/9001.html'>Page $page</a></main>" +
        "<script>var pagination = {total: $pages};</script></body></html>"
    private val key get() = "list-snapshot-v1|${EsjzoneClient.pageCacheKey(authorization, url)}"

    private fun store(revision: String) {
        val now = System.currentTimeMillis()
        val snapshot = ListPageCache.Snapshot(revision, 2, (1..2).associateWith {
            ListPageCache.Page(now, html(it))
        })
        assertTrue(PageCache.write(key, Gson().toJson(snapshot)))
    }

    private fun open() = ListPageCache.open(authorization, url, ::pageUrl, 60_000L,
        PageKind.LIST, forceRefresh = false) { body, _ -> ParsedListPage(2, listOf(body)) }

    private fun open(firstUrl: String) = ListPageCache.open(authorization, firstUrl,
        { page -> if (page == 1) firstUrl else "$firstUrl/$page.html" },
        60_000L, PageKind.LIST, forceRefresh = false
    ) { body, _ -> ParsedListPage(pageCount(Jsoup.parse(body)), listOf(body)) }

    private fun store(firstUrl: String, snapshot: ListPageCache.Snapshot): String {
        val snapshotKey = "list-snapshot-v1|${EsjzoneClient.pageCacheKey(authorization, firstUrl)}"
        assertTrue(PageCache.write(snapshotKey, Gson().toJson(snapshot)))
        return snapshotKey
    }

    private fun withResponses(responses: List<String>, block: (String) -> Unit) {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 5_000
            val served = FutureTask<Unit> {
                responses.forEach { html ->
                    server.accept().use { socket ->
                        socket.soTimeout = 5_000
                        val reader = socket.getInputStream().bufferedReader()
                        while (!reader.readLine().isNullOrEmpty()) { }
                        val body = html.toByteArray(Charsets.UTF_8)
                        socket.getOutputStream().apply {
                            write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n" +
                                "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n")
                                .toByteArray(Charsets.US_ASCII))
                            write(body)
                            flush()
                        }
                    }
                }
            }
            thread(isDaemon = true) { served.run() }
            block("http://127.0.0.1:${server.localPort}/list-01")
            served.get(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun persistedBatchKeepsAllPagesAcrossReinitializationAndInvalidatesOldSession() {
        val directory = Files.createTempDirectory("list-batch-restart-").toFile()
        PageCache.initializeDirectory(directory)
        try {
            store("batch-a")
            val (old, items) = open()
            assertEquals(listOf(html(1)), items)
            assertEquals(listOf(html(2)), old.more(2))
            PageCache.initializeDirectory(directory)
            assertFalse(old.isCurrent())
            val (restored, _) = open()
            assertEquals(listOf(html(2)), restored.more(2))
            assertEquals(1, PageCache.stats().entryCount)
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun requesterFromReplacedBatchCannotReadOrWriteNewBatchPages() {
        val directory = Files.createTempDirectory("list-batch-replace-").toFile()
        PageCache.initializeDirectory(directory)
        try {
            store("batch-a")
            val (old, _) = open()
            store("batch-b")
            val (current, _) = open()
            assertTrue(current.isCurrent())
            assertFalse(old.isCurrent())
            try {
                old.more(2)
                fail("Obsolete requester must be rejected before making a request")
            } catch (_: StaleListBatchException) { }
            assertEquals(listOf(html(2)), current.more(2))
            assertEquals(1, PageCache.stats().entryCount)
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun adoptingLegacyPagesKeepsTheirFetchTimesAndReclaimsIndependentFiles() {
        val directory = Files.createTempDirectory("list-legacy-adoption-").toFile()
        PageCache.initializeDirectory(directory)
        try {
            val fetchedAt = System.currentTimeMillis() - 1000L
            val firstKey = EsjzoneClient.pageCacheKey(authorization, pageUrl(1))
            val secondKey = EsjzoneClient.pageCacheKey(authorization, pageUrl(2))
            PageCache.write(firstKey, html(1), fetchedAt)
            PageCache.write(secondKey, html(2), fetchedAt)
            val (session, _) = open()
            assertNull(PageCache.readStale(firstKey))
            assertEquals(listOf(html(2)), session.more(2))
            assertNull(PageCache.readStale(secondKey))
            val stored = Gson().fromJson(PageCache.readStale(key), ListPageCache.Snapshot::class.java)
            assertEquals(fetchedAt, stored.pages[1]?.fetchedAt)
            assertEquals(fetchedAt, stored.pages[2]?.fetchedAt)
            assertEquals(1, PageCache.stats().entryCount)
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun failedPageUpdateReportsFailureAndPreservesCachedBatchForRetry() {
        val directory = Files.createTempDirectory("list-page-failure-").toFile()
        PageCache.initializeDirectory(directory)
        try {
            val now = System.currentTimeMillis()
            val snapshot = ListPageCache.Snapshot("batch-a", 2, mapOf(
                1 to ListPageCache.Page(now, html(1)),
                2 to ListPageCache.Page(now - 120_000L, html(2))
            ))
            assertTrue(PageCache.write(key, Gson().toJson(snapshot)))
            // An unsupported scheme fails locally without making an external request.
            val (session, _) = ListPageCache.open(authorization, url,
                { page -> if (page == 1) url else "unsupported://list-page/$page" },
                60_000L, PageKind.LIST, forceRefresh = false
            ) { body, _ -> ParsedListPage(2, listOf(body)) }
            val before = PageCache.readStale(key)
            repeat(2) {
                try {
                    session.more(2)
                    fail("Failed update must not silently return the expired page")
                } catch (_: IllegalArgumentException) { }
                assertTrue(session.isCurrent())
                assertEquals(before, PageCache.readStale(key))
            }
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun failedSnapshotCommitKeepsOldBatchAndReportsFailureWithoutFallback() {
        val directory = Files.createTempDirectory("list-commit-failure-").toFile()
        PageCache.initializeDirectory(directory)
        try {
            ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
                server.soTimeout = 5_000
                val firstUrl = "http://127.0.0.1:${server.localPort}/list-01"
                val snapshotKey = "list-snapshot-v1|${EsjzoneClient.pageCacheKey(authorization, firstUrl)}"
                val now = System.currentTimeMillis()
                val snapshot = ListPageCache.Snapshot("batch-a", 2, (1..2).associateWith {
                    ListPageCache.Page(now, html(it))
                })
                assertTrue(PageCache.write(snapshotKey, Gson().toJson(snapshot)))
                val refreshed = html(1).replace("Page 1", "Refreshed page")
                var parsedRefresh = false
                fun open(forceRefresh: Boolean = false, allowFallback: Boolean = true) =
                    ListPageCache.open(authorization, firstUrl,
                        { page -> if (page == 1) firstUrl else "$firstUrl/$page.html" },
                        60_000L, PageKind.LIST, forceRefresh, allowFallback
                    ) { body, _ ->
                        if (body == refreshed) parsedRefresh = true
                        ParsedListPage(2, listOf(body))
                    }
                val (old, _) = open()
                val before = PageCache.readStale(snapshotKey)
                val source = directory.listFiles()!!.single()
                val blockedTemporary = File(directory, "${source.name}.tmp")
                assertTrue(blockedTemporary.mkdir())
                File(blockedTemporary, "blocker").writeText("fixture")
                val responses = FutureTask<Unit> {
                    repeat(2) {
                        server.accept().use { socket ->
                            socket.soTimeout = 5_000
                            val reader = socket.getInputStream().bufferedReader()
                            while (!reader.readLine().isNullOrEmpty()) { }
                            val body = refreshed.toByteArray(Charsets.UTF_8)
                            socket.getOutputStream().apply {
                                write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n" +
                                    "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n")
                                    .toByteArray(Charsets.US_ASCII))
                                write(body)
                                flush()
                            }
                        }
                    }
                }
                thread(isDaemon = true) { responses.run() }
                val (fallback, items) = open(forceRefresh = true)
                assertTrue(parsedRefresh)
                assertEquals(listOf(html(1)), items)
                assertEquals(LoadFailureKind.CLIENT, fallback.fallbackFailure)
                parsedRefresh = false
                try {
                    open(forceRefresh = true, allowFallback = false)
                    fail("A failed snapshot commit must not report a successful refresh")
                } catch (_: IOException) { }
                assertTrue(parsedRefresh)
                responses.get(5, TimeUnit.SECONDS)
                assertEquals(before, PageCache.readStale(snapshotKey))
                val (reopened, reopenedItems) = open()
                assertEquals(listOf(html(1)), reopenedItems)
                assertTrue(old.isCurrent())
                assertTrue(fallback.isCurrent())
                assertTrue(reopened.isCurrent())
                assertEquals(listOf(html(2)), old.more(2))
            }
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun failedMoreCommitKeepsOldPageAndRetriesBeforePublishingToOtherSessions() {
        val directory = Files.createTempDirectory("list-more-commit-").toFile()
        PageCache.initializeDirectory(directory)
        try {
            val refreshed = html(2).replace("Page 2", "Updated page")
            withResponses(listOf(refreshed, refreshed)) { firstUrl ->
                val now = System.currentTimeMillis()
                val snapshotKey = store(firstUrl, ListPageCache.Snapshot("batch-a", 2, mapOf(
                    1 to ListPageCache.Page(now, html(1)),
                    2 to ListPageCache.Page(now - 120_000L, html(2))
                )))
                val (session, _) = open(firstUrl)
                val (other, _) = open(firstUrl)
                val before = PageCache.readStale(snapshotKey)
                val source = directory.listFiles()!!.single()
                val blockedTemporary = File(directory, "${source.name}.tmp")
                assertTrue(blockedTemporary.mkdir())
                File(blockedTemporary, "blocker").writeText("fixture")
                try {
                    session.more(2)
                    fail("A failed page commit must not publish the fetched page")
                } catch (_: IOException) { }
                assertEquals(before, PageCache.readStale(snapshotKey))
                assertTrue(session.isCurrent())
                assertTrue(other.isCurrent())
                assertTrue(blockedTemporary.deleteRecursively())
                assertEquals(listOf(refreshed), session.more(2))
                assertEquals(listOf(refreshed), other.more(2))
                val stored = Gson().fromJson(PageCache.readStale(snapshotKey), ListPageCache.Snapshot::class.java)
                assertEquals(refreshed, stored.pages[2]?.html)
            }
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun fetchedPageCountShrinksEveryLiveSessionAndPersistedBatch() {
        val directory = Files.createTempDirectory("list-page-count-").toFile()
        PageCache.initializeDirectory(directory)
        try {
            withResponses(listOf(html(2, 2))) { firstUrl ->
                val now = System.currentTimeMillis()
                val snapshotKey = store(firstUrl, ListPageCache.Snapshot("batch-a", 3, mapOf(
                    1 to ListPageCache.Page(now, html(1, 3)),
                    3 to ListPageCache.Page(now, html(3, 3))
                )))
                val (session, _) = open(firstUrl)
                val (other, _) = open(firstUrl)
                assertEquals(listOf(html(2, 2)), session.more(2))
                assertEquals(2, session.pageCount)
                assertEquals(2, other.pageCount)
                assertEquals(listOf(html(2, 2)), other.more(2))
                val stored = Gson().fromJson(PageCache.readStale(snapshotKey), ListPageCache.Snapshot::class.java)
                assertEquals(2, stored.pageCount)
                assertNull(stored.pages[3])
                try {
                    session.more(3)
                    fail("Removed pages must not be requested")
                } catch (_: IllegalArgumentException) { }
            }
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun invalidFreshCachedPageRecoversFromNetwork() {
        val directory = Files.createTempDirectory("list-invalid-fresh-").toFile()
        PageCache.initializeDirectory(directory)
        try {
            withResponses(listOf(html(1))) { firstUrl ->
                val snapshotKey = store(firstUrl, ListPageCache.Snapshot("invalid-batch", 2,
                    mapOf(1 to ListPageCache.Page(System.currentTimeMillis(), "<html>invalid</html>"))))
                val (session, items) = open(firstUrl)
                assertEquals(listOf(html(1)), items)
                assertTrue(session.isCurrent())
                val stored = Gson().fromJson(PageCache.readStale(snapshotKey), ListPageCache.Snapshot::class.java)
                assertEquals(html(1), stored.pages[1]?.html)
                assertNotEquals("invalid-batch", stored.revision)
            }
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun invalidFreshLaterPageRecoversWithoutReplacingTheBatch() {
        val directory = Files.createTempDirectory("list-invalid-later-").toFile()
        PageCache.initializeDirectory(directory)
        try {
            listOf("<html>invalid</html>", html(2).replace("Page 2", "Rejected page")).forEach { badHtml ->
                withResponses(listOf(html(2))) { firstUrl ->
                    val now = System.currentTimeMillis()
                    val snapshotKey = store(firstUrl, ListPageCache.Snapshot("batch-a", 2, mapOf(
                        1 to ListPageCache.Page(now, html(1)),
                        2 to ListPageCache.Page(now, badHtml)
                    )))
                    val (session, _) = ListPageCache.open(authorization, firstUrl,
                        { page -> if (page == 1) firstUrl else "$firstUrl/$page.html" },
                        60_000L, PageKind.LIST, forceRefresh = false
                    ) { body, _ ->
                        if (body.contains("Rejected page")) throw IOException("Cached page cannot be parsed")
                        ParsedListPage(2, listOf(body))
                    }
                    assertEquals(listOf(html(2)), session.more(2))
                    val stored = Gson().fromJson(PageCache.readStale(snapshotKey), ListPageCache.Snapshot::class.java)
                    assertEquals("batch-a", stored.revision)
                    assertEquals(html(1), stored.pages[1]?.html)
                    assertEquals(html(2), stored.pages[2]?.html)
                }
            }
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun failedBadPageRecoveryPreservesBatchAndRetriesTheSamePage() {
        val directory = Files.createTempDirectory("list-bad-page-retry-").toFile()
        PageCache.initializeDirectory(directory)
        try {
            withResponses(listOf("<html>invalid</html>", html(2))) { firstUrl ->
                val now = System.currentTimeMillis()
                val snapshotKey = store(firstUrl, ListPageCache.Snapshot("batch-a", 2, mapOf(
                    1 to ListPageCache.Page(now, html(1)),
                    2 to ListPageCache.Page(now, "<html>invalid</html>")
                )))
                val (session, _) = open(firstUrl)
                val before = PageCache.readStale(snapshotKey)
                try {
                    session.more(2)
                    fail("An invalid replacement must report failure")
                } catch (_: IOException) { }
                assertEquals(before, PageCache.readStale(snapshotKey))
                assertTrue(session.isCurrent())
                assertEquals(2, session.pageCount)
                assertEquals(listOf(html(2)), session.more(2))
            }
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun warmFirstAndLaterPageHitsUpdateDiskRecencyWithoutChangingFreshness() {
        val directory = Files.createTempDirectory("list-memory-hit-recency-").toFile()
        PageCache.initializeDirectory(directory)
        try {
            store("batch-a")
            val (session, _) = open()
            val before = PageCache.readStale(key)
            val file = directory.listFiles()!!.single()
            val oldAccess = System.currentTimeMillis() - 600_000L
            assertTrue(file.setLastModified(oldAccess))
            val (reopened, _) = open()
            assertTrue(file.lastModified() > oldAccess)
            assertTrue(reopened.isCurrent())
            assertTrue(file.setLastModified(oldAccess))
            assertEquals(listOf(html(2)), session.more(2))
            assertTrue(file.lastModified() > oldAccess)
            assertEquals(before, PageCache.readStale(key))
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun networkBodyValidationErrorsAreNotHiddenByStalePages() {
        val directory = Files.createTempDirectory("page-validation-errors-").toFile()
        PageCache.initializeDirectory(directory)
        try {
            val refreshed = html(1).replace("Page 1", "Updated page")
            listOf(ChapterPasswordRequiredException(), IOException("Chapter content is empty")).forEach { failure ->
                withResponses(listOf(refreshed)) { firstUrl ->
                    val pageKey = EsjzoneClient.pageCacheKey(authorization, firstUrl)
                    assertTrue(PageCache.write(pageKey, html(1), System.currentTimeMillis() - 120_000L))
                    try {
                        EsjzoneClient.getPage(authorization, firstUrl, 60_000L,
                            pageKind = PageKind.LIST, awaitFresh = true,
                            validateBody = { if (it == refreshed) throw failure })
                        fail("Body validation failures must reach the caller")
                    } catch (error: IOException) {
                        assertSame(failure, error)
                    }
                    assertEquals(html(1), PageCache.readStale(pageKey))
                }
            }
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun transportFailuresStillReturnValidatedStalePages() {
        val directory = Files.createTempDirectory("page-network-fallback-").toFile()
        PageCache.initializeDirectory(directory)
        try {
            val port = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
            val firstUrl = "http://127.0.0.1:$port/list-01"
            val pageKey = EsjzoneClient.pageCacheKey(authorization, firstUrl)
            assertTrue(PageCache.write(pageKey, html(1), System.currentTimeMillis() - 120_000L))
            assertEquals(html(1), EsjzoneClient.getPage(authorization, firstUrl, 60_000L,
                pageKind = PageKind.LIST, awaitFresh = true))
        } finally { directory.deleteRecursively() }
    }

}
