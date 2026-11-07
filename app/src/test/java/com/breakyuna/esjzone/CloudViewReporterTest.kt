package com.breakyuna.esjzone

import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.features.CloudViewRecord
import com.breakyuna.esjzone.network.features.CloudViewReportStore
import com.breakyuna.esjzone.network.features.CloudViewReporter
import com.breakyuna.esjzone.network.features.HistoryDataCache
import com.breakyuna.esjzone.network.features.chapterViewUrl
import com.breakyuna.esjzone.network.features.cloudViewFetchIsCurrent
import com.breakyuna.esjzone.network.features.shouldRequestCloudView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.coroutines.coroutineContext

class CloudViewReporterTest {
    private val authorization = Authorization("key", "token", "www.esjzone.cc")

    @Test
    fun downloadedReadingRequestsOnlyTheLatestChapterOnTheLoggedInSite() = runBlocking {
        val requested = mutableListOf<String>()
        val reporter = reporter(this, requested)
        reporter.noteLocal(authorization, "12", "/detail/12.html", "https://www.esjzone.one/forum/12/1.html")
        reporter.noteLocal(authorization, "12", "/detail/12.html", "/forum/12/2.html")
        reporter.noteLocal(authorization, "34", "/detail/34.html", "/forum/34/8.html")
        awaitChildren()
        assertEquals(
            listOf(
                "https://www.esjzone.cc/forum/12/2.html",
                "https://www.esjzone.cc/forum/34/8.html"
            ),
            requested
        )
        reporter.noteLocal(authorization, "12", "/detail/12.html", "/forum/12/2.html")
        awaitChildren()
        assertEquals(2, requested.size)
    }

    @Test
    fun networkReadReplacesAPendingChapterWithoutAnotherRequest() = runBlocking {
        val requested = mutableListOf<String>()
        val reporter = reporter(this, requested)
        reporter.noteLocal(authorization, "12", "/detail/12.html", "/forum/12/1.html")
        reporter.noteFetched(authorization, "12", "/detail/12.html", "/forum/12/4.html")
        awaitChildren()
        assertTrue(requested.isEmpty())
    }

    @Test
    fun failedReportStaysPendingAndDoesNotBlockTheNextBookForever() = runBlocking {
        val requested = mutableListOf<String>()
        val reporter = reporter(this, requested) { url -> !url.contains("/forum/12/") }
        reporter.noteLocal(authorization, "12", "/detail/12.html", "/forum/12/1.html")
        reporter.noteLocal(authorization, "34", "/detail/34.html", "/forum/34/8.html")
        awaitChildren()
        assertEquals(
            listOf(
                "https://www.esjzone.cc/forum/12/1.html",
                "https://www.esjzone.cc/forum/34/8.html"
            ),
            requested
        )
        reporter.flush(authorization)
        awaitChildren()
        assertEquals(
            listOf(
                "https://www.esjzone.cc/forum/12/1.html",
                "https://www.esjzone.cc/forum/34/8.html",
                "https://www.esjzone.cc/forum/12/1.html"
            ),
            requested
        )
    }

    @Test
    fun newerLocalHistoryDropsAStalePendingChapter() = runBlocking {
        val requested = mutableListOf<String>()
        var clock = 100L
        val reporter = CloudViewReporter(
            store = CloudViewReportStore(null),
            touch = { _, url, _ -> requested += url; true },
            latestActivity = { _, _ ->
                activity(chapterUrl = "/forum/12/9.html", lastReadAt = 500L)
            },
            now = { clock },
            scope = this
        )
        reporter.noteLocal(authorization, "12", "/detail/12.html", "/forum/12/1.html")
        awaitChildren()
        assertTrue(requested.isEmpty())
        clock = 600L
        reporter.noteLocal(authorization, "12", "/detail/12.html", "/forum/12/1.html")
        awaitChildren()
        assertEquals(listOf("https://www.esjzone.cc/forum/12/1.html"), requested)
    }

    @Test
    fun disabledReportingKeepsANetworkChapterFromBeingReplayedLater() = runBlocking {
        val requested = mutableListOf<String>()
        var enabled = false
        val reporter = CloudViewReporter(
            store = CloudViewReportStore(null),
            touch = { _, url, _ -> requested += url; true },
            enabled = { enabled },
            scope = this
        )
        reporter.noteLocal(authorization, "12", "/detail/12.html", "/forum/12/1.html")
        reporter.noteFetched(authorization, "12", "/detail/12.html", "/forum/12/4.html")
        enabled = true
        reporter.flush(authorization)
        awaitChildren()
        assertTrue(requested.isEmpty())
    }

