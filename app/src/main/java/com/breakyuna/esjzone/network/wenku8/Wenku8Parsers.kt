/*
 * Adapted from LightNovelReader, Apache-2.0.
 * Copyright (C) 2024 by NightFish <hk198580666@outlook.com>
 * Copyright (C) 2024 by yukonisen <yukonisen@curiousers.org>
 * Source: Wenku8WebsiteDataSource.kt and Wenku8Api.kt, commit
 * 8681711f020af991fe37ca89983cc4531fe94c53 (see doc/WENKU8_THIRD_PARTY_SOURCES.md).
 * Changes: existing models/Jsoup, validated URLs, no embedded accounts,
 * no upstream request/Builder/cache framework, preserve original titles.
 * Detail DOM checked against the supplied MHT 2026-10-11; home/list checked 2026-10-09.
 * Catalog remains a reference-source candidate.
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

class Wenku8LoginRequiredException : IOException("Wenku8 sign-in is required")

class Wenku8ParseException : IOException("Wenku8 page structure could not be recognized")
class Wenku8RestrictedException : IOException("Wenku8 content is restricted by the site")
class Wenku8SearchRateLimitException : IOException("Wenku8 search interval limit")

data class Wenku8SearchResult(
    val novels: List<CoveredNovelImpl>,
    val page: Int,
    val totalPages: Int,
    val sections: List<Wenku8HomeSection> = emptyList()
)

data class Wenku8HomeSection(val title: String, val novels: List<CoveredNovelImpl>)

/** Site snapshots supplement the fixed upstream rules; missing DOM never means empty results. */
object Wenku8Parsers {
    private const val INFO = "//*[@id='content']/div[1]/table[1]/tbody"
    private const val EXTRA = "//*[@id='content']/div[1]/table[2]/tbody/tr"

    private fun document(html: String, url: String): Document {
        com.breakyuna.esjzone.util.AppLogger.trace("Wenku8Parsers") {
            "stage=parse-document, url=${com.breakyuna.esjzone.util.diagnosticUrl(url)}, ${com.breakyuna.esjzone.util.diagnosticHtml(html)}"
        }
        if (CloudflareChallenge.hasChallengeDocumentMarkers(html)) throw Wenku8ParseException()
        return Jsoup.parse(html, url).also {
            if (it.selectFirst("input[name=username]") != null && it.selectFirst("input[name=password]") != null) throw Wenku8LoginRequiredException()
            if (it.text().contains("因版权问题")) throw Wenku8RestrictedException()
        }
    }

