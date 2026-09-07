package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.HtmlSelector
import com.breakyuna.esjzone.network.JsoupHtmlSelector
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

private const val HOME_SECTION_ROOT =
    "html > body > div:nth-of-type(3) > section > div > div:nth-of-type(1)"

/**
 * The home page has five product sections with independent ordering and R18
 * semantics. Keep the section boundary explicit: a card in one section must
 * never be silently reclassified into another section.
 */
internal enum class HomeSection(
    val key: String,
    internal val markerSelector: String,
    internal val positionalFallback: String
) {
    TRANSLATED(
        key = "recent-translated",
        markerSelector = "[data-home-section='recent-translated'], [data-section='recent-translated'], #recent-translated, .recently-update-translated",
        positionalFallback = HOME_SECTION_ROOT + " > div:nth-of-type(3)"
    ),
    ORIGINAL(
        key = "recent-original",
        markerSelector = "[data-home-section='recent-original'], [data-section='recent-original'], #recent-original, .recently-update-original",
        positionalFallback = HOME_SECTION_ROOT + " > div:nth-of-type(5)"
    ),
    TRANSLATED_R18(
        key = "recent-translated-r18",
        markerSelector = "[data-home-section='recent-translated-r18'], [data-section='recent-translated-r18'], #recent-translated-r18, .recently-update-translated-r18",
        positionalFallback = HOME_SECTION_ROOT + " > div:nth-of-type(7)"
    ),
    ORIGINAL_R18(
        key = "recent-original-r18",
        markerSelector = "[data-home-section='recent-original-r18'], [data-section='recent-original-r18'], #recent-original-r18, .recently-update-original-r18",
        positionalFallback = HOME_SECTION_ROOT + " > div:nth-of-type(9)"
    ),
    RECOMMENDATION(
        key = "recommendation",
        markerSelector = "[data-home-section='recommendation'], [data-section='recommendation'], #recommendation, .recommendation",
        positionalFallback = HOME_SECTION_ROOT + " > div:nth-of-type(11)"
    );

}

private val homeSelector: HtmlSelector = JsoupHtmlSelector

internal fun selectHomeSectionCards(
    document: Document,
    section: HomeSection,
    selector: HtmlSelector = homeSelector
): List<Element> {
    val markedRoots = selector.select(document, section.markerSelector)
    val roots = if (markedRoots.isNotEmpty()) {
        markedRoots
    } else {
        selector.select(document, section.positionalFallback)
    }

    return roots
        .flatMap { root -> selector.select(root, ".card.mb-30, .card") }
        .distinct()
        .filter { card ->
            selector.select(card, "h5.card-title a[href], h5 a[href*='/detail/']").isNotEmpty()
        }
}
