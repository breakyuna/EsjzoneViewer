package com.breakyuna.esjzone

import com.breakyuna.esjzone.offline.DownloadedChapterRecord
import com.breakyuna.esjzone.offline.DownloadedNovelManifest
import com.breakyuna.esjzone.offline.NovelDownloadManager
import com.breakyuna.esjzone.offline.ChapterSelectionCodec
import com.breakyuna.esjzone.offline.NovelDownloadStore
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure contracts for resumable manifests and WorkManager's unique-work key. */
class NovelDownloadContractTest {

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
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
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
    fun manifestRoundTrip_preservesPartialStateAndChapterFileIdentity() {
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
                DownloadedChapterRecord(
                    index = 0,
                    name = "Chapter 1",
                    url = "/forum/9001/1.html",
                    fileName = "chapter-a.json",
                    downloaded = true
                ),
                DownloadedChapterRecord(
                    index = 1,
                    name = "Chapter 2",
                    url = "/forum/9001/2.html",
                    fileName = "chapter-b.json",
                    downloaded = false
                )
            ),
            downloadedAt = 1234L,
            complete = false
        )

        val restored = Gson().fromJson(Gson().toJson(manifest), DownloadedNovelManifest::class.java)
        assertEquals(manifest, restored)
        assertEquals("chapter-a.json", restored.chapters.first().fileName)
        assertTrue(restored.chapters.first().downloaded)
        assertTrue(!restored.complete)
    }

    @Test
    fun downloadConcurrency_defaultsToFive() {
        assertEquals(5, NovelDownloadStore.DEFAULT_DOWNLOAD_CONCURRENCY)
    }

    @Test
    fun manifestRoundTrip_preservesRequiresPasswordAndPendingPasswordChapters() {
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
                DownloadedChapterRecord(
                    index = 0,
                    name = "Chapter 1",
                    url = "/forum/9001/1.html",
                    fileName = "chapter-a.json",
                    downloaded = true,
                    requiresPassword = false
                ),
                DownloadedChapterRecord(
                    index = 1,
                    name = "Chapter 2 (Protected)",
                    url = "/forum/9001/2.html",
                    fileName = "chapter-b.json",
                    downloaded = false,
                    requiresPassword = true
                ),
                DownloadedChapterRecord(
                    index = 2,
                    name = "Chapter 3 (Protected but Downloaded)",
                    url = "/forum/9001/3.html",
                    fileName = "chapter-c.json",
                    downloaded = true,
                    requiresPassword = true
                )
            ),
            downloadedAt = 1234L,
            complete = false
        )

        val restored = Gson().fromJson(Gson().toJson(manifest), DownloadedNovelManifest::class.java)
        assertEquals(manifest, restored)
        assertTrue(restored.chapters[1].requiresPassword)
        // Chapter 2 requires password and is not downloaded -> in pending
        // Chapter 3 is downloaded -> excluded from pendingPasswordChapters
        assertEquals(1, restored.pendingPasswordChapters.size)
        assertEquals("Chapter 2 (Protected)", restored.pendingPasswordChapters.first().name)
    }

    @Test
    fun legacyManifestWithoutRequiresPassword_defaultsToFalse() {
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

        val restored = Gson().fromJson(legacyJson, DownloadedNovelManifest::class.java)
        assertEquals(false, restored.chapters.first().requiresPassword)
        assertTrue(restored.pendingPasswordChapters.isEmpty())
    }

    @Test
    fun manifestRoundTrip_preservesCommonPassword() {
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
                DownloadedChapterRecord(
                    index = 0,
                    name = "Chapter 1",
                    url = "/forum/9001/1.html",
                    fileName = "chapter-a.json",
                    downloaded = false,
                    requiresPassword = true
                )
            ),
            downloadedAt = 1234L,
            complete = false,
            commonPassword = "test_common_secret"
        )

        val restored = Gson().fromJson(Gson().toJson(manifest), DownloadedNovelManifest::class.java)
        assertEquals(manifest, restored)
        assertEquals("test_common_secret", restored.commonPassword)
    }

    @Test
    fun legacyManifestWithoutCommonPassword_defaultsToNull() {
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
                "chapters": [],
                "downloadedAt": 0,
                "complete": false
            }
        """.trimIndent()

        val restored = Gson().fromJson(legacyJson, DownloadedNovelManifest::class.java)
        org.junit.Assert.assertNull(restored.commonPassword)
    }
}
