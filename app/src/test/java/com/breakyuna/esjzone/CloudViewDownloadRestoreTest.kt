package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.features.CloudViewRecord
import com.breakyuna.esjzone.network.features.CloudViewReportStore
import com.breakyuna.esjzone.network.features.CloudViewReporterHolder
import com.breakyuna.esjzone.network.features.CloudViewReporter
import com.breakyuna.esjzone.network.features.cloudViewRestoreChapter
import com.breakyuna.esjzone.network.features.siteHistoryChapter
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.HistoryNovel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.coroutineContext

/**
 * A fake site: like ESJ Zone, every logged-in chapter request becomes the
 * novel's latest entry in /my/view, whoever made the request.
 */
private class FakeSite {
    var latest: String? = null
    val requests = mutableListOf<String>()
    fun request(url: String) {
        requests += url
        latest = url
    }
}

class CloudViewDownloadRestoreTest {
    private val authorization = Authorization("key", "token", "www.esjzone.cc")
    private val novelId = "12"
    private val novelUrl = "https://www.esjzone.cc/detail/12.html"

    private fun history(chapterUrl: String) =
        HistoryNovel("Book", "/detail/12.html", "vid-12", Chapter("Chapter", chapterUrl, true))

    private fun reporter(
        scope: CoroutineScope,
        site: FakeSite,
        siteHistory: () -> List<HistoryNovel>?,
        clock: () -> Long = System::currentTimeMillis,
        inFlight: (String) -> Unit = {}
    ) = CloudViewReporter(
        store = CloudViewReportStore(null),
        touch = { _, url, stillCurrent ->
            if (!stillCurrent()) {
                false
            } else {
                inFlight(url)
                site.request(url)
                true
            }
        },
        now = clock,
        siteHistory = { siteHistory() },
        scope = scope
    )

    /** Reproduces the report: downloading moved the site record to the last downloaded chapter. */
    @Test
    fun downloadingABookLeavesTheSiteOnTheChapterTheReaderWasAt() = runBlocking {
        val site = FakeSite()
        site.latest = "https://www.esjzone.cc/forum/12/3.html"
        val reporter = reporter(this, site, { listOf(history(site.latest!!)) })

        val fetch = reporter.beginBackgroundFetch(authorization, novelId, novelUrl)
        // The download worker requests chapters with the account cookies, often out of order.
        listOf(1, 5, 2, 9, 4).forEach { site.request("https://www.esjzone.cc/forum/12/$it.html") }
        assertEquals("https://www.esjzone.cc/forum/12/4.html", site.latest)

        assertTrue(reporter.finishBackgroundFetch(fetch))
        assertEquals("https://www.esjzone.cc/forum/12/3.html", site.latest)
    }

    @Test
    fun readingDuringTheDownloadWinsOverTheEarlierSiteRecord() = runBlocking {
        val site = FakeSite()
        site.latest = "https://www.esjzone.cc/forum/12/3.html"
        var time = 1_000L
        val reporter = reporter(this, site, { listOf(history("/forum/12/3.html")) }, clock = { time })

        val fetch = reporter.beginBackgroundFetch(authorization, novelId, novelUrl)
        time = 2_000L
        reporter.noteLocal(authorization, novelId, novelUrl, "/forum/12/6.html")
        awaitChildren()
        site.request("https://www.esjzone.cc/forum/12/9.html")

        time = 3_000L
        assertTrue(reporter.finishBackgroundFetch(fetch))
        assertEquals("https://www.esjzone.cc/forum/12/6.html", site.latest)
    }

    @Test
    fun restoreRunsEvenWhenReportingReadingIsTurnedOff() = runBlocking {
        val site = FakeSite()
        val reporter = CloudViewReporter(
            store = CloudViewReportStore(null),
            touch = { _, url, _ -> site.request(url); true },
            enabled = { false },
            siteHistory = { listOf(history("/forum/12/3.html")) },
            scope = this
        )
        val fetch = reporter.beginBackgroundFetch(authorization, novelId, novelUrl)
        site.request("https://www.esjzone.cc/forum/12/9.html")
        assertTrue(reporter.finishBackgroundFetch(fetch))
        assertEquals("https://www.esjzone.cc/forum/12/3.html", site.latest)
    }

