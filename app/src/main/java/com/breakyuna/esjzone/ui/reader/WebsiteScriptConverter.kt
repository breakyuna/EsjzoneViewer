package com.breakyuna.esjzone.ui.reader

/** No reader offsets/cache are created for untrusted web frames. Budget resets per page. */
internal class WebsiteScriptConverter(
    private val transform: (String, ReaderScript) -> String = ReaderScriptConverter::convertUncached
) {
    private var remainingChars = MAX_PAGE_CHARS
    private var remainingCalls = MAX_PAGE_CALLS
    private var closed = false

    @Synchronized
    fun convert(text: String, mode: ReaderScript): String {
        if (closed || mode == ReaderScript.ORIGINAL || text.isBlank() ||
            text.length > MAX_TEXT_LENGTH || remainingCalls <= 0 || text.length > remainingChars) {
            return text
        }
        remainingCalls--
        remainingChars -= text.length
        return transform(text, mode)
    }

    @Synchronized
    fun convertBatch(texts: List<String>, mode: ReaderScript): List<String> {
        if (closed || mode == ReaderScript.ORIGINAL || texts.isEmpty() ||
            texts.size > MAX_BATCH_ITEMS || remainingCalls <= 0) {
            return texts
        }
        var totalChars = 0
        var convertibleChars = 0
        for (item in texts) {
            if (item.length > MAX_BATCH_CHARS - totalChars) return texts
            totalChars += item.length
            if (item.isNotBlank() && item.length <= MAX_TEXT_LENGTH) {
                convertibleChars += item.length
            }
        }
        if (convertibleChars == 0 || convertibleChars > remainingChars) return texts
        remainingCalls--
        remainingChars -= convertibleChars
        return texts.map { item ->
            if (item.isBlank() || item.length > MAX_TEXT_LENGTH) item else transform(item, mode)
        }
    }

    @Synchronized
    fun resetPage() {
        if (closed) return
        remainingCalls = MAX_PAGE_CALLS
        remainingChars = MAX_PAGE_CHARS
    }

    @Synchronized
    fun close() { closed = true }

    companion object {
        internal const val MAX_TEXT_LENGTH = 32_768
        internal const val MAX_BATCH_ITEMS = 256
        internal const val MAX_BATCH_CHARS = 256 * 1024
        internal const val MAX_PAGE_CALLS = 4_096
        internal const val MAX_PAGE_CHARS = 8 * 1024 * 1024
    }
}
