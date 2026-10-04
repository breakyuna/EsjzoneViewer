package com.breakyuna.esjzone.data.reader

import androidx.compose.ui.graphics.Color
import com.breakyuna.esjzone.data.repository.toReaderBlocks
import com.breakyuna.esjzone.domain.reader.*
import com.breakyuna.esjzone.novellibrary.component.*
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import com.google.gson.Gson
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
    val textLength: Int
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

    companion object {
        private val gson = Gson()
        fun from(components: List<Component>): ChapterBody = fromBlocks(components.flatMap(Component::toReaderBlocks))
        fun fromBlocks(blocks: List<ReaderBlock>): ChapterBody {
            val stored = blocks.map { block ->
                when (block) {
                    is ReaderBlock.Paragraph -> StoredBlock("paragraph", block.parts.map(::storeText))
                    is ReaderBlock.Text -> StoredBlock("text", listOf(storeText(block)))
                    is ReaderBlock.Image -> StoredBlock("image", image = block.url)
                    ReaderBlock.LineBreak -> StoredBlock("break")
                }
            }
            return ChapterBody(2, 1, stored, digest(stored), length(stored))
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

fun DetailedChapter.withStructuredBody(): DetailedChapter = if (body != null) this else copy(body = ChapterBody.from(content))

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