    @Test
    fun anotherAccountDoesNotReceiveOrSuppressThePreviousReport() = runBlocking {
        val requested = mutableListOf<String>()
        val reporter = reporter(this, requested)
        val other = Authorization("other-key", "other-token", "www.esjzone.cc")
        reporter.noteLocal(authorization, "12", "/detail/12.html", "/forum/12/1.html")
        awaitChildren()
        requested.clear()
        reporter.flush(other)
        awaitChildren()
        assertTrue(requested.isEmpty())
        reporter.noteFetched(authorization, "12", "/detail/12.html", "/forum/12/4.html")
        reporter.noteLocal(other, "12", "/detail/12.html", "/forum/12/4.html")
        awaitChildren()
        assertEquals(listOf("https://www.esjzone.cc/forum/12/4.html"), requested)
    }

    @Test
    fun confirmationAdvancesOnlyWhenTheConfirmedChapterChanges() = runBlocking {
        val reporter = reporter(this, mutableListOf())
        val other = Authorization("other-key", "other-token", "www.esjzone.cc")
        assertFalse(reporter.fetchIsCurrent(authorization, "12", "/detail/12.html", "/forum/12/1.html", null))
        assertTrue(reporter.fetchIsCurrent(authorization, "12", "/detail/12.html", "/forum/12/1.html", 0))
        reporter.noteFetched(authorization, "12", "/detail/12.html", "/forum/12/1.html")
        assertEquals(1, reporter.confirmation(authorization, "12", "/detail/12.html", "/forum/12/1.html"))
        assertFalse(reporter.fetchIsCurrent(authorization, "12", "/detail/12.html", "/forum/12/1.html", 0))
        reporter.noteFetched(authorization, "12", "/detail/12.html", "/forum/12/1.html")
        assertEquals(1, reporter.confirmation(authorization, "12", "/detail/12.html", "/forum/12/1.html"))
        reporter.observeNetworkFetch(authorization, "34", "/detail/34.html", "/forum/34/8.html")
        assertTrue(reporter.fetchIsCurrent(authorization, "12", "/detail/12.html", "/forum/12/1.html", 1))
        reporter.noteLocal(authorization, "12", "/detail/12.html", "/forum/12/2.html")
        assertEquals(2, reporter.confirmation(authorization, "12", "/detail/12.html", "/forum/12/1.html"))
        assertEquals(0, reporter.confirmation(other, "12", "/detail/12.html", "/forum/12/1.html"))
        assertFalse(cloudViewFetchIsCurrent(null, 0))
        assertTrue(cloudViewFetchIsCurrent(2, 2))
    }

    @Test
    fun laterFetchMakesThePreviouslyReportedChapterPendingAgain() = runBlocking {
        val requested = mutableListOf<String>()
        val reporter = reporter(this, requested)
        reporter.noteFetched(authorization, "12", "/detail/12.html", "/forum/12/1.html")
        reporter.noteLocal(authorization, "12", "/detail/12.html", "/forum/12/1.html")
        awaitChildren()
        assertTrue(requested.isEmpty())
        reporter.observeNetworkFetch(authorization, "34", "/detail/34.html", "/forum/34/8.html")
        assertNull(reporter.observeNetworkFetch(
            authorization, "12", "/detail/12.html", "https://www.wenku8.net/novel/1/12/2.htm"
        ))
        reporter.noteLocal(authorization, "12", "/detail/12.html", "/forum/12/1.html")
        awaitChildren()
        assertTrue(requested.isEmpty())
        reporter.observeNetworkFetch(authorization, "12", "/detail/12.html", "/forum/12/2.html")
        reporter.noteLocal(authorization, "12", "/detail/12.html", "/forum/12/1.html")
        awaitChildren()
        assertEquals(listOf("https://www.esjzone.cc/forum/12/1.html"), requested)
    }

    @Test
    fun restoredGenerationDoesNotMaskANewerFetchAfterRestart() = runBlocking {
        val file = File.createTempFile("cloud-view-restart", ".json")
        file.deleteOnExit()
        val account = HistoryDataCache.scopeFor(authorization)!!
        CloudViewReportStore(file).merge(CloudViewRecord(
            "www.esjzone.cc", "id:12", "12", "/detail/12.html", "/forum/12/1.html",
            "https://www.esjzone.cc/forum/12/1.html", true, 100L, account, 1
        ))
        val requested = mutableListOf<String>()
        val reporter = CloudViewReporter(
            store = CloudViewReportStore(file),
            touch = { _, url, _ -> requested += url; true },
            scope = this
        )
        reporter.observeNetworkFetch(authorization, "12", "/detail/12.html", "/forum/12/2.html")
        reporter.noteLocal(authorization, "12", "/detail/12.html", "/forum/12/1.html")
        awaitChildren()
        assertEquals(listOf("https://www.esjzone.cc/forum/12/1.html"), requested)
    }

