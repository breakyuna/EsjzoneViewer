package com.breakyuna.esjzone.ui.reader

/** No reader offsets/cache are created for untrusted web frames. Budget lasts for this browser. */
internal class WebsiteScriptConverter(
    private val transform: (String, ReaderScript) -> String = ReaderScriptConverter::convertUncached
) {
    private var remaining = 4 * 1024 * 1024
    private var calls = 4096
    private var closed = false

    @Synchronized
    fun convert(text: String, mode: ReaderScript): String {
        if (closed || mode == ReaderScript.ORIGINAL || text.length > 32_768 ||
            calls <= 0 || text.length > remaining) return text
        calls--
        remaining -= text.length
        return transform(text, mode)
    }

    @Synchronized
    fun close() { closed = true }
}
