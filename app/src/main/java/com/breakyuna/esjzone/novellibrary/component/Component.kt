package com.breakyuna.esjzone.novellibrary.component

import com.breakyuna.esjzone.network.EsjzoneUrls
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.io.Serializable


private val BLOCK_TAG_NAMES = setOf(
    "p", "div", "section", "article", "blockquote", "main", "aside",
    "header", "footer", "h1", "h2", "h3", "h4", "h5", "h6",
    "ul", "ol", "li", "pre", "hr", "table", "thead", "tbody", "tfoot",
    "tr", "td", "th", "details", "figure", "figcaption", "dl", "dt", "dd",
    "center", "address", "fieldset", "legend"
)

private val IGNORED_TAG_NAMES = setOf("script", "style", "noscript", "template", "rp")

private fun Node.isBlockElement(): Boolean =
    this is Element && normalName() in BLOCK_TAG_NAMES

private fun Node.containsBlockElement(): Boolean = when {
    isBlockElement() -> true
    this is Element -> normalName() !in IGNORED_TAG_NAMES && childNodes().any { it.containsBlockElement() }
    else -> false
}

private fun Element.hasBlockDescendants(): Boolean =
    childNodes().any { it.containsBlockElement() }

private fun Element.isSignificantParagraph(): Boolean =
    text().isNotBlank() || selectFirst("img, br") != null

/** Synthetic paragraphs keep their source parent available to formatting consumers. */
internal fun extractParagraphElements(
    container: Element,
    onAnonymousParagraph: (Element, Element) -> Unit = { _, _ -> }
): List<Element> = buildList {
    fun collect(element: Element) {
        if (element.normalName() in IGNORED_TAG_NAMES) return
        if (!element.hasBlockDescendants()) {
            add(element)
            return
        }

        var currentAnon: Element? = null
        fun flushAnon() {
            val anon = currentAnon ?: return
            if (anon.isSignificantParagraph()) {
                add(anon)
            }
            currentAnon = null
        }

        for (child in element.childNodes()) {
            if (child is Element && child.normalName() in IGNORED_TAG_NAMES) continue
            if (child is Element && child.containsBlockElement()) {
                flushAnon()
                collect(child)
            } else {
                if (child is TextNode && child.text().isBlank()) {
                    currentAnon?.appendChild(child.clone())
                } else {
                    val anon = currentAnon ?: Element("p").apply {
                        setBaseUri(element.baseUri())
                    }.also {
                        currentAnon = it
                        onAnonymousParagraph(it, element)
                    }
                    anon.appendChild(child.clone())
                }
            }
        }
        flushAnon()
    }

    collect(container)
}


fun analyseComponents(element: Element): List<Component> {
    val components = mutableListOf<Component>()

    for (paragraph in extractParagraphElements(element)) {
        components.addAll(analyseParagraph(paragraph))
    }

    return components.toList()
}


internal fun analyseParagraph(paragraph: Element): List<Component> {
    if (paragraph.normalName() in IGNORED_TAG_NAMES) return emptyList()
    if (paragraph.hasBlockDescendants()) {
        return analyseComponents(paragraph)
    }

    if (paragraph.tagName() == "img") {
        val imageUrl = resolveImageUrl(paragraph)
        return if (imageUrl.isNotBlank()) listOf(ImageComponent(imageUrl)) else emptyList()
    }

    if (paragraph.selectFirst("img") == null) {
        val components = mutableListOf<TextComponent>()
        for (child in paragraph.childNodes()) {
            if (child is TextNode) {
                components.add(TextComponent(child.text()))
            } else if (child is Element) {
                components.addAll(analyseText(child, listOf()))
            }
        }
        return components.toSingle()
    }

    val results = mutableListOf<Component>()
    val currentTexts = mutableListOf<TextComponent>()

    fun flushTexts() {
        if (currentTexts.isNotEmpty()) {
            results.addAll(currentTexts.toSingle())
            currentTexts.clear()
        }
    }

    fun collectMixed(node: Node, styles: List<TextStyle>) {
        if (node is TextNode) {
            var component = TextComponent(node.text())
            for (style in styles) component = component.style(style)
            currentTexts.add(component)
        } else if (node is Element) {
            if (node.normalName() in IGNORED_TAG_NAMES) return
            if (node.tagName() == "img") {
                val imageUrl = resolveImageUrl(node)
                if (imageUrl.isNotBlank()) {
                    flushTexts()
                    results.add(ImageComponent(imageUrl))
                }
                return
            }

            if (node.selectFirst("img") == null) {
                currentTexts.addAll(analyseText(node, styles))
                return
            }

            val newStyles = extractInlineStyles(node, styles)

            for (child in node.childNodes()) {
                collectMixed(child, newStyles)
            }
        }
    }

    for (child in paragraph.childNodes()) {
        collectMixed(child, emptyList())
    }
    flushTexts()

    return results
}

