package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.wenku8.Wenku8PageKind
import com.breakyuna.esjzone.network.wenku8.Wenku8Parsers
import com.breakyuna.esjzone.network.wenku8.Wenku8ParseException
import com.breakyuna.esjzone.network.wenku8.Wenku8LoginRequiredException
import com.breakyuna.esjzone.network.wenku8.Wenku8RestrictedException
import com.breakyuna.esjzone.network.wenku8.Wenku8SearchRateLimitException
import com.breakyuna.esjzone.network.wenku8.Wenku8SearchType
import com.breakyuna.esjzone.network.wenku8.Wenku8Urls
import com.breakyuna.esjzone.network.wenku8.wenku8PageAllowed
import com.breakyuna.esjzone.network.wenku8.wenku8PageCacheIdentity
import com.breakyuna.esjzone.network.wenku8.wenku8SearchUrl
import com.breakyuna.esjzone.novellibrary.component.ChapterListItem
import com.breakyuna.esjzone.novellibrary.novel.NovelChapterList
import com.breakyuna.esjzone.novellibrary.novel.preview
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic fixtures and reduced MHT structure; these tests do not establish device compatibility. */
class Wenku8ParsersTest {
    private val book = Wenku8Urls.detail("2552")
    private val catalog = Wenku8Urls.catalog("2552")

    @Test fun loginFormIsReportedAsSignInRequiredAcrossBusinessPages() {
        val html = "<form><input name=username><input name=password type=password></form>"
        assertThrows(Wenku8LoginRequiredException::class.java) {
            Wenku8Parsers.home(html, "${Wenku8Urls.BASE}/index.php")
        }
        assertThrows(Wenku8LoginRequiredException::class.java) {
            Wenku8Parsers.search(html, wenku8SearchUrl("fixture", Wenku8SearchType.TITLE, 1), 1)
        }
        assertThrows(Wenku8LoginRequiredException::class.java) {
            Wenku8Parsers.detail(html, book, NovelChapterList(emptyList()))
        }
        assertThrows(Wenku8LoginRequiredException::class.java) {
            Wenku8Parsers.catalog(html, catalog)
        }
    }

    @Test fun groupedCatalogPreservesAllRowsAndRejectsOtherBooks() {
        val html = """<html><body><table>
            <tr><td class=vcss vid=1>第一卷</td></tr>
            <tr><td><a href=1.htm>第一章</a></td><td><a href=2.htm>第二章</a></td></tr>
            <tr><td class=vcss vid=2>第二卷</td></tr>
            <tr><td><a href=3.htm>第三章</a></td></tr>
            </table></body></html>"""
        val parsed = Wenku8Parsers.catalog(html, catalog)
        assertEquals(listOf("第一章", "第二章", "第三章"), parsed.orderedChapters.map { it.name })
        assertEquals(listOf("第一卷", "第二卷"), parsed.items.filterIsInstance<ChapterListItem>().map { it.name.text })
        assertEquals("https://www.wenku8.net/novel/2/2552/2.htm", parsed.orderedChapters[1].url)
        assertThrows(Wenku8ParseException::class.java) {
            Wenku8Parsers.catalog(html.replace("3.htm", "/novel/2/2553/3.htm"), catalog)
        }
    }

    @Test fun detailKeepsParenthesizedTitleAndUsesLocalIdentity() {
        val parsed = Wenku8Parsers.detail(detailHtml(), book, NovelChapterList(emptyList()))
        assertEquals("示例（原书名）", parsed.name)
        assertEquals("作者", parsed.author)
        assertEquals("wenku8:2552", parsed.id())
        assertEquals("", parsed.forumUrl)
        assertEquals(book, parsed.sourceUrl)
        assertEquals(12345, parsed.words)
        assertEquals("文库 · 已完结", parsed.type)
        assertEquals("2026-10-09", parsed.updatedAt)
        assertEquals(listOf("冒险", "幻想"), parsed.tags)
        assertEquals("https://www.wenku8.net/cover.jpg", parsed.coverUrl)
        assertEquals("简介正文", parsed.description.preview())
    }

    @Test fun suppliedDetailStructureMapsBookMetadataWithoutAuxiliaryText() {
        val url = Wenku8Urls.detail("1414")
        val parsed = Wenku8Parsers.detail(savedDetailHtml(), url, NovelChapterList(emptyList()))
        assertEquals("魔法少女育成计划", parsed.name)
        assertEquals("wenku8:1414", parsed.id())
        assertEquals("远藤浅蜊", parsed.author)
        assertEquals("其他文库 · 已完结", parsed.type)
        assertEquals("2025-10-29", parsed.updatedAt)
        assertEquals(2279994, parsed.words)
        assertEquals(listOf("魔法", "战斗", "大逃杀", "黑暗", "猎奇", "百合"), parsed.tags)
        assertEquals("https://img.wenku8.com/image/1/1414/1414s.jpg", parsed.coverUrl)
        assertEquals(0, parsed.views)
        assertEquals(0, parsed.likes)
        assertEquals("", parsed.forumUrl)
        assertEquals(url, parsed.sourceUrl)
        val description = parsed.description.preview(Int.MAX_VALUE)
        assertTrue(description.startsWith("大人气社交游戏“魔法少女育成计划”"))
        assertTrue(description.contains("魔法惊险战斗故事！"))
        assertFalse(description.contains("热度"))
        assertFalse(description.contains("第十一卷"))
        assertFalse(description.contains("内容简介："))
    }

