/*
 * Adapted from LightNovelReader, Apache-2.0.
 * Copyright (C) 2024 by NightFish <hk198580666@outlook.com>
 * Copyright (C) 2024 by yukonisen <yukonisen@curiousers.org>
 * Source: Wenku8WebsiteDataSource.kt and Wenku8Api.kt, commit
 * 8681711f020af991fe37ca89983cc4531fe94c53 (see doc/WENKU8_THIRD_PARTY_SOURCES.md).
 * Changes: existing models/Jsoup, validated URLs, no embedded accounts,
 * no upstream request/Builder/cache framework, preserve original titles.
 * Current-site DOM remains unverified; these are reference-source candidates.
 */
package com.breakyuna.esjzone.network.wenku8

import com.breakyuna.esjzone.novellibrary.component.ChapterItem
import com.breakyuna.esjzone.novellibrary.component.ChapterListItem
import com.breakyuna.esjzone.novellibrary.component.Item
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovelImpl
import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel
import com.breakyuna.esjzone.novellibrary.novel.NovelChapterList
import com.breakyuna.esjzone.novellibrary.novel.analyseDescription
import com.breakyuna.esjzone.network.external.CloudflareChallenge
import java.io.IOException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class Wenku8ParseException : IOException("Wenku8 page structure could not be recognized")
class Wenku8RestrictedException : IOException("Wenku8 content is restricted by the site")
class Wenku8SearchRateLimitException : IOException("Wenku8 search interval limit")

data class Wenku8SearchResult(
    val novels: List<CoveredNovelImpl>,
    val page: Int,
    val totalPages: Int
)

/** Candidate rules from the fixed upstream SHA; never infer empty results from missing DOM. */
object Wenku8Parsers {
    private const val INFO = "//*[@id='content']/div[1]/table[1]/tbody"
    private const val EXTRA = "//*[@id='content']/div[1]/table[2]/tbody/tr"

    private fun document(html: String, url: String): Document {
        if (CloudflareChallenge.hasChallengeDocumentMarkers(html)) throw Wenku8ParseException()
        return Jsoup.parse(html, url).also {
            if (it.text().contains("因版权问题")) throw Wenku8RestrictedException()
        }
    }

    fun detail(html: String, url: String, catalog: NovelChapterList): DetailedNovel {
        if (Wenku8Urls.detailIdentity(url) == null) throw Wenku8ParseException()
        val doc = document(html, url)
        fun field(path: String): Element = doc.selectXpath(path).firstOrNull() ?: throw Wenku8ParseException()
        fun text(path: String): String = field(path).text().trim()
        fun value(column: Int, label: String): String = text("$INFO/tr[2]/td[$column]").removePrefix(label).trim()
        val name = text("$INFO/tr[1]/td/table/tbody/tr/td[1]/span/b").takeIf(String::isNotBlank)
            ?: throw Wenku8ParseException()
        val cover = image(field("$EXTRA/td[1]/img").attr("src"), url)
        val tags = text("$EXTRA/td[2]/span[1]/b").removePrefix("作品Tags：")
            .split(Regex("\\s+")).filter(String::isNotBlank)
        return DetailedNovel(
            name = name, url = url, coverUrl = cover, views = 0, likes = 0,
            words = value(5, "全文长度：").removeSuffix("字").toIntOrNull() ?: throw Wenku8ParseException(),
            type = listOf(value(1, "文库分类："), value(3, "小说状态：")).joinToString(" · "),
            author = value(2, "小说作者："),
            forumUrl = "", tags = tags, isAdult = tags.any { it.equals("R18", true) }, isFavorite = false,
            description = analyseDescription(field("$EXTRA/td[2]/span[6]")),
            chapterList = catalog, sourceUrl = url, updatedAt = value(4, "最后更新：")
        )
    }

