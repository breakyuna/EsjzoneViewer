package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.EsjzoneXPaths
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovelImpl
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node

private val cardNumberRegex = Regex("(?<![0-9])[0-9][0-9,\\u00a0 ]*(?![0-9])")

/**
 * Parses a number displayed by a card statistic while preserving the old
 * non-null API used by views and likes. Missing or malformed values become
 * zero for those legacy fields.
 */
internal fun parseCardCount(raw: String?): Int =
    parseOptionalCardCount(raw) ?: 0

internal fun parseOptionalCardCount(raw: String?): Int? {
    val match = cardNumberRegex.find(raw.orEmpty()) ?: return null
    return match.value
        .filter(Char::isDigit)
        .toIntOrNull()
}

/**
 * Parses the common fields supplied by a caller that already extracted the
 * card's title, URL and primary statistics. Keeping this overload avoids
 * breaking callers and parser tests that construct a small synthetic card.
 */
internal fun parseNovelCard(
    card: Element,
    name: String,
    url: String,
    views: String?,
    likes: String?,
    r18: Boolean,
    latestTitle: String? = null,
    latestUrl: String? = null,
    author: String? = null,
    authorUrl: String? = null,
    words: Int? = null,
    articleCount: Int? = null,
    discussionCount: Int? = null
): CoveredNovel {
    return CoveredNovelImpl(
        coverUrl = EsjzoneUrls.coverUrlFromNovelCard(card),
        name = name.trim(),
        url = url.trim(),
        views = parseCardCount(views),
        likes = parseCardCount(likes),
        isAdult = r18 || card.select(EsjzoneXPaths.NovelCard.R18BadgeSelector).isNotEmpty(),
        latestTitle = latestTitle.cleanCardText(),
        latestUrl = latestUrl.cleanCardUrl(),
        author = author.cleanCardText(),
        authorUrl = authorUrl.cleanCardUrl(),
        words = words,
        articleCount = articleCount,
        discussionCount = discussionCount
    )
}

/** Home and list/search cards have different fields but share one parser. */
internal enum class NovelCardLayout { HOME, LIST }

/**
 * Parses a server-rendered ESJ card by semantic classes and icon names.
 *
 * HOME cards expose the latest chapter and primary view/favorite counts.
 * LIST cards (including tag search and [Novels.kt]) additionally expose the
 * author and the file/feather/message statistics. No detail-page request is
 * made here, and absent fields remain null so the UI can hide them.
 */
internal fun parseNovelCard(
    card: Element,
    r18: Boolean,
    layout: NovelCardLayout
): CoveredNovel {
    val titleLink = card.selectFirst(EsjzoneXPaths.NovelCard.TitleLinkSelector)
    val imageLink = card.selectFirst(".card-img-tiles[href], a[href*='/detail/']")
    // A list card can split its statistics across several `.card-other`
    // blocks (commonly words, views/favorites, then articles/discussions).
    // Keep every block so semantic icon lookup cannot stop at the first one.
    val stats = card.select(EsjzoneXPaths.NovelCard.StatsSelector)
    val latestLink = card.selectFirst(EsjzoneXPaths.NovelCard.LatestLinkSelector)
    val authorLink = card.selectFirst(EsjzoneXPaths.NovelCard.AuthorLinkSelector)

    val views = statCount(stats, StatKind.VIEWS)
    val likes = statCount(stats, StatKind.LIKES)
    val isList = layout == NovelCardLayout.LIST

    return parseNovelCard(
        card = card,
        name = titleLink?.text().orEmpty().ifBlank { card.attr("title") },
        url = firstNonBlank(
            titleLink?.attr("href"),
            imageLink?.attr("href")
        ),
        views = views?.toString(),
        likes = likes?.toString(),
        r18 = r18,
        latestTitle = latestLink?.text(),
        latestUrl = latestLink?.attr("href"),
        author = authorLink?.text().takeIf { isList },
        authorUrl = authorLink?.attr("href").takeIf { isList },
        words = statCount(stats, StatKind.WORDS).takeIf { isList },
        articleCount = statCount(stats, StatKind.ARTICLES).takeIf { isList },
        discussionCount = statCount(stats, StatKind.DISCUSSIONS).takeIf { isList }
    )
}

private enum class StatKind(
    val selectors: String
) {
    VIEWS(
        ".icon-eye, [class*='icon-eye'], [class*='feather-eye'], " +
            "[data-feather='eye'], [data-icon='eye']"
    ),
    LIKES(
        ".icon-heart, [class*='icon-heart'], [class*='feather-heart'], " +
            "[data-feather='heart'], [data-icon='heart']"
    ),
    WORDS(
        ".icon-file-text, [class*='icon-file-text'], [class*='feather-file-text'], " +
            "[data-feather='file-text'], [data-icon='file-text']"
    ),
    ARTICLES(
        ".icon-feather, [class*='icon-feather'], [class*='feather-feather'], " +
            "[data-feather='feather'], [data-icon='feather']"
    ),
    DISCUSSIONS(
        ".icon-message-square, [class*='icon-message-square'], [class*='feather-message-square'], " +
            "[data-feather='message-square'], [data-icon='message-square']"
    )
}

private val anyStatIconTokens = setOf(
    "eye",
    "heart",
    "file-text",
    "feather",
    "message-square"
)

/** Finds the number next to a semantic icon without relying on div position. */
private fun statCount(stats: Iterable<Element>, kind: StatKind): Int? {
    for (statsBlock in stats) {
        val icon = statsBlock.selectFirst(kind.selectors) ?: continue

        var node: Element? = icon
        repeat(5) {
            val candidate = node ?: return@repeat
            if (candidate !== statsBlock) {
                parseOptionalCardCount(candidate.text())?.let { return it }
            } else {
                // Some templates put the icon and its value directly under
                // `.card-other` instead of wrapping each pair in a div.
                directSiblingCount(icon, statsBlock, kind)?.let { return it }
            }
            node = candidate.parent()
        }
    }
    return null
}

private fun directSiblingCount(
    icon: Element,
    statsBlock: Element,
    kind: StatKind
): Int? {
    fun scan(start: Node?, direction: (Node) -> Node?): Int? {
        var sibling = start
        repeat(3) {
            val candidate = sibling ?: return@repeat
            if (candidate is Element && candidate !== statsBlock && candidate.isStatIcon()) {
                return@repeat
            }
            parseOptionalCardCount(candidate.toString())?.let { return it }
            sibling = direction(candidate)
        }
        return null
    }

    return scan(icon.nextSibling()) { it.nextSibling() }
        ?: scan(icon.previousSibling()) { it.previousSibling() }
}

private fun Element.isStatIcon(): Boolean {
    val dataName = attr("data-feather").ifBlank { attr("data-icon") }
    if (dataName in anyStatIconTokens) return true
    return classNames().any { className ->
        anyStatIconTokens.any { token ->
            className.contains("icon-$token") || className.contains("feather-$token")
        }
    }
}

private fun firstNonBlank(vararg values: String?): String =
    values.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()

private fun String?.cleanCardText(): String? =
    this?.trim()?.takeIf { it.isNotBlank() }

private fun String?.cleanCardUrl(): String? =
    this?.trim()?.takeIf { it.isNotBlank() }
