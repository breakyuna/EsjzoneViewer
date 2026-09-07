package com.breakyuna.esjzone.network

import org.jsoup.nodes.Element

/**
 * Small boundary around CSS selection so parser callers do not own the
 * concrete selector engine. The current implementation intentionally stays
 * Jsoup-backed; another selector implementation can satisfy this contract
 * without changing parser-facing field extraction.
 */
interface HtmlSelector {

    fun select(root: Element, selector: String): List<Element>

    fun first(root: Element, selector: String): Element? =
        select(root, selector).firstOrNull()
}

object JsoupHtmlSelector : HtmlSelector {

    override fun select(root: Element, selector: String): List<Element> =
        root.select(selector)
}