    fun detail(html: String, url: String, catalog: NovelChapterList): DetailedNovel {
        if (Wenku8Urls.detailIdentity(url) == null) throw Wenku8ParseException()
        val doc = document(html, url)
        fun field(path: String): Element = doc.selectXpath(path).firstOrNull() ?: run {
            com.breakyuna.esjzone.util.AppLogger.trace("Wenku8Parsers") { "stage=missing-field, xpath=$path" }
            throw Wenku8ParseException()
        }
        fun text(path: String): String = field(path).text().trim()
        val metadata = doc.selectXpath("$INFO/tr[2]/td").map { it.text().trim() }
        fun value(label: String): String = metadata.firstOrNull { it.startsWith(label) }
            ?.removePrefix(label)?.trim()?.takeIf(String::isNotBlank) ?: throw Wenku8ParseException()
        val spans = field("$EXTRA/td[2]").children().filter { it.tagName() == "span" }
        fun span(label: String): Element = spans.firstOrNull { it.text().trim().startsWith(label) }
            ?: throw Wenku8ParseException()
        val name = text("$INFO/tr[1]/td/table/tbody/tr/td[1]/span/b").takeIf(String::isNotBlank)
            ?: throw Wenku8ParseException()
        val cover = image(field("$EXTRA/td[1]/img").attr("src"), url)
        val tags = span("作品Tags：").text().trim().removePrefix("作品Tags：")
            .split(Regex("\\s+")).filter(String::isNotBlank)
        val description = span("内容简介：").nextElementSiblings().firstOrNull { it.tagName() == "span" }
            ?: throw Wenku8ParseException()
        return DetailedNovel(
            name = name, url = url, coverUrl = cover, views = 0, likes = 0,
            words = value("全文长度：").removeSuffix("字").toIntOrNull() ?: throw Wenku8ParseException(),
            type = listOf(value("文库分类："), value("文章状态：")).joinToString(" · "),
            author = value("小说作者："),
            forumUrl = "", tags = tags, isAdult = tags.any { it.equals("R18", true) }, isFavorite = false,
            description = analyseDescription(description),
            chapterList = catalog, sourceUrl = url, updatedAt = value("最后更新：")
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
        val menu = doc.select("#content > div:first-child fieldset a[href]")
            .firstOrNull { it.text().trim() == "小说目录" }
        if (menu != null) {
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
            val name = link.attr("tiptitle").ifBlank { link.attr("title") }.trim().takeIf(String::isNotBlank) ?: throw Wenku8ParseException()
            val metadata = card.select("p").map { it.text() }
            val author = metadata.firstOrNull { it.startsWith("作者:") || it.startsWith("作者：") }
                ?.substringBefore('/')?.substringAfter(':')?.substringAfter('：')?.trim()
            val tags = card.select("p").firstOrNull { it.text().startsWith("Tags:") }?.selectFirst("span")?.text().orEmpty()
                .split(Regex("\\s+")).filter(String::isNotBlank)
            CoveredNovelImpl(name = name, url = bookUrl, isAdult = tags.any { it.equals("R18", true) },
                coverUrl = image(link.selectFirst("img")?.attr("src").orEmpty(), url), author = author)
        }
        // No verified zero-result marker is available; missing cards remain a parse error.
        if (books.isEmpty()) throw Wenku8ParseException()
        return Wenku8SearchResult(books, page, total)
    }

    /** Verified on the signed-in website, 2026-10-09. No detail-page fan-out. */
    fun home(html: String, url: String): Wenku8SearchResult {
        if (!wenku8PageAllowed(url, Wenku8PageKind.HOME)) throw Wenku8ParseException()
        val doc = document(html, url)
        val sections = doc.select("#centers > .block, #right > .block").mapNotNull { block ->
            val title = block.selectFirst(".blocktitle")?.ownText()?.trim().orEmpty()
                .ifBlank { block.selectFirst(".blocktitle .txt")?.text().orEmpty() }
                .substringBefore('(').trim()
            val books = block.select(".blockcontent a[href]").mapNotNull book@ { link ->
                val bookUrl = resolve(link.attr("href"), url)
                if (Wenku8Urls.detailIdentity(bookUrl) == null) return@book null
                val name = link.attr("tiptitle").ifBlank { link.text() }.trim()
                if (name.isBlank()) return@book null
                CoveredNovelImpl(name = name, url = bookUrl,
                    coverUrl = image(link.selectFirst("img")?.attr("src").orEmpty(), url))
            }.groupBy { it.url }.values.map { duplicates ->
                duplicates.firstOrNull { it.coverUrl.isNotBlank() } ?: duplicates.first()
            }
            if (title.isBlank() || books.isEmpty()) null else Wenku8HomeSection(title, books)
        }
        if (sections.isEmpty()) throw Wenku8ParseException()
        return Wenku8SearchResult(emptyList(), 1, 1, sections)
    }

    private fun resolve(raw: String, base: String): String =
        base.toHttpUrlOrNull()?.resolve(raw)?.toString().orEmpty()

    private fun image(raw: String, base: String): String {
        if (raw.isBlank()) return ""
        val parsed = resolve(raw, base).toHttpUrlOrNull() ?: return ""
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) return ""
        if (parsed.host == "img.wenku8.com" && parsed.port in setOf(80, 443)) {
            return parsed.newBuilder().scheme("https").port(443).build().toString()
        }
        return parsed.toString().takeIf {
            parsed.isHttps && parsed.host == "www.wenku8.net" && parsed.port == 443
        }.orEmpty()
    }
}
