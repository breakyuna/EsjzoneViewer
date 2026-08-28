package com.breakyuna.esjzone.novellibrary.novel

import com.breakyuna.esjzone.novellibrary.component.Component
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.novellibrary.component.analyseComponents
import org.jsoup.nodes.Element
import java.io.Serializable

data class NovelDescription(
    val components: List<Component>
) : Serializable

private val DESCRIPTION_HEADING_REGEX =
    "^(?:小说|小說)(?:web版)?(?:简介|簡介)[:：]\\s*".toRegex()

fun analyseDescription(element: Element): NovelDescription {
    return NovelDescription(
        analyseComponents(element)
    )
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
