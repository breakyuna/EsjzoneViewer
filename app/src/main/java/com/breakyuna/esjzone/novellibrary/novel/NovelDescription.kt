package com.breakyuna.esjzone.novellibrary.novel

import androidx.compose.runtime.Immutable
import com.breakyuna.esjzone.novellibrary.component.Component
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.novellibrary.component.analyseParagraph
import com.breakyuna.esjzone.novellibrary.component.extractParagraphElements
import org.jsoup.nodes.Element
import java.io.Serializable

@Immutable
data class NovelDescription(
    val components: List<Component>,
    // One format per component; mixed text/images share alignment, with one final margin.
    val paragraphFormats: List<DescriptionParagraphFormat> = emptyList()
) : Serializable

@Immutable
data class DescriptionParagraphFormat(
    val alignment: String? = null,
    val spaceAfter: Boolean = false
) : Serializable

private val DESCRIPTION_HEADING_REGEX =
    "^(?:小说|小說)(?:web版)?(?:简介|簡介)[:：]\\s*".toRegex()

fun analyseDescription(element: Element): NovelDescription {
    val components = mutableListOf<Component>()
    val formats = mutableListOf<DescriptionParagraphFormat>()
    for (paragraph in extractParagraphElements(element)) {
        val parsed = analyseParagraph(paragraph)
        val alignment = (sequenceOf(paragraph) + paragraph.parents().asSequence())
            .mapNotNull { node ->
                node.attr("style").split(';').lastOrNull {
                    it.substringBefore(':').trim().equals("text-align", ignoreCase = true)
                }?.substringAfter(':')?.trim()?.lowercase()
            }.firstOrNull()
        components.addAll(parsed)
        parsed.indices.forEach { index ->
            formats.add(DescriptionParagraphFormat(alignment, paragraph.normalName() == "p" && index == parsed.lastIndex))
        }
    }
    return NovelDescription(components, formats)
}

/** Returns a compact plain-text preview suitable for a novel list card. */
fun NovelDescription.preview(maxLength: Int = 120): String {
    if (maxLength <= 0) return ""

    val plainText = buildString {
        components
            .filterIsInstance<TextComponent>()
            .forEach { component ->
                append(component.toPlainText())
                append(' ')
            }
    }
        .replace(Regex("\\s+"), " ")
        .trim()
        .replaceFirst(DESCRIPTION_HEADING_REGEX, "")
        .trim()

    if (plainText.length <= maxLength) return plainText
    return plainText.take(maxLength - 1).trimEnd() + "…"
}

private fun TextComponent.toPlainText(): String {
    return buildString {
        append(text)
        getExtras().forEach { append(it.toPlainText()) }
    }
}
