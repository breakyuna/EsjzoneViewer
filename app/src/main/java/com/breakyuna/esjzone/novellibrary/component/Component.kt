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
import java.util.IdentityHashMap


private val BLOCK_TAG_NAMES = setOf(
    "p", "div", "section", "article", "blockquote", "main", "aside",
    "header", "footer", "h1", "h2", "h3", "h4", "h5", "h6",
    "ul", "ol", "li", "pre", "hr", "table", "thead", "tbody", "tfoot",
    "tr", "td", "th", "details", "figure", "figcaption", "dl", "dt", "dd",
    "center", "address", "fieldset", "legend"
)

private val IGNORED_TAG_NAMES = setOf("script", "style", "noscript", "template", "rp")
internal val IMAGE_URL_ATTRIBUTES = listOf(
    "data-src", "data-original", "data-lazy-src", "data-actualsrc", "data-url", "data-origin", "data-file", "src"
)

private fun Element.hasBlockDescendants(): Boolean {
    val stack = ArrayDeque<Element>()
    for (child in childNodes()) {
        if (child is Element && child.normalName() !in IGNORED_TAG_NAMES) {
            if (child.normalName() in BLOCK_TAG_NAMES) return true
            stack.addLast(child)
        }
    }
    while (stack.isNotEmpty()) {
        val current = stack.removeLast()
        for (child in current.childNodes()) {
            if (child is Element && child.normalName() !in IGNORED_TAG_NAMES) {
                if (child.normalName() in BLOCK_TAG_NAMES) return true
                stack.addLast(child)
            }
        }
    }
    return false
}

private fun computeBlockDescendants(root: Element): IdentityHashMap<Element, Boolean> {
    val hasBlockDescendant = IdentityHashMap<Element, Boolean>()
    if (root.normalName() in IGNORED_TAG_NAMES) return hasBlockDescendant
    val stack = ArrayDeque<Element>()
    val postOrder = ArrayList<Element>()
    stack.addLast(root)
    while (stack.isNotEmpty()) {
        val current = stack.removeLast()
        postOrder.add(current)
        for (child in current.childNodes()) {
            if (child is Element && child.normalName() !in IGNORED_TAG_NAMES) {
                stack.addLast(child)
            }
        }
    }
    for (i in postOrder.size - 1 downTo 0) {
        val element = postOrder[i]
        for (child in element.childNodes()) {
            if (child is Element && child.normalName() !in IGNORED_TAG_NAMES) {
                if (child.normalName() in BLOCK_TAG_NAMES || hasBlockDescendant.containsKey(child)) {
                    hasBlockDescendant[element] = true
                    break
                }
            }
        }
    }
    return hasBlockDescendant
}

private fun Element.isSignificantParagraph(): Boolean =
    text().isNotBlank() || selectFirst("img, br") != null

/** Synthetic paragraphs keep their source parent available to formatting consumers. */
internal fun extractParagraphElements(
    container: Element,
    onAnonymousParagraph: (Element, Element) -> Unit = { _, _ -> }
): List<Element> = buildList {
    if (container.normalName() in IGNORED_TAG_NAMES) return@buildList
    val blockDescendants = computeBlockDescendants(container)

    fun collect(element: Element) {
        if (element.normalName() in IGNORED_TAG_NAMES) return
        if (!blockDescendants.containsKey(element)) {
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
            if (child is Element && (child.normalName() in BLOCK_TAG_NAMES || blockDescendants.containsKey(child))) {
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
        components.addAll(analyseParagraphContent(paragraph))
    }

    return components.toList()
}


internal fun analyseParagraph(paragraph: Element): List<Component> {
    if (paragraph.normalName() in IGNORED_TAG_NAMES) return emptyList()
    if (paragraph.hasBlockDescendants()) {
        return analyseComponents(paragraph)
    }
    return analyseParagraphContent(paragraph)
}

private fun analyseParagraphContent(paragraph: Element): List<Component> {
    if (paragraph.normalName() in IGNORED_TAG_NAMES) return emptyList()

    if (paragraph.normalName() == "img") {
        val imageUrl = resolveImageUrl(paragraph)
        return if (imageUrl.isNotBlank()) listOf(ImageComponent(imageUrl)) else emptyList()
    }

    val images = paragraph.getElementsByTag("img")
    if (images.isEmpty()) {
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

    val imgAncestors = IdentityHashMap<Element, Boolean>()
    for (img in images) {
        var current = img.parent()
        while (current != null && current !== paragraph) {
            if (imgAncestors.put(current, true) != null) break
            current = current.parent()
        }
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
            if (node.normalName() == "img") {
                val imageUrl = resolveImageUrl(node)
                if (imageUrl.isNotBlank()) {
                    flushTexts()
                    results.add(ImageComponent(imageUrl))
                }
                return
            }

            if (!imgAncestors.containsKey(node)) {
                currentTexts.addAll(analyseText(node, styles))
                return
            }
            if (node.nameIs("ruby")) {
                collectRuby(node, styles, ::collectMixed)
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
    return IMAGE_URL_ATTRIBUTES.asSequence()
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
private val STANDALONE_RGB_COLOR_REGEX =
    "rgba?\\(([0-9]+),([0-9]+),([0-9]+)(?:,[0-9.]+)?\\)".toRegex(RegexOption.IGNORE_CASE)

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

private fun collectRuby(ruby: Element, styles: List<TextStyle>, emit: (Node, List<TextStyle>) -> Unit) {
    val base = mutableListOf<Pair<Node, List<TextStyle>>>()
    val readings = mutableListOf<TextComponent>()

    fun flush() {
        val reading = readings.firstOrNull()?.also { first ->
            for (extra in readings.drop(1)) first.append(TextComponent(" ")).append(extra)
        }
        val annotation = reading?.let(::FuriganaTextStyle)
        for ((node, inherited) in base) emit(node, if (annotation == null) inherited else inherited + annotation)
        base.clear()
        readings.clear()
    }

    fun walk(node: Node, inherited: List<TextStyle>) {
        when (node) {
            is TextNode -> {
                if (readings.isNotEmpty() && node.text().isNotBlank()) flush()
                base.add(node to inherited)
            }
            is Element -> when (node.normalName()) {
                in IGNORED_TAG_NAMES -> Unit
                "rt" -> analyseText(node, emptyList()).toSingle().firstOrNull()?.let(readings::add)
                "img", "br", "ruby" -> {
                    if (readings.isNotEmpty()) flush()
                    base.add(node to inherited)
                }
                else -> {
                    val nestedStyles = extractInlineStyles(node, inherited)
                    node.childNodes().forEach { walk(it, nestedStyles) }
                }
            }
        }
    }

    ruby.childNodes().forEach { walk(it, styles) }
    flush()
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
            collectRuby(node, styles) { base, inherited -> list.addAll(analyseText(base, inherited)) }
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
    val match = STANDALONE_RGB_COLOR_REGEX.matchEntire(trimmed)
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
