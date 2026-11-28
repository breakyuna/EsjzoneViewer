package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.external.CloudflareChallengeRequiredException
import com.breakyuna.esjzone.offline.BackgroundDownloadStatus
import com.breakyuna.esjzone.offline.ChapterSelectionCodec
import com.breakyuna.esjzone.offline.DownloadProgress
import com.breakyuna.esjzone.offline.DownloadedChapterRecord
import com.breakyuna.esjzone.offline.DownloadedNovelManifest
import com.breakyuna.esjzone.offline.NovelDownloadManager
import com.breakyuna.esjzone.offline.NovelDownloadStore
import com.breakyuna.esjzone.offline.findCloudflareChallenge
import com.google.gson.Gson
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure contracts for resumable manifests and WorkManager's unique-work key. */
class NovelDownloadContractTest {

    private fun progressRecord(number: Int, downloaded: Boolean) = DownloadedChapterRecord(
        number - 1, "Chapter $number", "/forum/9001/$number.html", "chapter-$number.json", downloaded)

    @Test
    fun queuedDownload_restoresFullEmptyRefreshedAndDeduplicatedProgress() {
        val chapters = listOf(progressRecord(1, true), progressRecord(2, true), progressRecord(3, false))
        val fullTags = setOf(NovelDownloadManager.TOTAL_TAG_PREFIX + 3,
            NovelDownloadManager.SELECTION_TAG_PREFIX)
        assertNull(NovelDownloadManager.selectedChapterUrls(fullTags))
        assertEquals(DownloadProgress(2, 3, ""), NovelDownloadManager.restoredProgress(fullTags, chapters))
        assertEquals(DownloadProgress(3, 3, ""),
            NovelDownloadManager.restoredProgress(fullTags, chapters.map { it.copy(downloaded = true) }))

        val emptyTags = setOf(NovelDownloadManager.TOTAL_TAG_PREFIX + 12,
            NovelDownloadManager.SELECTION_TAG_PREFIX)
        assertEquals(DownloadProgress(0, 12, ""), NovelDownloadManager.restoredProgress(emptyTags, emptyList()))

        val refreshedTags = setOf(NovelDownloadManager.TOTAL_TAG_PREFIX + 2,
            NovelDownloadManager.SELECTION_TAG_PREFIX)
        assertEquals(DownloadProgress(2, 3, ""), NovelDownloadManager.restoredProgress(refreshedTags, chapters))

        val mixed = listOf(
            progressRecord(1, true),
            progressRecord(1, true),
            progressRecord(2, false).copy(bodyAvailable = true),
            progressRecord(3, true).copy(localOnly = true)
        )
        assertEquals(DownloadProgress(2, 2, ""), NovelDownloadManager.restoredProgress(emptySet(), mixed))
    }

    @Test
    fun queuedSelection_restoresItsOwnScopeAcrossMirrorAliases() {
        val selected = setOf("https://www.esjzone.one/forum/9001/1.html", "/forum/9001/3.html")
        val tags = setOf(NovelDownloadManager.TOTAL_TAG_PREFIX + 2,
            NovelDownloadManager.SELECTION_TAG_PREFIX + ChapterSelectionCodec.encode(selected))
        assertEquals(selected, NovelDownloadManager.selectedChapterUrls(tags))
        val chapters = listOf(progressRecord(1, true), progressRecord(2, true), progressRecord(3, false))
        assertEquals(DownloadProgress(1, 2, ""), NovelDownloadManager.restoredProgress(tags, chapters))
    }

    @Test
    fun queuedUnlock_restoresTargetsOutsideCatalogAndSavedUnlockedBodies() {
        val tags = setOf(NovelDownloadManager.UNLOCK_TAG,
            NovelDownloadManager.TOTAL_TAG_PREFIX + 2,
            NovelDownloadManager.SELECTION_TAG_PREFIX + ChapterSelectionCodec.encode(
                setOf("/forum/9001/1.html", "/forum/9001/3.html")))
        val chapters = listOf(progressRecord(1, true).copy(localOnly = true),
            progressRecord(2, true), progressRecord(3, false).copy(requiresPassword = true),
            progressRecord(4, false).copy(requiresPassword = true))

        assertEquals(DownloadProgress(1, 2, ""), NovelDownloadManager.restoredProgress(tags, chapters))
        assertEquals(DownloadProgress(2, 2, ""), NovelDownloadManager.restoredProgress(tags,
            chapters.map { if (it.index == 2) it.copy(downloaded = true, requiresPassword = false) else it }))

        val bodyAvailableTags = setOf(NovelDownloadManager.UNLOCK_TAG,
            NovelDownloadManager.TOTAL_TAG_PREFIX + 1,
            NovelDownloadManager.SELECTION_TAG_PREFIX + ChapterSelectionCodec.encode(setOf("/forum/9001/1.html")))
        val unlockedBody = progressRecord(1, false).copy(bodyAvailable = true, requiresPassword = false)
        assertEquals(DownloadProgress(1, 1, ""), NovelDownloadManager.restoredProgress(bodyAvailableTags, listOf(unlockedBody)))
    }

