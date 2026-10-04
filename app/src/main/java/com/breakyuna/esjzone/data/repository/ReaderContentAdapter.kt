package com.breakyuna.esjzone.data.repository

import androidx.compose.ui.graphics.Color
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderTextStyle
import com.breakyuna.esjzone.novellibrary.component.BackgroundColorTextStyle
import com.breakyuna.esjzone.novellibrary.component.BoldTextStyle
import com.breakyuna.esjzone.novellibrary.component.ColorTextStyle
import com.breakyuna.esjzone.novellibrary.component.Component
import com.breakyuna.esjzone.novellibrary.component.FontSizeTextStyle
import com.breakyuna.esjzone.novellibrary.component.FuriganaTextStyle
import com.breakyuna.esjzone.novellibrary.component.ImageComponent
import com.breakyuna.esjzone.novellibrary.component.ItalicTextStyle
import com.breakyuna.esjzone.novellibrary.component.LineThroughTextStyle
import com.breakyuna.esjzone.novellibrary.component.NewLineComponent
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.novellibrary.component.TextStyle
import com.breakyuna.esjzone.novellibrary.component.UnderlineTextStyle

fun Component.toReaderBlocks(): List<ReaderBlock> = when (this) {
    is ImageComponent -> listOf(ReaderBlock.Image(url))
    is NewLineComponent -> listOf(ReaderBlock.LineBreak)
    is TextComponent -> {
        ReaderBlock.Paragraph(flattenParagraphParts())
            .let(::listOf)
    }
    else -> emptyList()
}

private fun TextComponent.flattenParagraphParts(): List<ReaderBlock.Text> = buildList {
    fun appendPart(component: TextComponent) {
        val reading = component.getStyles()
            .filterIsInstance<FuriganaTextStyle>()
            .firstOrNull()
            ?.readingText()
            ?.let { it.text + it.getExtras().joinToString("") { extra -> extra.text } }
        add(
            ReaderBlock.Text(
                value = component.text,
                styles = component.getStyles().mapNotNull(TextStyle::toReaderStyle).toSet(),
                ruby = reading?.let { com.breakyuna.esjzone.domain.reader.ReaderRuby(component.text, it) }
            )
        )
        component.getExtras().forEach(::appendPart)
    }
    appendPart(this@flattenParagraphParts)
}

private fun TextStyle.toReaderStyle(): ReaderTextStyle? = when {
    this === BoldTextStyle -> ReaderTextStyle.Bold
    this === ItalicTextStyle -> ReaderTextStyle.Italic
    this === UnderlineTextStyle -> ReaderTextStyle.Underline
    this === LineThroughTextStyle -> ReaderTextStyle.StrikeThrough
    this is FontSizeTextStyle -> ReaderTextStyle.FontSizePx(size())
    this is ColorTextStyle -> color().toReaderColor { red, green, blue ->
        ReaderTextStyle.ForegroundColor(red, green, blue)
    }
    this is BackgroundColorTextStyle -> color().toReaderColor { red, green, blue ->
        ReaderTextStyle.BackgroundColor(red, green, blue)
    }
    this is FuriganaTextStyle -> null
    else -> null
}

private fun Color.toReaderColor(factory: (Int, Int, Int) -> ReaderTextStyle): ReaderTextStyle =
    factory((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

