package com.breakyuna.esjzone

import com.breakyuna.esjzone.offline.DownloadedChapterRecord
import com.breakyuna.esjzone.offline.DownloadedNovelManifest
import com.breakyuna.esjzone.offline.NovelDownloadManager
import com.breakyuna.esjzone.offline.NovelDownloadStore
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure contracts for resumable manifests and WorkManager's unique-work key. */
class NovelDownloadContractTest {

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
}
