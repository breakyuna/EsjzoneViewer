package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovelImpl
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.EsjzoneXPaths
import us.codecraft.xsoup.XPathEvaluator
import org.jsoup.nodes.Element

internal fun parseCardCount(raw: String?): Int =
    raw?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: 0

/** Shared card field extraction; callers supply R18 because home sections encode it by zone. */
internal fun parseNovelCard(
    card: Element,
    name: String,
    url: String,
    views: String?,
    likes: String?,
    r18: Boolean
): CoveredNovel {
    return CoveredNovelImpl(
        EsjzoneUrls.coverUrlFromNovelCard(card),
        name.trim(), url.trim(), parseCardCount(views), parseCardCount(likes),
        r18
    )
}

internal enum class NovelCardLayout { HOME, LIST }

private data class CardFields(
    val title: XPathEvaluator,
    val url: XPathEvaluator,
    val views: XPathEvaluator,
    val likes: XPathEvaluator
)

private val homeCardFields = CardFields(
    EsjzoneXPaths.Home.Novel.Name,
    EsjzoneXPaths.Home.Novel.Url,
    EsjzoneXPaths.Home.Novel.Views,
    EsjzoneXPaths.Home.Novel.Likes
)
private val listCardFields = CardFields(
    EsjzoneXPaths.Tags.Novel.Name,
    EsjzoneXPaths.Tags.Novel.Url,
    EsjzoneXPaths.Tags.Novel.Views,
    EsjzoneXPaths.Tags.Novel.Likes
)

/** Keep verified template differences here instead of duplicating every page parser. */
internal fun parseNovelCard(card: Element, r18: Boolean, layout: NovelCardLayout): CoveredNovel {
    val fields = if (layout == NovelCardLayout.HOME) homeCardFields else listCardFields
    return parseNovelCard(
        card,
        fields.title.evaluate(card).get().orEmpty(),
        fields.url.evaluate(card).get().orEmpty(),
        fields.views.evaluate(card).get(),
        fields.likes.evaluate(card).get(),
        r18
    )
}