    @Test
    fun nothingIsRequestedWhenTheBookWasNeverOnTheSiteOrInTheReporter() = runBlocking {
        val site = FakeSite()
        val reporter = reporter(this, site, { emptyList() })
        val fetch = reporter.beginBackgroundFetch(authorization, novelId, novelUrl)
        site.request("https://www.esjzone.cc/forum/12/9.html")
        assertFalse(reporter.finishBackgroundFetch(fetch))
        assertEquals(listOf("https://www.esjzone.cc/forum/12/9.html"), site.requests)
    }

    @Test
    fun aFailedSiteHistoryReadFallsBackToTheReporterRecord() = runBlocking {
        val site = FakeSite()
        var time = 1_000L
        val reporter = reporter(this, site, { throw java.io.IOException("offline") }, clock = { time })
        reporter.noteLocal(authorization, novelId, novelUrl, "/forum/12/2.html")
        awaitChildren()
        time = 2_000L
        val fetch = reporter.beginBackgroundFetch(authorization, novelId, novelUrl)
        site.request("https://www.esjzone.cc/forum/12/9.html")
        assertTrue(reporter.finishBackgroundFetch(fetch))
        assertEquals("https://www.esjzone.cc/forum/12/2.html", site.latest)
    }

    @Test
    fun loggedOutDownloadsNeedNoRestore() = runBlocking {
        val site = FakeSite()
        val reporter = reporter(this, site, { listOf(history("/forum/12/3.html")) })
        assertNull(reporter.beginBackgroundFetch(Authorization("", "", "www.esjzone.cc"), novelId, novelUrl))
        assertFalse(reporter.finishBackgroundFetch(null))
        assertTrue(site.requests.isEmpty())
    }

    @Test
    fun siteHistoryMatchesTheBookByIdAndIgnoresOtherBooks() {
        val rows = listOf(
            HistoryNovel("Other", "/detail/34.html", "vid-34", Chapter("C", "/forum/34/1.html", true)),
            history("/forum/12/7.html")
        )
        assertEquals("https://www.esjzone.cc/forum/12/7.html", siteHistoryChapter(rows, "www.esjzone.cc", "id:12"))
        assertNull(siteHistoryChapter(rows, "www.esjzone.cc", "id:56"))
    }

    @Test
    fun restoreChapterPrefersNewReaderReportsThenTheSiteThenOldReports() {
        fun record(chapter: String, at: Long) = CloudViewRecord("www.esjzone.cc", "id:12", "12", novelUrl,
            "/forum/12/$chapter.html", "https://www.esjzone.cc/forum/12/$chapter.html", true, at, "acct")
        assertEquals("https://www.esjzone.cc/forum/12/6.html",
            cloudViewRestoreChapter(record("6", 200), "https://www.esjzone.cc/forum/12/3.html", 100))
        assertEquals("https://www.esjzone.cc/forum/12/3.html",
            cloudViewRestoreChapter(record("2", 50), "https://www.esjzone.cc/forum/12/3.html", 100))
        assertEquals("https://www.esjzone.cc/forum/12/2.html",
            cloudViewRestoreChapter(record("2", 50), null, 100))
        assertNull(cloudViewRestoreChapter(null, " ", 100))
    }