internal fun resolveImageUrl(image: Element): String {
    // A non-empty placeholder src must not hide the real lazy-loaded URL.
    val baseUrl = image.baseUri().trim().ifBlank { EsjzoneUrls.Base }
    return sequenceOf(
        "data-src",
        "data-original",
        "data-lazy-src",
        "data-actualsrc",
        "data-url",
        "data-origin",
        "data-file",
        "src"
    )
        .mapNotNull { attribute ->
            val raw = image.attr(attribute).trim()
            when {
                raw.startsWith('#') -> null
                raw.startsWith("file:", ignoreCase = true) -> raw.takeIf { it.substringAfter(':').isNotBlank() }
                else -> EsjzoneUrls.resolve(raw, baseUrl).trim().takeIf { it.isNotBlank() }
            }
        }
        .firstOrNull()
        .orEmpty()
}

private val STYLE_BACKGROUND_COLOR_REGEX =
    "background-color:rgba?\\(([0-9]+),([0-9]+),([0-9]+)(?:,[0-9.]+)?\\)".toRegex(RegexOption.IGNORE_CASE)
private val STYLE_COLOR_REGEX =
    "(?<!background-)color:rgba?\\(([0-9]+),([0-9]+),([0-9]+)(?:,[0-9.]+)?\\)".toRegex(RegexOption.IGNORE_CASE)
private val STYLE_HEX_BACKGROUND_COLOR_REGEX =
    "background-color:#([0-9a-f]{3}|[0-9a-f]{6})(?![0-9a-f])".toRegex(RegexOption.IGNORE_CASE)
private val STYLE_HEX_COLOR_REGEX =
    "(?<!background-)color:#([0-9a-f]{3}|[0-9a-f]{6})(?![0-9a-f])".toRegex(RegexOption.IGNORE_CASE)
private val STYLE_FONT_SIZE_REGEX = "font-size:([0-9]+)px".toRegex(RegexOption.IGNORE_CASE)

private fun extractInlineStyles(node: Element, baseStyles: List<TextStyle>): List<TextStyle> {
    val newStyles = mutableListOf<TextStyle>()
    newStyles.addAll(baseStyles)
    when (node.normalName()) {
        "strong", "b" -> newStyles.add(BoldTextStyle)
        "em", "i" -> newStyles.add(ItalicTextStyle)
        "u", "ins" -> newStyles.add(UnderlineTextStyle)
        "s", "del", "strike" -> newStyles.add(LineThroughTextStyle)
        "a" -> node.attr("href").trim().takeIf { it.isNotEmpty() }?.let { newStyles.add(LinkTextStyle(it)) }
        "font" -> parseStandaloneColor(node.attr("color"))?.let { newStyles.add(ColorTextStyle(it)) }
    }
    val inlineCss = node.attr("style")
    if (inlineCss.isNotBlank()) {
        newStyles.addAll(analyseStyles(inlineCss.replace(" ", "")))
    }
    return newStyles
}