    @Test fun suppliedDetailStructureIsRecognizedAsSingleSearchHit() {
        val result = Wenku8Parsers.search(savedDetailHtml(),
            wenku8SearchUrl("魔法少女育成计划", Wenku8SearchType.TITLE, 1), 1)
        assertEquals(1, result.totalPages)
        val novel = result.novels.single()
        assertEquals(Wenku8Urls.detail("1414"), novel.url)
        assertEquals("魔法少女育成计划", novel.name)
        assertEquals("远藤浅蜊", novel.author)
        assertEquals("https://img.wenku8.com/image/1/1414/1414s.jpg", novel.coverUrl)
    }

    @Test fun searchSeparatesSingleHitRateLimitAndUnknownEmptyPage() {
        val single = detailHtml()
        val url = wenku8SearchUrl("示例", Wenku8SearchType.TITLE, 1)
        assertEquals(book, Wenku8Parsers.search(single, url, 1).novels.single().url)
        val adultSingle = Wenku8Parsers.search(single.replace("冒险 幻想", "R18"), url, 1).novels.single()
        assertTrue(adultSingle.isAdult)
        assertEquals("作者", adultSingle.author)
        assertEquals("https://www.wenku8.net/cover.jpg", adultSingle.coverUrl)
        assertThrows(Wenku8SearchRateLimitException::class.java) {
            Wenku8Parsers.search("<p>两次搜索的间隔时间不得少于 5 秒</p>", url, 1)
        }
        assertThrows(Wenku8ParseException::class.java) { Wenku8Parsers.search("<div id=content></div>", url, 1) }
        assertThrows(Wenku8RestrictedException::class.java) {
            Wenku8Parsers.detail("<p>因版权问题</p>", book, NovelChapterList(emptyList()))
        }
    }

    @Test fun searchListUsesCurrentPageAndPreservesCardTitles() {
        val html = """<div id=content><table><tr><td><div>
            <div><a href='/book/2552.htm' title='示例（原书名）'><img src='/cover.jpg'></a></div>
            <div><p>title</p><p>作者:作者/文库:文库</p><p>metadata</p><p><span>冒险 幻想</span></p><p>简介:简介</p></div>
            </div></td></tr></table></div><div id=pagelink><em>2/3</em></div>"""
        val result = Wenku8Parsers.search(html, wenku8SearchUrl("示例", Wenku8SearchType.TITLE, 2), 2)
        assertEquals(2, result.page)
        assertEquals(3, result.totalPages)
        assertEquals("示例（原书名）", result.novels.single().name)
        assertEquals("作者", result.novels.single().author)
        assertThrows(Wenku8ParseException::class.java) {
            Wenku8Parsers.search(html.replace("2/3", "1/3"), wenku8SearchUrl("示例", Wenku8SearchType.TITLE, 2), 2)
        }
    }

    @Test fun searchEncodingIsAppliedOnceAndRedirectsRemainSourceRestricted() {
        val url = wenku8SearchUrl("中 +", Wenku8SearchType.AUTHOR, 2)
        assertTrue(url.contains("searchkey=%D6%D0+%2B"))
        assertFalse(url.contains("%25D6"))
        assertEquals("author", url.toHttpUrl().queryParameter("searchtype"))
        assertTrue(wenku8PageAllowed(url, Wenku8PageKind.SEARCH))
        assertTrue(wenku8PageAllowed(book, Wenku8PageKind.SEARCH))
        assertFalse(wenku8PageAllowed(book.replace(".net", ".net.example.org"), Wenku8PageKind.SEARCH))
        assertFalse(wenku8PageAllowed("https://www.wenku8.net/login.php", Wenku8PageKind.DETAIL))
    }

    @Test fun chineseSearchKeywordsRemainDistinctInTheCache() {
        val first = wenku8SearchUrl("中国", Wenku8SearchType.TITLE, 1)
        val second = wenku8SearchUrl("中文", Wenku8SearchType.TITLE, 1)
        assertNotEquals(wenku8PageCacheIdentity(first), wenku8PageCacheIdentity(second))
        assertEquals(first, wenku8PageCacheIdentity("$first#anchor"))
        assertNotEquals(wenku8PageCacheIdentity(first),
            wenku8PageCacheIdentity(wenku8SearchUrl("中国", Wenku8SearchType.AUTHOR, 1)))
        assertNotEquals(wenku8PageCacheIdentity(first),
            wenku8PageCacheIdentity(wenku8SearchUrl("中国", Wenku8SearchType.TITLE, 2)))
    }

    private fun savedDetailHtml(): String = requireNotNull(javaClass.getResource("/wenku8/detail.html"))
        .readText(Charsets.UTF_8)

    private fun detailHtml() = """<html><body><div id=content><div>
        <table><tr><td><table><tr><td><span><b>示例（原书名）</b></span></td></tr></table></td></tr>
        <tr><td>文库分类：文库</td><td>小说作者：作者</td><td>文章状态：已完结</td><td>最后更新：2026-10-09</td><td>全文长度：12345字</td></tr></table>
        <table><tr><td><img src=/cover.jpg></td><td><span><b>作品Tags：冒险 幻想</b></span><br>
        <span><b>作品热度：B级</b></span><br><span>最近章节：</span><br><span>最新章节</span><br>
        <span>内容简介：</span><br><span>简介正文</span></td></tr></table>
        <div><div><span><fieldset><legend>阅读</legend><div><a href='$catalog'>小说目录</a></div></fieldset></span></div></div>
        </div></div></body></html>"""
}
