package com.breakyuna.esjzone.data.reader

import androidx.compose.ui.graphics.Color
import com.breakyuna.esjzone.data.repository.toReaderBlocks
import com.breakyuna.esjzone.domain.reader.*
import com.breakyuna.esjzone.novellibrary.component.*
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import com.google.gson.Gson
import org.jsoup.Jsoup
import java.io.Serializable
import java.security.MessageDigest

/** Explicit wire types; no Compose objects or polymorphic Gson deserialization. */
data class StoredTextStyle(val type: String, val values: List<Int> = emptyList()) : Serializable
data class StoredText(val text: String, val styles: List<StoredTextStyle>, val reading: String? = null) : Serializable
data class StoredBlock(val type: String, val parts: List<StoredText> = emptyList(), val image: String? = null) : Serializable

data class ChapterBody(
    val schemaVersion: Int,
    val parserVersion: Int,
    val blocks: List<StoredBlock>,
    val fingerprint: String,
    val textLength: Int,
    // Not part of the content fingerprint: changing presentation must not invalidate anchors.
    val formatting: ReaderChapterFormatting? = null,
    val legacyPositions: ReaderLegacyPositions? = null
) : Serializable {
    fun readerBlocks(imageUrl: (String) -> String = { it }): List<ReaderBlock> = blocks.map { block ->
        when (block.type) {
            "paragraph" -> ReaderBlock.Paragraph(block.parts.map { it.toReaderText() })
            "text" -> block.parts.single().toReaderText()
            "image" -> ReaderBlock.Image(imageUrl(requireNotNull(block.image)))
            "break" -> ReaderBlock.LineBreak
            else -> error("Unsupported chapter block")
        }
    }

    /** Compatibility for non-renderer consumers; never passes through HTML. */
    fun components(imageUrl: (String) -> String = { it }): List<Component> = blocks.map { block ->
        when (block.type) {
            "paragraph", "text" -> block.parts.map { it.toComponent() }.let { parts ->
                (parts.firstOrNull() ?: TextComponent("")).apply { parts.drop(1).forEach(::append) }
            }
            "image" -> ImageComponent(imageUrl(requireNotNull(block.image)))
            "break" -> NewLineComponent()
            else -> error("Unsupported chapter block")
        }
    }

    fun validate(): ChapterBody = apply {
        require(schemaVersion == 2 && parserVersion > 0 && blocks.isNotEmpty()) { "Unsupported chapter format" }
        readerBlocks() // Validate all type/style discriminators before publication.
        require(fingerprint == digest(blocks) && textLength == length(blocks)) { "Invalid chapter content" }
    }

    internal fun recoverLeadingBreakText(html: String?, baseUrl: String): ChapterBody {
        if (parserVersion >= CURRENT_PARSER_VERSION || html.isNullOrBlank() ||
            !html.contains("<br", ignoreCase = true) || blocks.none { it.type == "break" }) return this

        return runCatching {
            val parsed = analyseChapterContent(Jsoup.parseBodyFragment(html, baseUrl).body())
            val parsedBlocks = parsed.blocks.map(::storeBlock)
            if (blocks.size != parsedBlocks.size) return this

            // Saved HTML may have been pretty-printed. Preserve the original
            // blocks and only put back text that was dropped at a leading break.
            val repaired = blocks.zip(parsedBlocks).map { (stored, candidate) ->
                when {
                    stored == candidate -> stored
                    stored.type == "break" && candidate.type == "paragraph" &&
                        candidate.parts.firstOrNull()?.text == "\n" &&
                        candidate.parts.any { it.text.isNotBlank() } -> candidate
                    stored.type == "paragraph" && candidate.type == "paragraph" &&
                        stored.textWithoutWhitespace() == candidate.textWithoutWhitespace() -> stored
                    else -> return this
                }
            }
            if (repaired == blocks) this else withRebuiltBlocks(repaired, formatting ?: parsed.formatting)
        }.getOrDefault(this)
    }

    /**
     * Treats saved chapter HTML as the source of truth when the parser version
     * advances. Upgrades blocks and formatting
     * cleanly without requiring one-off migration code for every HTML quirk.
     */
    internal fun rebuildFromHtmlIfOutdated(html: String?, baseUrl: String): ChapterBody {
        if (parserVersion >= CURRENT_PARSER_VERSION || html.isNullOrBlank()) return this

        return runCatching {
            val document = Jsoup.parseBodyFragment(html, baseUrl)
            if (document.selectFirst("#oops input#pw, input#pw[name=pw]") != null) return this
            if (com.breakyuna.esjzone.network.external.CloudflareChallenge.hasChallengeDocumentMarkers(html)) return this

            val parsed = analyseChapterContent(document.body())
            val parsedBlocks = parsed.blocks.map(::storeBlock)
            if (parsedBlocks.isEmpty()) return this
            if (!isCompatibleRebuild(blocks, parsedBlocks)) return this

            withRebuiltBlocks(parsedBlocks, parsed.formatting)
        }.getOrDefault(this)
    }

    private fun withRebuiltBlocks(rebuilt: List<StoredBlock>, formatting: ReaderChapterFormatting): ChapterBody {
        val upgraded = copy(parserVersion = CURRENT_PARSER_VERSION, formatting = formatting)
        if (rebuilt == blocks) return upgraded
        val newFingerprint = digest(rebuilt)
        return upgraded.copy(
            blocks = rebuilt,
            fingerprint = newFingerprint,
            textLength = length(rebuilt),
            legacyPositions = if (newFingerprint == fingerprint) legacyPositions else legacyPositionsFor(rebuilt)
        )
    }

    internal fun inheritLegacyPositions(previous: ChapterBody?): ChapterBody {
        if (previous == null || legacyPositions != null) return this
        val positions = when {
            fingerprint == previous.fingerprint -> previous.legacyPositions
            previous.parserVersion < parserVersion -> previous.legacyPositionsFor(blocks)
            previous.legacyPositions != null &&
                blocks.joinToString("") { it.textWithoutWhitespace() } ==
                previous.blocks.joinToString("") { it.textWithoutWhitespace() } -> previous.legacyPositions
            else -> null
        }
        return if (positions == null) this else copy(legacyPositions = positions)
    }

    private fun legacyPositionsFor(rebuilt: List<StoredBlock>): ReaderLegacyPositions? {
        val normalized = rebuilt.joinToString("") { it.textWithoutWhitespace() }
        val oldBlocks = readerBlocks()
        val oldText = oldBlocks.map { it.sourceText() }
        val pieces = oldText.map { it.filterNot(Char::isWhitespace) }

        // A block is mapped only when the surrounding old blocks constrain it to one location.
        val latestStarts = IntArray(pieces.size)
        var end = normalized.length
        for (index in pieces.indices.reversed()) {
            val piece = pieces[index]
            if (piece.isEmpty()) continue
            end = normalized.lastIndexOf(piece, end - piece.length)
            if (end < 0) return null
            latestStarts[index] = end
        }

        var next = 0
        val positions = buildMap {
            for ((index, piece) in pieces.withIndex()) {
                if (piece.isEmpty()) continue
                val start = normalized.indexOf(piece, next)
                if (start < 0) return null
                next = start + piece.length
                if (start != latestStarts[index]) continue
                val raw = oldText[index]
                put(index, ReaderLegacyTextBlock(
                    start, raw.length, raw.indices.filter { raw[it].isWhitespace() },
                    ReaderUnderlines.sourceSignature(oldBlocks[index])
                ))
            }
        }
        return positions.takeIf { it.isNotEmpty() }?.let { ReaderLegacyPositions(fingerprint, it) }
    }

    companion object {
        internal const val CURRENT_PARSER_VERSION = 3
        private val gson = Gson()
        fun from(components: List<Component>): ChapterBody = fromBlocks(components.flatMap(Component::toReaderBlocks))
        fun fromBlocks(blocks: List<ReaderBlock>): ChapterBody {
            val stored = blocks.map(::storeBlock)
            return ChapterBody(2, CURRENT_PARSER_VERSION, stored, digest(stored), length(stored))
        }
        private fun storeBlock(block: ReaderBlock): StoredBlock = when (block) {
            is ReaderBlock.Paragraph -> StoredBlock("paragraph", block.parts.map(::storeText))
            is ReaderBlock.Text -> StoredBlock("text", listOf(storeText(block)))
            is ReaderBlock.Image -> StoredBlock("image", image = block.url)
            ReaderBlock.LineBreak -> StoredBlock("break")
        }
        private fun StoredBlock.textWithoutWhitespace() =
            parts.joinToString("") { part -> part.text.filterNot(Char::isWhitespace) }
        private fun isCompatibleRebuild(existing: List<StoredBlock>, candidate: List<StoredBlock>): Boolean {
            val oldText = existing.joinToString("") { it.textWithoutWhitespace() }
            val newText = candidate.joinToString("") { it.textWithoutWhitespace() }
            val oldImages = existing.mapNotNull { it.image?.takeIf(String::isNotBlank) }
            val newImages = candidate.mapNotNull { it.image?.takeIf(String::isNotBlank) }
            if (oldText == newText && oldImages == newImages) return true
            if (oldImages.any { it !in newImages }) return false
            if (oldText.isEmpty()) return true
            // Every surviving text block from the old snapshot must appear in order in the rebuilt text.
            var cursor = 0
            for (block in existing) {
                val piece = block.textWithoutWhitespace()
                if (piece.isEmpty()) continue
                val found = newText.indexOf(piece, startIndex = cursor)
                if (found < 0) return false
                cursor = found + piece.length
            }
            return true
        }
        private fun length(blocks: List<StoredBlock>) = blocks.sumOf { b -> b.parts.sumOf { it.text.length } }
        private fun digest(blocks: List<StoredBlock>): String = MessageDigest.getInstance("SHA-256")
            .digest(gson.toJson(blocks.map { block -> block.copy(parts = block.parts.map { part ->
                part.copy(styles = part.styles.sortedWith(compareBy<StoredTextStyle> { it.type }.thenBy { it.values.joinToString(",") }))
            }) }).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        private fun storeText(text: ReaderBlock.Text) = StoredText(text.value, text.styles.map { style ->
            when (style) {
                ReaderTextStyle.Bold -> StoredTextStyle("bold")
                ReaderTextStyle.Italic -> StoredTextStyle("italic")
                ReaderTextStyle.Underline -> StoredTextStyle("underline")
                ReaderTextStyle.StrikeThrough -> StoredTextStyle("strike")
                is ReaderTextStyle.FontSizePx -> StoredTextStyle("size", listOf(style.value))
                is ReaderTextStyle.ForegroundColor -> StoredTextStyle("foreground", listOf(style.red, style.green, style.blue))
                is ReaderTextStyle.BackgroundColor -> StoredTextStyle("background", listOf(style.red, style.green, style.blue))
            }
        }, text.ruby?.reading)
    }
}

fun DetailedChapter.withStructuredBody(): DetailedChapter {
    val current = body ?: ChapterBody.from(content)
    val ready = current.withFormatting(contentHtml, sourceUrl)
    return if (ready === body) this else copy(body = ready)
}

private fun StoredText.toReaderText() = ReaderBlock.Text(text, styles.map { it.readerStyle() }.toSet(),
    reading?.let { ReaderRuby(text, it) })

private fun StoredTextStyle.readerStyle(): ReaderTextStyle = when (type) {
    "bold" -> ReaderTextStyle.Bold
    "italic" -> ReaderTextStyle.Italic
    "underline" -> ReaderTextStyle.Underline
    "strike" -> ReaderTextStyle.StrikeThrough
    "size" -> ReaderTextStyle.FontSizePx(values.single())
    "foreground", "background" -> {
        require(values.size == 3 && values.all { it in 0..255 })
        if (type == "foreground") ReaderTextStyle.ForegroundColor(values[0], values[1], values[2])
        else ReaderTextStyle.BackgroundColor(values[0], values[1], values[2])
    }
    else -> error("Unsupported chapter style")
}

private fun StoredText.toComponent(): TextComponent = TextComponent(text).apply {
    styles.forEach { stored ->
        style(when (val value = stored.readerStyle()) {
            ReaderTextStyle.Bold -> BoldTextStyle
            ReaderTextStyle.Italic -> ItalicTextStyle
            ReaderTextStyle.Underline -> UnderlineTextStyle
            ReaderTextStyle.StrikeThrough -> LineThroughTextStyle
            is ReaderTextStyle.FontSizePx -> FontSizeTextStyle(value.value)
            is ReaderTextStyle.ForegroundColor -> ColorTextStyle(Color(value.red, value.green, value.blue))
            is ReaderTextStyle.BackgroundColor -> BackgroundColorTextStyle(Color(value.red, value.green, value.blue))
        })
    }
    reading?.let { style(FuriganaTextStyle(TextComponent(it))) }
}