private fun analyseText(node: Node, styles: List<TextStyle>): List<TextComponent> {
    if (node is TextNode) {
        var component = TextComponent(node.text())
        for (style in styles)
            component = component.style(style)
        return listOf(component)
    } else if (node is Element) {
        if (node.normalName() in IGNORED_TAG_NAMES) return emptyList()
        val list = mutableListOf<TextComponent>()

        if (node.nameIs("ruby")) {
            val directRts = node.children().filter { it.nameIs("rt") }
            if (directRts.isNotEmpty()) {
                val pendingBase = mutableListOf<Node>()
                fun emitBase(appliedStyles: List<TextStyle>) {
                    for (baseNode in pendingBase) {
                        if (baseNode is TextNode) {
                            list.addAll(analyseText(baseNode, appliedStyles))
                        } else if (baseNode is Element && !baseNode.nameIs("rp") && !baseNode.nameIs("rt")) {
                            list.addAll(analyseText(baseNode, appliedStyles))
                        }
                    }
                    pendingBase.clear()
                }
                for (childNode in node.childNodes()) {
                    if (childNode is Element && childNode.nameIs("rt")) {
                        val single = analyseText(childNode, emptyList()).toSingle().firstOrNull()
                        val applied = if (single != null) styles + FuriganaTextStyle(single) else styles
                        emitBase(applied)
                    } else if (childNode is Element && childNode.nameIs("rp")) {
                        continue
                    } else {
                        pendingBase.add(childNode)
                    }
                }
                emitBase(styles)
                return list
            }
            val rt = node.getElementsByTag("rt").firstOrNull()
            if (rt != null) {
                val single = analyseText(rt, listOf()).toSingle().firstOrNull()
                if (single != null) {
                    val newStyles = styles + FuriganaTextStyle(single)
                    for (childNode in node.childNodes()) {
                        if (childNode is TextNode) {
                            list.addAll(analyseText(childNode, newStyles))
                        } else if (childNode is Element && !childNode.nameIs("rp") && !childNode.nameIs("rt")) {
                            list.addAll(analyseText(childNode, newStyles))
                        }
                    }
                    return list
                }
            }
            for (childNode in node.childNodes()) {
                if (childNode is TextNode) {
                    list.addAll(analyseText(childNode, styles))
                } else if (childNode is Element && !childNode.nameIs("rp") && !childNode.nameIs("rt")) {
                    list.addAll(analyseText(childNode, styles))
                }
            }
            return list
        } else if (node.nameIs("br")) {
            return listOf(NewLineComponent())
        }

        val newStyles = extractInlineStyles(node, styles)

        node.childNodes().map { analyseText(it, newStyles) }.forEach { list.addAll(it) }
        return list
    }
    return listOf()
}

private fun analyseStyles(styleString: String): List<TextStyle> {
    val list = mutableListOf<TextStyle>()

    for (result in STYLE_COLOR_REGEX.findAll(styleString)) {
        parseRgbColor(result)?.let { color ->
            list.add(ColorTextStyle(color))
        }
    }
    for (result in STYLE_HEX_COLOR_REGEX.findAll(styleString)) {
        parseHexColor(result.groupValues[1])?.let { color ->
            list.add(ColorTextStyle(color))
        }
    }

    for (result in STYLE_BACKGROUND_COLOR_REGEX.findAll(styleString)) {
        parseRgbColor(result)?.let { color ->
            list.add(BackgroundColorTextStyle(color))
        }
    }
    for (result in STYLE_HEX_BACKGROUND_COLOR_REGEX.findAll(styleString)) {
        parseHexColor(result.groupValues[1])?.let { color ->
            list.add(BackgroundColorTextStyle(color))
        }
    }

    for (result in STYLE_FONT_SIZE_REGEX.findAll(styleString)) {
        val size = result.groupValues.getOrNull(1)?.toIntOrNull()
        if (size != null && size in 1..512) {
            list.add(FontSizeTextStyle(size))
        }
    }

    return list.toList()
}

private fun parseStandaloneColor(raw: String): Color? {
    val trimmed = raw.trim().replace(" ", "")
    if (trimmed.startsWith("#")) return parseHexColor(trimmed.removePrefix("#"))
    val match = "rgba?\\(([0-9]+),([0-9]+),([0-9]+)(?:,[0-9.]+)?\\)".toRegex(RegexOption.IGNORE_CASE)
        .matchEntire(trimmed)
    return match?.let(::parseRgbColor)
}

