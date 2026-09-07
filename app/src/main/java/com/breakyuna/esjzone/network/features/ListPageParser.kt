package com.breakyuna.esjzone.network.features

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

internal val pagesRegex = "total: ([0-9]+)".toRegex()

/** CSS-only boundaries shared by the list, search and paged list requesters. */
internal fun selectNovelCards(document: Document): List<Element> =
    document.select(".card.mb-30").filter { card ->
        card.select("h5.card-title a[href], h5 a[href*='/detail/']").isNotEmpty()
    }

/** Reads the server-rendered pager count without relying on script position. */
internal fun pageCount(document: Document): Int =
    document.select("script")
        .asSequence()
        .map { script -> script.data().ifBlank { script.html() } }
        .mapNotNull { script ->
            pagesRegex.find(script)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
        .firstOrNull()
        ?: 1
