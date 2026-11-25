package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.features.CloudViewReportStore
import com.breakyuna.esjzone.network.features.CloudViewReporter
import com.breakyuna.esjzone.network.features.CloudViewReporterHolder
import com.breakyuna.esjzone.network.features.SiteViewRecord
import com.breakyuna.esjzone.network.features.firstSiteChapter
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

    private fun otherBook() =
        HistoryNovel("Other", "/detail/34.html", "vid-34", Chapter("C", "/forum/34/1.html", true))

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
        val reporter = reporter(this, site, { listOf(otherBook(), history(site.latest!!)) })

        val fetch = reporter.beginBackgroundFetch(authorization, novelId, novelUrl)
        // The download worker requests chapters with the account cookies, often out of order.
        listOf(1, 5, 2, 9, 4).forEach { site.request("https://www.esjzone.cc/forum/12/$it.html") }
        assertEquals("https://www.esjzone.cc/forum/12/4.html", site.latest)

        var lookedUpFirst = false
        assertTrue(reporter.finishBackgroundFetch(fetch) { lookedUpFirst = true; "/forum/12/1.html" })
        assertFalse(lookedUpFirst)
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
    fun readingWhileTheInitialHistoryRequestIsInFlightWinsOverItsSnapshot() = runBlocking {
        for (hadSiteRecord in listOf(true, false)) {
            val site = FakeSite()
            var time = 1_000L
            lateinit var reports: CloudViewReporter
            reports = reporter(this, site, {
                val snapshot = if (hadSiteRecord) listOf(history("/forum/12/3.html")) else emptyList()
                // The response still describes the old state, but the reader has advanced.
                time = 2_000L
                site.request("https://www.esjzone.cc/forum/12/7.html")
                reports.noteFetched(authorization, novelId, novelUrl, "/forum/12/7.html")
                time = 3_000L
                snapshot
            }, clock = { time })

            val fetch = reports.beginBackgroundFetch(authorization, novelId, novelUrl)
            site.request("https://www.esjzone.cc/forum/12/9.html")
            time = 4_000L
            var lookedUpFirstChapter = false
            assertTrue(reports.finishBackgroundFetch(fetch) {
                lookedUpFirstChapter = true
                "/forum/12/1.html"
            })
            assertEquals("https://www.esjzone.cc/forum/12/7.html", site.latest)
            assertFalse(lookedUpFirstChapter)
        }
    }

    /** 阳's rule: the site had no record before the download, so it should end on chapter 1. */
    @Test
    fun aBookTheSiteHadNoRecordOfEndsOnTheFirstChapter() = runBlocking {
        for (historySnapshot in listOf(listOf(otherBook()), emptyList())) {
            val site = FakeSite()
            val reporter = reporter(this, site, { historySnapshot })
            val fetch = reporter.beginBackgroundFetch(authorization, novelId, novelUrl)
            assertEquals(SiteViewRecord.Absent, fetch?.siteRecord)
            listOf(4, 9).forEach { site.request("https://www.esjzone.cc/forum/12/$it.html") }
            assertTrue(reporter.finishBackgroundFetch(fetch) { "/forum/12/1.html" })
            assertEquals("https://www.esjzone.cc/forum/12/1.html", site.latest)
        }

        val site = FakeSite()
        val directory = java.nio.file.Files.createTempDirectory("cloud-view").toFile()
        try {
            val holder = CloudViewReporterHolder { reporter(this, site, { emptyList() }) }
            holder.preservingSiteView(directory, authorization, novelId, novelUrl, { "/forum/12/1.html" }) {
                listOf(1, 5, 9).forEach { site.request("https://www.esjzone.cc/forum/12/$it.html") }
            }
            assertEquals("https://www.esjzone.cc/forum/12/1.html", site.latest)
        } finally {
            directory.deleteRecursively()
        }

        val noFirstSite = FakeSite()
        val noFirstReporter = reporter(this, noFirstSite, { emptyList() })
        val noFirstFetch = noFirstReporter.beginBackgroundFetch(authorization, novelId, novelUrl)
        noFirstSite.request("https://www.esjzone.cc/forum/12/9.html")
        assertFalse(noFirstReporter.finishBackgroundFetch(noFirstFetch) { throw IllegalStateException("no manifest") })
        assertFalse(noFirstReporter.finishBackgroundFetch(noFirstFetch))
        assertEquals(listOf("https://www.esjzone.cc/forum/12/9.html"), noFirstSite.requests)
    }

    /** /my/view could not be read (or was not a history page): never reset to chapter 1. */
    @Test
    fun anUnknownSiteHistorySkipsTheRestore() = runBlocking {
        for (failure in listOf<() -> List<HistoryNovel>?>({ null }, { throw java.io.IOException("offline") })) {
            val site = FakeSite()
            val reporter = reporter(this, site, failure)
            val fetch = reporter.beginBackgroundFetch(authorization, novelId, novelUrl)
            assertEquals(SiteViewRecord.Unknown, fetch?.siteRecord)
            site.request("https://www.esjzone.cc/forum/12/9.html")
            var looked = false
            assertFalse(reporter.finishBackgroundFetch(fetch) { looked = true; "/forum/12/1.html" })
            assertFalse(looked)
            assertEquals(listOf("https://www.esjzone.cc/forum/12/9.html"), site.requests)
        }
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
    fun firstSiteChapterSkipsLinksThatAreNotChaptersOnTheLoggedInSite() {
        assertEquals("https://www.esjzone.cc/forum/12/1.html", firstSiteChapter("www.esjzone.cc",
            listOf("https://example.com/post/1", "https://www.esjzone.cc/detail/99.html", "/forum/12/1.html", "/forum/12/2.html")))
        assertNull(firstSiteChapter("www.esjzone.cc", listOf("https://example.com/post/1")))
        assertNull(firstSiteChapter("www.esjzone.cc", emptyList()))
    }

    /** WorkManager can resume a download in a process where MainActivity never ran, and failures must still restore. */
    @Test
    fun holderRestoresSitePositionOnColdResumeAndDownloadFailure() = runBlocking {
        val directory = java.nio.file.Files.createTempDirectory("cloud-view").toFile()
        try {
            val site = FakeSite().apply { latest = "https://www.esjzone.cc/forum/12/3.html" }
            val holder = CloudViewReporterHolder { reporter(this, site, { listOf(history(site.latest!!)) }) }
            assertNull(holder.current())

            holder.preservingSiteView(directory, authorization, novelId, novelUrl) {
                listOf(1, 5, 9).forEach { site.request("https://www.esjzone.cc/forum/12/$it.html") }
            }
            assertNotNull(holder.current())
            assertEquals("https://www.esjzone.cc/forum/12/3.html", site.latest)

            val failure = runCatching {
                holder.preservingSiteView(directory, authorization, novelId, novelUrl) {
                    site.request("https://www.esjzone.cc/forum/12/9.html")
                    throw java.io.IOException("network lost")
                }
            }
            assertTrue(failure.isFailure)
            assertEquals("https://www.esjzone.cc/forum/12/3.html", site.latest)
        } finally {
            directory.deleteRecursively()
        }
        Unit
    }

    /** A chapter opened or fetched while the restore request is in flight must not be dropped. */
    @Test
    fun aChapterReadDuringTheRestoreRequestIsStillReported() = runBlocking {
        for (fetched in listOf(false, true)) {
            val site = FakeSite().apply { latest = "https://www.esjzone.cc/forum/12/3.html" }
            var time = 1_000L
            lateinit var reporter: CloudViewReporter
            var readerMoved = false
            reporter = reporter(this, site, { listOf(history("/forum/12/3.html")) }, clock = { time }, inFlight = { url ->
                if (!readerMoved && url.endsWith("/forum/12/3.html")) {
                    readerMoved = true
                    time = 3_000L
                    if (fetched) {
                        site.request("https://www.esjzone.cc/forum/12/7.html")
                        reporter.noteFetched(authorization, novelId, novelUrl, "/forum/12/7.html")
                    } else {
                        reporter.noteLocal(authorization, novelId, novelUrl, "/forum/12/7.html")
                    }
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
    }

    private suspend fun awaitChildren() {
        while (coroutineContext.job.children.any()) {
            coroutineContext.job.children.toList().forEach { it.join() }
        }
    }
}
