package com.breakyuna.esjzone.ui.designsystem

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.breakyuna.esjzone.ui.reader.ReaderScript
import com.breakyuna.esjzone.ui.reader.ReaderScriptConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val LocalGlobalScript = staticCompositionLocalOf { ReaderScript.ORIGINAL }

@Composable
fun GlobalText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current
) {
    val script = LocalGlobalScript.current
    val displayed = if (script == ReaderScript.ORIGINAL || text.length <= 512) {
        remember(text, script) { ReaderScriptConverter.convert(text, script) }
    } else {
        val converted by produceState(text, text, script) {
            value = withContext(Dispatchers.Default) { ReaderScriptConverter.convert(text, script) }
        }
        converted
    }
    MaterialText(
        text = displayed, modifier = modifier, color = color, fontSize = fontSize,
        fontStyle = fontStyle, fontWeight = fontWeight, fontFamily = fontFamily,
        letterSpacing = letterSpacing, textDecoration = textDecoration,
        textAlign = textAlign, lineHeight = lineHeight, overflow = overflow,
        softWrap = softWrap, maxLines = maxLines, minLines = minLines,
        onTextLayout = onTextLayout, style = style
    )
}

@Composable
fun GlobalText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current
) {
    val script = LocalGlobalScript.current
    val displayed = if (script == ReaderScript.ORIGINAL || text.length <= 512) {
        remember(text, script) { convertAnnotatedText(text, script) }
    } else {
        val converted by produceState(text, text, script) {
            value = withContext(Dispatchers.Default) { convertAnnotatedText(text, script) }
        }
        converted
    }
    MaterialText(
        text = displayed, modifier = modifier, color = color, fontSize = fontSize,
        fontStyle = fontStyle, fontWeight = fontWeight, fontFamily = fontFamily,
        letterSpacing = letterSpacing, textDecoration = textDecoration,
        textAlign = textAlign, lineHeight = lineHeight, overflow = overflow,
        softWrap = softWrap, maxLines = maxLines, minLines = minLines,
        inlineContent = inlineContent, onTextLayout = onTextLayout, style = style
    )
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun convertAnnotatedText(text: AnnotatedString, script: ReaderScript): AnnotatedString {
    if (script == ReaderScript.ORIGINAL) return text
    val mapping = ReaderScriptConverter.mapping(text.text, script)
    val startOffsets = HashMap<Int, Int>()
    val endOffsets = HashMap<Int, Int>()
    fun startOffset(index: Int): Int = startOffsets.getOrPut(index) {
        val clamped = index.coerceIn(0, mapping.sourceLength)
        var lo = 0
        var hi = mapping.text.length
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (mapping.sourceEnds[mid] > clamped) hi = mid else lo = mid + 1
        }
        lo
    }
    fun endOffset(start: Int, end: Int): Int {
        if (end <= start) return startOffset(start)
        val mappedEnd = endOffsets.getOrPut(end) {
            val clamped = end.coerceIn(0, mapping.sourceLength)
            var lo = 0
            var hi = mapping.text.length
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (mapping.sourceStarts[mid] >= clamped) hi = mid else lo = mid + 1
            }
            lo
        }
        return mappedEnd.coerceAtLeast(startOffset(start))
    }
    return AnnotatedString.Builder(mapping.text).apply {
        text.spanStyles.forEach { addStyle(it.item, startOffset(it.start), endOffset(it.start, it.end)) }
        text.paragraphStyles.forEach { addStyle(it.item, startOffset(it.start), endOffset(it.start, it.end)) }
        text.getStringAnnotations(0, text.length).forEach {
            addStringAnnotation(it.tag, it.item, startOffset(it.start), endOffset(it.start, it.end))
        }
        text.getTtsAnnotations(0, text.length).forEach {
            addTtsAnnotation(it.item, startOffset(it.start), endOffset(it.start, it.end))
        }
        text.getUrlAnnotations(0, text.length).forEach {
            addUrlAnnotation(it.item, startOffset(it.start), endOffset(it.start, it.end))
        }
        text.getLinkAnnotations(0, text.length).forEach {
            when (val link = it.item) {
                is LinkAnnotation.Url -> addLink(link, startOffset(it.start), endOffset(it.start, it.end))
                is LinkAnnotation.Clickable -> addLink(link, startOffset(it.start), endOffset(it.start, it.end))
                else -> Unit
            }
        }
    }.toAnnotatedString()
}
