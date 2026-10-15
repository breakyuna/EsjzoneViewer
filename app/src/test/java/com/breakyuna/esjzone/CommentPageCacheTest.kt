package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.PageCache
import com.breakyuna.esjzone.network.PageCacheTtl
import com.breakyuna.esjzone.network.features.getCachedCommentPageSnapshot
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class CommentPageCacheTest {
    @Test
    fun initialCommentsReadIsCacheOnlyAndAcceptsStaleCommentsForDisplay() {
        val directory = Files.createTempDirectory("comment-cache-").toFile()
        PageCache.initializeDirectory(directory)
        val auth = Authorization()
        // No server is running: a cache-only miss must simply return null.
        val url = "http://127.0.0.1:1/forum/9001/1.html"
        val key = EsjzoneClient.pageCacheKey(auth, url)
        try {
            assertNull(EsjzoneClient.getCachedCommentPageSnapshot(auth, url))
            val html = requireNotNull(javaClass.getResource("/esj/comments-pages.html"))
                .readText()
            assertTrue(PageCache.write(key, html,
                System.currentTimeMillis() - PageCacheTtl.COMMUNITY - 1_000L))
            val snapshot = requireNotNull(EsjzoneClient.getCachedCommentPageSnapshot(auth, url))
            assertEquals(16, snapshot.comments.size)
            assertEquals("8101", snapshot.comments.first().id)
            assertEquals("Comment 16", snapshot.comments.last().contentText)

            assertTrue(PageCache.write(key, "<html><title>Just a moment</title></html>"))
            assertNull(EsjzoneClient.getCachedCommentPageSnapshot(auth, url))
            PageCache.clear()
            assertNull(EsjzoneClient.getCachedCommentPageSnapshot(auth, url))
        } finally { directory.deleteRecursively() }
    }
}