    @Test
    fun newerChapterConfirmedBeforeTheRequestCancelsTheStaleReport() = runBlocking {
        val requested = mutableListOf<String>()
        lateinit var reporter: CloudViewReporter
        reporter = CloudViewReporter(
            store = CloudViewReportStore(null),
            touch = touch@{ _, url, stillCurrent ->
                reporter.noteFetched(authorization, "12", "/detail/12.html", "/forum/12/4.html")
                if (!stillCurrent()) return@touch false
                requested += url
                true
            },
            scope = this
        )
        reporter.noteLocal(authorization, "12", "/detail/12.html", "/forum/12/1.html")
        awaitChildren()
        assertTrue(requested.isEmpty())
    }

    @Test
    fun staleReportRequestsTheChapterConfirmedWhileItWasInFlight() = runBlocking {
        val requested = mutableListOf<String>()
        lateinit var reporter: CloudViewReporter
        reporter = CloudViewReporter(
            store = CloudViewReportStore(null),
            touch = touch@{ _, url, stillCurrent ->
                if (!stillCurrent()) return@touch false
                requested += url
                if (url.endsWith("/forum/12/1.html")) {
                    reporter.noteFetched(authorization, "12", "/detail/12.html", "/forum/12/4.html")
                }
                true
            },
            scope = this
        )
        reporter.noteLocal(authorization, "12", "/detail/12.html", "/forum/12/1.html")
        awaitChildren()
        assertEquals(
            listOf(
                "https://www.esjzone.cc/forum/12/1.html",
                "https://www.esjzone.cc/forum/12/4.html"
            ),
            requested
        )
    }

    @Test
    fun savedReportWithoutAnAccountIsDropped() {
        val file = File.createTempFile("cloud-view", ".json")
        file.deleteOnExit()
        val account = HistoryDataCache.scopeFor(authorization)
        assertTrue(!account.isNullOrBlank())
        val store = CloudViewReportStore(file)
        assertTrue(store.merge(CloudViewRecord(
            "www.esjzone.cc", "id:12", "12", "/detail/12.html", "/forum/12/1.html",
            "https://www.esjzone.cc/forum/12/1.html", false, 100L, account!!
        )))
        assertEquals(account, CloudViewReportStore(file).snapshot().single().account)
        file.writeText("""[{"site":"www.esjzone.cc","novelKey":"id:12","novelId":"12","novelUrl":"/detail/12.html","chapterKey":"/forum/12/1.html","chapterUrl":"https://www.esjzone.cc/forum/12/1.html","reported":true,"updatedAt":1}]""")
        assertTrue(CloudViewReportStore(file).snapshot().isEmpty())
    }

    @Test
    fun externalChapterIsNotAWebsiteView() {
        assertNull(chapterViewUrl("www.esjzone.cc", "https://example.com/read/1"))
        val record = com.breakyuna.esjzone.network.features.CloudViewRecord(
            "www.esjzone.cc", "id:12", "12", "/detail/12.html", "/forum/12/1.html",
            "https://www.esjzone.cc/forum/12/1.html", false, 100L
        )
        assertTrue(shouldRequestCloudView(record, null))
        assertTrue(shouldRequestCloudView(record, activity(chapterUrl = "/forum/12/1.html", lastReadAt = 1L)))
    }

    private fun reporter(
        scope: CoroutineScope,
        requested: MutableList<String>,
        accept: (String) -> Boolean = { true }
    ) = CloudViewReporter(
        store = CloudViewReportStore(null),
        touch = { _, url, _ ->
            requested += url
            accept(url)
        },
        scope = scope
    )

    private fun activity(chapterUrl: String, lastReadAt: Long) = LocalReadingActivity(
        activityId = "12",
        novelId = "12",
        novelName = "Book",
        novelUrl = "/detail/12.html",
        chapterUrl = chapterUrl,
        chapterName = "Chapter",
        chapterIndex = 0,
        totalChapters = 10,
        chapterProgress = 0f,
        startedAt = 0L,
        lastReadAt = lastReadAt,
        durationMs = 0L
    )

    private suspend fun awaitChildren() {
        while (coroutineContext.job.children.any()) {
            coroutineContext.job.children.toList().forEach { it.join() }
        }
    }
}