    /** Codex P2: WorkManager can resume a download in a process where MainActivity never ran. */
    @Test
    fun aDownloadResumedWithoutTheAppUiStillRestoresTheSitePosition() = runBlocking {
        val site = FakeSite()
        site.latest = "https://www.esjzone.cc/forum/12/3.html"
        val directory = java.nio.file.Files.createTempDirectory("cloud-view").toFile()
        val holder = CloudViewReporterHolder { reporter(this, site, { listOf(history(site.latest!!)) }) }
        assertNull(holder.current())

        holder.preservingSiteView(directory, authorization, novelId, novelUrl) {
            listOf(1, 5, 9).forEach { site.request("https://www.esjzone.cc/forum/12/$it.html") }
        }

        assertNotNull(holder.current())
        assertEquals("https://www.esjzone.cc/forum/12/3.html", site.latest)
        directory.deleteRecursively()
        Unit
    }

    @Test
    fun theRestoreRunsWhenTheDownloadFails() = runBlocking {
        val site = FakeSite()
        site.latest = "https://www.esjzone.cc/forum/12/3.html"
        val directory = java.nio.file.Files.createTempDirectory("cloud-view").toFile()
        val holder = CloudViewReporterHolder { reporter(this, site, { listOf(history(site.latest!!)) }) }
        val failure = runCatching {
            holder.preservingSiteView(directory, authorization, novelId, novelUrl) {
                site.request("https://www.esjzone.cc/forum/12/9.html")
                throw java.io.IOException("network lost")
            }
        }
        assertTrue(failure.isFailure)
        assertEquals("https://www.esjzone.cc/forum/12/3.html", site.latest)
        directory.deleteRecursively()
        Unit
    }

    /** Codex P2: a chapter opened while the restore request is in flight must not be dropped. */
    @Test
    fun aChapterOpenedDuringTheRestoreRequestIsStillReported() = runBlocking {
        val site = FakeSite()
        site.latest = "https://www.esjzone.cc/forum/12/3.html"
        var time = 1_000L
        lateinit var reporter: CloudViewReporter
        var readerMoved = false
        reporter = reporter(this, site, { listOf(history("/forum/12/3.html")) }, clock = { time }, inFlight = { url ->
            if (!readerMoved && url.endsWith("/forum/12/3.html")) {
                readerMoved = true
                time = 3_000L
                reporter.noteLocal(authorization, novelId, novelUrl, "/forum/12/7.html")
            }
        })

        val fetch = reporter.beginBackgroundFetch(authorization, novelId, novelUrl)
        site.request("https://www.esjzone.cc/forum/12/9.html")
        time = 2_000L
        assertTrue(reporter.finishBackgroundFetch(fetch))
        awaitChildren()

        assertTrue(readerMoved)
        assertEquals("https://www.esjzone.cc/forum/12/7.html", site.latest)
    }

    @Test
    fun aChapterFetchedDuringTheRestoreRequestIsSentAgainAfterIt() = runBlocking {
        val site = FakeSite()
        site.latest = "https://www.esjzone.cc/forum/12/3.html"
        var time = 1_000L
        lateinit var reporter: CloudViewReporter
        var readerFetched = false
        reporter = reporter(this, site, { listOf(history("/forum/12/3.html")) }, clock = { time }, inFlight = { url ->
            if (!readerFetched && url.endsWith("/forum/12/3.html")) {
                readerFetched = true
                time = 3_000L
                // The reader's own page request reached the site before the restore did.
                site.request("https://www.esjzone.cc/forum/12/7.html")
                reporter.noteFetched(authorization, novelId, novelUrl, "/forum/12/7.html")
            }
        })

        val fetch = reporter.beginBackgroundFetch(authorization, novelId, novelUrl)
        site.request("https://www.esjzone.cc/forum/12/9.html")
        time = 2_000L
        assertTrue(reporter.finishBackgroundFetch(fetch))
        awaitChildren()

        assertTrue(readerFetched)
        assertEquals("https://www.esjzone.cc/forum/12/7.html", site.latest)
    }

    private suspend fun awaitChildren() {
        while (coroutineContext.job.children.any()) {
            coroutineContext.job.children.toList().forEach { it.join() }
        }
    }
}