private fun parseHexColor(hex: String): Color? {
    val normalized = when (hex.length) {
        3 -> hex.map { "$it$it" }.joinToString("")
        6 -> hex
        else -> return null
    }
    val rgb = normalized.toIntOrNull(16) ?: return null
    return Color((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
}

private fun parseRgbColor(result: MatchResult): Color? {
    val red = result.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
    val green = result.groupValues.getOrNull(2)?.toIntOrNull() ?: return null
    val blue = result.groupValues.getOrNull(3)?.toIntOrNull() ?: return null

    if (red !in 0..255 || green !in 0..255 || blue !in 0..255) {
        return null
    }

    return Color(red, green, blue)
}

interface Component : Serializable

open class TextComponent(val text: String) : Component {

    private val extras: MutableList<TextComponent> = mutableListOf()
    private val styles: MutableList<TextStyle> = mutableListOf()
    private var inline: FuriganaTextStyle? = null

    fun append(component: TextComponent): TextComponent {
        this.extras.add(component)
        return this
    }

    open fun style(style: TextStyle): TextComponent {
        if (style is FuriganaTextStyle)
            inline = style
        this.styles.add(style)
        return this
    }

    fun getExtras(): List<TextComponent> {
        return this.extras.toList()
    }

    fun getStyles(): List<TextStyle> {
        return this.styles.toList()
    }

    fun toAnnotatedString(
        textTransform: (String) -> String = { it }
    ): AnnotatedString {
        return buildAnnotatedString {
            var basicSpanStyle = SpanStyle()
            this@TextComponent.getStyles().forEach {
                basicSpanStyle = it.apply(basicSpanStyle)
            }


            withStyle(basicSpanStyle) {
                append(textTransform(this@TextComponent.text))
            }

            for (extra in this@TextComponent.getExtras()) {
                append(extra.toAnnotatedString(textTransform))
            }
        }
    }

    fun toInlineAnnotatedString(
        textMeasurer: TextMeasurer,
        localTextStyle: androidx.compose.ui.text.TextStyle,
        localDensity: Density,
        textTransform: (String) -> String = { it }
    ): Pair<AnnotatedString, Map<String, InlineTextContent>> {
        val inlineContent = mutableMapOf<String, InlineTextContent>()
        // val width = (text.length.toDouble() + ((text.length - 1) * 0.05) + 2).em

        return buildAnnotatedString {
            var basicSpanStyle = SpanStyle()

            this@TextComponent.getStyles().forEach {
                basicSpanStyle = it.apply(basicSpanStyle)
            }

            val inlineStyle = inline
            val displayedText = textTransform(this@TextComponent.text)
            if (inlineStyle != null) {
                withStyle(basicSpanStyle) {
                    appendInlineContent(
                        this@TextComponent.text,
                        displayedText
                    )
                }

                val size = textMeasurer
                    .measure(displayedText, localTextStyle.copy().merge(basicSpanStyle))
                    .size
                val width = with(localDensity) { size.width.toDp().toSp() }
                val height = with(localDensity) { size.height.toDp().toSp() }

                inlineContent[this@TextComponent.text] = inlineStyle.inline(
                    width,
                    height,
                    basicSpanStyle,
                    displayedText,
                    localDensity,
                    textTransform
                )
            } else {
                withStyle(basicSpanStyle) {
                    append(displayedText)
                }
            }

            for (extra in this@TextComponent.getExtras()) {
                val (str, inlines) = extra.toInlineAnnotatedString(
                    textMeasurer,
                    localTextStyle,
                    localDensity,
                    textTransform
                )
                append(str)
                inlineContent.putAll(inlines)
            }
        } to inlineContent.toMap()
    }

}

class NewLineComponent : TextComponent("\n") {

    override fun style(style: TextStyle): TextComponent {
        return this
    }

}

class ImageComponent(val url: String) : Component

interface TextStyle {

    fun apply(spanStyle: SpanStyle): SpanStyle {
        return spanStyle
    }

}

/** Retains the source href for reader link extraction without changing text styling. */
data class LinkTextStyle(val href: String) : TextStyle, Serializable

object BoldTextStyle : TextStyle {

    override fun apply(spanStyle: SpanStyle): SpanStyle {
        return spanStyle.merge(
            SpanStyle(
                fontWeight = FontWeight.Bold
            )
        )
    }

}

object UnderlineTextStyle : TextStyle {

    override fun apply(spanStyle: SpanStyle): SpanStyle {
        return spanStyle.merge(
            SpanStyle(
                textDecoration = TextDecoration.Underline
            )
        )
    }

}

object ItalicTextStyle : TextStyle {

    override fun apply(spanStyle: SpanStyle): SpanStyle {
        return spanStyle.merge(
            SpanStyle(
                fontStyle = FontStyle.Italic
            )
        )
    }

}

object LineThroughTextStyle : TextStyle {

    override fun apply(spanStyle: SpanStyle): SpanStyle {
        return spanStyle.merge(
            SpanStyle(
                textDecoration = TextDecoration.LineThrough
            )
        )
    }

}

class ColorTextStyle(private val color: Color) : TextStyle {

    fun color(): Color = color

    override fun apply(spanStyle: SpanStyle): SpanStyle {
        return spanStyle.merge(
            SpanStyle(
                color = this.color
            )
        )
    }

}

class BackgroundColorTextStyle(private val color: Color) : TextStyle {

    fun color(): Color = color

    override fun apply(spanStyle: SpanStyle): SpanStyle {
        return spanStyle.merge(
            SpanStyle(
                background = this.color
            )
        )
    }

}

class FontSizeTextStyle(private val size: Int) : TextStyle {

    fun size(): Int = size

    override fun apply(spanStyle: SpanStyle): SpanStyle {
        return spanStyle.merge(
            SpanStyle(
                fontSize = this.size.sp
            )
        )
    }

}

// Code from: https://github.com/mainrs/android-compose-furigana/blob/main/library/src/main/java/net/zerotask/libraries/android/compose/furigana/TextWithReading.kt
// License: MIT License
class FuriganaTextStyle(text: TextComponent) : TextStyle {

    private val text: TextComponent

    init {
        this.text = safer(text)
    }

    fun readingText(): TextComponent = text

    private fun safer(text: TextComponent): TextComponent {
        val newOne = TextComponent(text.text)
        for (style in text.getStyles())
            if (style !is FontSizeTextStyle)
                newOne.style(style)

        for (extra in text.getExtras())
            newOne.append(safer(extra))
        return newOne
    }

    /**
     * TODO: fix magic number, though it not a big problem
     */
    fun inline(
        width: TextUnit,
        height: TextUnit,
        style: SpanStyle,
        bottom: String,
        localDensity: Density,
        textTransform: (String) -> String = { it }
    ): InlineTextContent {
        return InlineTextContent(
            placeholder = Placeholder(
                width = width,
                height = (height.value + 18).sp,
                // height = 2.12.em,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Bottom,
            ),
            children = {
                val readingFontSize = 11.sp / 1.4
                val boxHeight = with(localDensity) { (readingFontSize).toDp() }

                Column(
                    modifier = Modifier.fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Box(
                        modifier = Modifier.requiredHeight(boxHeight + 6.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Text(
                            modifier = Modifier
                                .wrapContentWidth(unbounded = true)
                                .padding(top = 3.dp),
                            text = text.toAnnotatedString(textTransform),
                            style = androidx.compose.ui.text.TextStyle.Default.copy(fontSize = readingFontSize)
                        )
                    }
                    Text(text = buildAnnotatedString {
                        withStyle(style) {
                            append(textTransform(bottom))
                        }
                    })
                }
            }
        )
    }

}

private fun List<TextComponent>.toSingle(): List<TextComponent> {
    val first = firstOrNull() ?: return emptyList()
    val rest = drop(1)
    // A standalone break has no text in the reader. Keep the entire group as a
    // paragraph when a leading break is followed by prose.
    val paragraph = if (first is NewLineComponent && rest.any { it.text.isNotBlank() }) {
        TextComponent(first.text)
    } else first

    for (component in rest)
        paragraph.append(component)

    return listOf(paragraph)
}