    @Test
    fun chapterSelection_survivesCompactWorkerInputForLargeTableOfContents() {
        val urls = (1..782).map { "/forum/9001/$it.html" }.toSet()
        val encoded = ChapterSelectionCodec.encode(urls)

        assertTrue(encoded.length < 8_000)
        assertEquals(urls, ChapterSelectionCodec.decode(encoded))
    }

    @Test
    fun chapterSelection_requiresEveryRequestedChapterInRefreshedContents() {
        val available = listOf("/forum/9001/1.html", "/forum/9001/2.html")
        assertEquals(null, NovelDownloadStore.selectionKeys(available, null))
        assertEquals(setOf(NovelDownloadStore.chapterKey(available[1])),
            NovelDownloadStore.selectionKeys(available, setOf(available[1])))
        assertThrows(IllegalArgumentException::class.java) {
            NovelDownloadStore.selectionKeys(available, setOf("/forum/9001/3.html"))
        }
    }

    @Test
    fun uniqueWorkName_isStableAcrossAliasesAndFragments() {
        val first = NovelDownloadManager.uniqueWorkName(
            "https://www.esjzone.cc/detail/9001.html#chapter-1"
        )
        val alias = NovelDownloadManager.uniqueWorkName(
            "https://esjzone.cc/detail/9001.html#chapter-9"
        )
        val otherNovel = NovelDownloadManager.uniqueWorkName(
            "https://www.esjzone.cc/detail/9002.html"
        )

        assertEquals(first, alias)
        assertNotEquals(first, otherNovel)
        assertTrue(first.startsWith("novel-download-"))
    }

    @Test
    fun manifestRoundTrip_preservesPartialStatePasswordsAndLegacyDefaults() {
        val manifest = DownloadedNovelManifest(
            name = "Fixture novel",
            url = "https://www.esjzone.cc/detail/9001.html",
            coverUrl = "",
            views = 12,
            likes = 3,
            words = 400,
            type = "原创",
            author = "Fixture author",
            forumUrl = "/forum/9001/1.html",
            tags = listOf("tag"),
            isAdult = false,
            description = "fixture",
            sourceUrl = null,
            updatedAt = null,
            chapters = listOf(
                DownloadedChapterRecord(0, "Chapter 1", "/forum/9001/1.html", "chapter-a.json", downloaded = true),
                DownloadedChapterRecord(1, "Chapter 2 (Protected)", "/forum/9001/2.html", "chapter-b.json", downloaded = false, requiresPassword = true),
                DownloadedChapterRecord(2, "Chapter 3 (Protected but Downloaded)", "/forum/9001/3.html", "chapter-c.json", downloaded = true, requiresPassword = true)
            ),
            downloadedAt = 1234L,
            complete = false,
            commonPassword = "test_common_secret"
        )

        val restored = Gson().fromJson(Gson().toJson(manifest), DownloadedNovelManifest::class.java)
        assertEquals(manifest, restored)
        assertEquals("chapter-a.json", restored.chapters.first().fileName)
        assertFalse(restored.complete)
        assertEquals("test_common_secret", restored.commonPassword)
        assertEquals(listOf("Chapter 2 (Protected)"), restored.pendingPasswordChapters.map { it.name })

        val legacyJson = """
            {
                "name": "Legacy Novel",
                "url": "https://www.esjzone.cc/detail/9001.html",
                "coverUrl": "",
                "views": 0,
                "likes": 0,
                "words": 0,
                "type": "",
                "author": "",
                "tags": [],
                "isAdult": false,
                "description": "",
                "chapters": [
                    {
                        "index": 0,
                        "name": "Chapter 1",
                        "url": "/forum/9001/1.html",
                        "fileName": "chapter-a.json",
                        "downloaded": false
                    }
                ],
                "downloadedAt": 0,
                "complete": false
            }
        """.trimIndent()

        val legacy = Gson().fromJson(legacyJson, DownloadedNovelManifest::class.java)
        assertFalse(legacy.chapters.first().requiresPassword)
        assertTrue(legacy.pendingPasswordChapters.isEmpty())
        assertNull(legacy.commonPassword)
    }

    @Test
    fun cloudflareChallengeDetection_unwrapsNestedCausesAndExposesStatusDefaults() {
        val challengeUrl = "https://www.wenku8.net/novel/1/2/3.htm"
        val direct = CloudflareChallengeRequiredException(challengeUrl)
        val wrapped = IllegalStateException("download failed", IOException("outer", direct))
        val nonCf = IllegalStateException("download failed", IOException("timeout"))

        assertEquals(direct, findCloudflareChallenge(direct))
        assertEquals(challengeUrl, findCloudflareChallenge(wrapped)?.chapterUrl)
        assertNull(findCloudflareChallenge(nonCf))

        val defaultStatus = BackgroundDownloadStatus(
            id = "work-1",
            running = false,
            finished = true,
            succeeded = false,
            progress = null
        )
        assertFalse(defaultStatus.wenkuVerificationRequired)
        assertNull(defaultStatus.wenkuVerificationUrl)

        val cfStatus = defaultStatus.copy(
            wenkuVerificationRequired = true,
            wenkuVerificationUrl = "https://www.wenku8.net/novel/1/2/3.htm"
        )
        assertTrue(cfStatus.wenkuVerificationRequired)
        assertEquals("https://www.wenku8.net/novel/1/2/3.htm", cfStatus.wenkuVerificationUrl)
    }
}
