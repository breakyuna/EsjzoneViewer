package com.breakyuna.esjzone

import com.breakyuna.esjzone.database.BookmarkCoverStore
import com.breakyuna.esjzone.database.copyBookmarkCover
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkCoverStoreTest {
    @Test fun cancelledCopyCannotOverwriteAnotherWritersPublishedCover() = runBlocking {
        withTimeout(10_000) {
            val directory = Files.createTempDirectory("bookmark-cover-race-").toFile()
            val target = File(directory, "cover.jpg")
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val old = launch(Dispatchers.IO) {
                copyBookmarkCover(target) { temporary ->
                    temporary.outputStream().use { output ->
                        output.write("old-start".toByteArray())
                        entered.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                        output.write("-old-end".toByteArray())
                    }
                }
            }
            try {
                assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
                old.cancel()
                assertTrue(withContext(Dispatchers.IO) {
                    copyBookmarkCover(target) { it.writeText("new-complete") }
                })
                assertEquals("new-complete", target.readText())
                release.countDown()
                old.join()
                assertEquals("new-complete", target.readText())
                assertEquals(listOf(target), directory.listFiles()!!.toList())
            } finally {
                release.countDown()
                old.join()
                directory.deleteRecursively()
            }
        }
    }

    @Test
    fun cleanNovelId_normalizesIdsDetailPathsAndChapterUrls() {
        assertEquals("1642059588", BookmarkCoverStore.cleanNovelId("1642059588"))
        assertEquals("1642059588", BookmarkCoverStore.cleanNovelId("/detail/1642059588.html"))
        val chapter1 = BookmarkCoverStore.cleanNovelId("", "https://www.esjzone.me/forum/1642059588/1.html")
        val chapter5 = BookmarkCoverStore.cleanNovelId("1642059588", "https://www.esjzone.me/forum/1642059588/5.html")
        val chapterDetail = BookmarkCoverStore.cleanNovelId("/detail/1642059588.html", "")
        assertEquals("1642059588", chapter1)
        assertEquals(chapter1, chapter5)
        assertEquals(chapter1, chapterDetail)
    }
}