    fun catalog(html: String, url: String): NovelChapterList {
        val identity = Wenku8Urls.catalogIdentity(url) ?: throw Wenku8ParseException()
        val doc = document(html, url)
        val items = mutableListOf<Item>()
        var title: String? = null
        var chapters = mutableListOf<Chapter>()
        fun flush() {
            if (chapters.isNotEmpty()) {
                val group = title
                if (group == null) items.addAll(chapters.map(::ChapterItem))
                else items.add(ChapterListItem(TextComponent(group), chapters.toList()))
            }
            chapters = mutableListOf()
        }
        doc.selectXpath("/html/body/table/tbody/tr").forEach { row ->
            val first = row.selectFirst("td")
            if (first?.attr("class") == "vcss") {
                flush()
                title = first.text().trim().takeIf(String::isNotBlank) ?: throw Wenku8ParseException()
            } else {
                row.select("td > a").forEach { link ->
                    val resolved = resolve(link.attr("href"), url)
                    if (Wenku8Urls.chapterIdentity(resolved) != identity) throw Wenku8ParseException()
                    val name = link.text().trim().takeIf(String::isNotBlank) ?: throw Wenku8ParseException()
                    chapters.add(Chapter(name, resolved, false))
                }
            }
        }
        flush()
        return NovelChapterList(items).takeIf { it.orderedChapters.isNotEmpty() } ?: throw Wenku8ParseException()
    }

    fun search(html: String, url: String, page: Int): Wenku8SearchResult {
        val doc = document(html, url)
        if (doc.text().contains("两次搜索的间隔时间不得少于 5 秒")) throw Wenku8SearchRateLimitException()
        val menu = doc.selectXpath("//*[@id='content']/div[1]/div[4]/div/span[1]/fieldset/div/a").firstOrNull()
        if (menu != null && menu.text().contains("小说目录")) {
            val identity = Wenku8Urls.catalogIdentity(resolve(menu.attr("href"), url)) ?: throw Wenku8ParseException()
            val detailUrl = Wenku8Urls.detail(requireNotNull(Wenku8Urls.bookId(identity)))
            val book = detail(html, detailUrl, NovelChapterList(emptyList()))
            return Wenku8SearchResult(listOf(CoveredNovelImpl(name = book.name, url = detailUrl,
                author = book.author, coverUrl = book.coverUrl, isAdult = book.isAdult)), 1, 1)
        }
        val pagination = doc.selectFirst("#pagelink em")?.text()?.split('/') ?: throw Wenku8ParseException()
        if (pagination.getOrNull(0)?.trim()?.toIntOrNull() != page) throw Wenku8ParseException()
        val total = pagination.getOrNull(1)?.trim()?.toIntOrNull()?.takeIf { it >= page }
            ?: throw Wenku8ParseException()
        val cards = doc.selectXpath("//*[@id='content']/table/tbody/tr/td/div")
        val books = cards.map { card ->
            val link = card.selectFirst("div > div:nth-child(1) > a") ?: throw Wenku8ParseException()
            val bookUrl = resolve(link.attr("href"), url)
            if (Wenku8Urls.detailIdentity(bookUrl) == null) throw Wenku8ParseException()
            val name = link.attr("title").trim().takeIf(String::isNotBlank) ?: throw Wenku8ParseException()
            val author = card.selectFirst("div > div:nth-child(2) > p:nth-child(2)")
                ?.text()?.substringBefore('/')?.substringAfter(':')?.trim()
            val tags = card.selectFirst("div > div:nth-child(2) > p:nth-child(4) > span")?.text().orEmpty()
                .split(Regex("\\s+")).filter(String::isNotBlank)
            CoveredNovelImpl(name = name, url = bookUrl, isAdult = tags.any { it.equals("R18", true) },
                coverUrl = image(link.selectFirst("img")?.attr("src").orEmpty(), url), author = author)
        }
        // No verified zero-result marker is available; missing cards remain a parse error.
        if (books.isEmpty()) throw Wenku8ParseException()
        return Wenku8SearchResult(books, page, total)
    }

    private fun resolve(raw: String, base: String): String =
        base.toHttpUrlOrNull()?.resolve(raw)?.toString().orEmpty()

    private fun image(raw: String, base: String): String = resolve(raw, base).takeIf {
        val url = it.toHttpUrlOrNull()
        url != null && url.isHttps && url.host == "www.wenku8.net" && url.port == 443 &&
            url.username.isEmpty() && url.password.isEmpty()
    }.orEmpty()
}
