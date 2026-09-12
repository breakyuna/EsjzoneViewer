package com.breakyuna.esjzone.network.features

/**
 * Semantic CSS boundaries shared by home, list and search card parsing.
 * Keeping these constants in a dedicated file makes the selector contract
 * explicit and allows the parser to remain Jsoup-only.
 */
internal object NovelCardSelectors {
    const val TITLE_LINK = "h5.card-title a, h5 a[href*='/detail/'], a.card-title[href*='/detail/']"
    const val LATEST_LINK = ".card-ep a"
    const val LATEST_EPISODE = ".card-ep"
    const val AUTHOR_LINK = ".card-author a"
    const val STATS = ".card-other"
    const val R18_BADGE = ".product-badge.top"
}
