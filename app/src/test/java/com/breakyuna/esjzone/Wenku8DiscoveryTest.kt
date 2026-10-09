package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.wenku8.*
import org.junit.Assert.*
import org.junit.Test

/** Public DOM shapes observed on 2026-10-09; no account data or cookies. */
class Wenku8DiscoveryTest {
    @Test fun homeGroupsBooksDeduplicatesCoverAndTextAndKeepsFullTitle() {
        val html = """<div id=centers><div class=block><div class=blocktitle>新书风云榜</div>
            <div class=blockcontent><div><a href='/book/123.htm' tiptitle='完整书名（副标题）'>
            <img src='http://img.wenku8.com/image/0/123/123s.jpg'></a><br>
            <a href='/book/123.htm'>完整书名（副标题）</a></div></div></div></div>
            <div id=right><div class=block><div class=blocktitle><span class=txt>今日热榜</span></div>
            <div class=blockcontent><ul><li><a href='/book/123.htm' tiptitle='完整书名（副标题）'>截断书名</a></li>
            </ul><div class=more><a href='/modules/article/toplist.php?sort=dayvisit'>更多</a></div></div></div></div>"""
        val result = Wenku8Parsers.home(html, wenku8BrowseUrl(Wenku8Browse.HOME))
        assertEquals(listOf("新书风云榜", "今日热榜"), result.sections.map { it.title })
        assertEquals(1, result.sections.first().novels.size)
        assertEquals("完整书名（副标题）", result.sections.last().novels.single().name)
        assertEquals("https://img.wenku8.com/image/0/123/123s.jpg", result.sections.first().novels.single().coverUrl)
        assertThrows(Wenku8ParseException::class.java) {
            Wenku8Parsers.home("<div id=centers></div>", wenku8BrowseUrl(Wenku8Browse.HOME))
        }
        assertThrows(Wenku8LoginRequiredException::class.java) {
            Wenku8Parsers.home("<input name=username><input name=password type=password>", wenku8BrowseUrl(Wenku8Browse.HOME))
        }
    }

    @Test fun realListShapeUsesTiptitleFirstAuthorParagraphAndTags() {
        val html = """<div id=content><table><tr><td><div>
            <div><a href='/book/123.htm' tiptitle='完整标题'><img src='http://img.wenku8.com/image/0/123/123s.jpg'></a></div>
            <div><b><a href='/book/123.htm'>截断标题</a></b><p>作者:示例作者/分类:电击文库</p>
            <p>更新:2026-10-09/字数:100K/连载中</p><p>Tags:<span>奇幻 R18</span></p></div>
            </div></td></tr></table></div><div id=pagelink><em>2/3</em></div>"""
        val result = Wenku8Parsers.search(html, wenku8BrowseUrl(Wenku8Browse.POPULAR, 2), 2)
        assertEquals("完整标题", result.novels.single().name)
        assertEquals("示例作者", result.novels.single().author)
        assertTrue(result.novels.single().isAdult)
        assertEquals(3, result.totalPages)
    }

    @Test fun allBrowseBuildersAreAllowedButOtherEndpointsAndHostsAreRejected() {
        Wenku8Browse.entries.forEach {
            assertTrue(wenku8PageAllowed(wenku8BrowseUrl(it, 2),
                if (it == Wenku8Browse.HOME) Wenku8PageKind.HOME else Wenku8PageKind.BROWSE))
        }
        assertFalse(wenku8PageAllowed("https://www.wenku8.net/modules/article/toplist.php?sort=unknown", Wenku8PageKind.BROWSE))
        assertFalse(wenku8PageAllowed("https://www.wenku8.net/modules/article/addbookcase.php?bid=123", Wenku8PageKind.BROWSE))
        assertFalse(wenku8PageAllowed(wenku8BrowseUrl(Wenku8Browse.ALL).replace("www.wenku8.net", "example.org"), Wenku8PageKind.BROWSE))
    }
}
