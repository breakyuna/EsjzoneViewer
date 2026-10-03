package com.breakyuna.esjzone.ui.reader

import com.breakyuna.esjzone.domain.reader.ReaderUnderline
import com.breakyuna.esjzone.domain.reader.ReaderUnderlines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderChapterDocument
import com.breakyuna.esjzone.ui.designsystem.AppImageViewer
import com.breakyuna.esjzone.ui.designsystem.AppReaderImage
import com.breakyuna.esjzone.ui.designsystem.AppShapes

/**
 * Domain-AST renderer. The renderer has no dependency on legacy Component
 * classes, network clients, or navigation, so the reader shell can evolve
 * independently from parsing and persistence.
 */
@Composable
fun ReaderRenderer(
    document: ReaderChapterDocument,
    chapterName: String,
    settings: ReaderSettings,
    textMeasurer: TextMeasurer,
    density: Density,
    contentColor: Color,
    textTransform: (String) -> String = { it }
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ReaderChapterHeading(chapterName, settings, contentColor, textTransform)
        ReaderBlocks(document.blocks, settings, textMeasurer, density, contentColor, textTransform)
    }
}

/** Render a small slice of a chapter in one lazy-list item. */
@Composable
fun ReaderBlocks(
    blocks: List<ReaderBlock>,
    settings: ReaderSettings,
    textMeasurer: TextMeasurer,
    density: Density,
    contentColor: Color,
    textTransform: (String) -> String = { it },
    blockStartIndex: Int = 0,
    underlines: List<ReaderUnderline> = emptyList(),
    onUnderline: (ReaderUnderline, Boolean) -> Unit = { _, _ -> }
) {
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = settings.font.family(),
        fontSize = settings.fontSizeSp.sp,
        lineHeight = settings.lineHeightSp.sp,
        letterSpacing = settings.letterSpacingSp.sp
    )

    val content: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth()) {
            blocks.forEachIndexed { index, block ->
                when (block) {
                    is ReaderBlock.Paragraph -> ReaderParagraphBlock(
                        block = block,
                        textStyle = textStyle,
                        textMeasurer = textMeasurer,
                        density = density,
                        textTransform = textTransform,
                        contentColor = contentColor,
                        paragraphSpacingDp = settings.paragraphSpacingDp,
                        enabled = settings.longPressUnderline,
                        blockIndex = blockStartIndex + index,
                        underlines = underlines,
                        onUnderline = onUnderline
                    )
                    is ReaderBlock.Text -> ReaderTextBlock(
                        block = block,
                        textStyle = textStyle,
                        textMeasurer = textMeasurer,
                        density = density,
                        textTransform = textTransform,
                        contentColor = contentColor,
                        paragraphSpacingDp = settings.paragraphSpacingDp,
                        enabled = settings.longPressUnderline,
                        blockIndex = blockStartIndex + index,
                        underlines = underlines,
                        onUnderline = onUnderline
                    )
                    is ReaderBlock.Image -> ReaderImage(
                        url = block.url,
                        contentDescription = stringResource(R.string.reader_open_image),
                        modifier = Modifier.padding(vertical = settings.paragraphSpacingDp.dp)
                    )
                    ReaderBlock.LineBreak -> Spacer(
                        modifier = Modifier.height(with(density) { settings.lineHeightSp.sp.toDp() })
                    )
                }
            }
        }
    }
    if (settings.longPressUnderline) DisableSelection { content() } else SelectionContainer { content() }
}

@Composable
private fun ReaderParagraphBlock(
    block: ReaderBlock.Paragraph,
    textStyle: TextStyle,
    textMeasurer: TextMeasurer,
    density: Density,
    textTransform: (String) -> String,
    contentColor: Color,
    paragraphSpacingDp: Float,
    enabled: Boolean,
    blockIndex: Int,
    underlines: List<ReaderUnderline>,
    onUnderline: (ReaderUnderline, Boolean) -> Unit
) {
    val (paragraph, inlineContent) = remember(block, textStyle, textTransform, density) {
        val inlines = linkedMapOf<String, InlineTextContent>()
        val annotated = buildAnnotatedString {
            block.parts.forEach { part ->
                val (text, partInlines) = part.toAnnotatedReaderText(
                    baseStyle = textStyle,
                    textMeasurer = textMeasurer,
                    density = density,
                    textTransform = textTransform
                )
                append(text)
                inlines.putAll(partInlines)
            }
        }
        annotated to inlines
    }
    ReaderUnderlineText(
        text = paragraph,
        inlineContent = inlineContent,
        style = textStyle,
        color = contentColor,
        modifier = Modifier.padding(bottom = paragraphSpacingDp.dp),
        enabled = enabled,
        blockIndex = blockIndex,
        signature = remember(block, paragraph) { ReaderUnderlines.signature(block, paragraph.text) },
        underlines = underlines,
        onUnderline = onUnderline
    )
}

@Composable
private fun ReaderTextBlock(
    block: ReaderBlock.Text,
    textStyle: TextStyle,
    textMeasurer: TextMeasurer,
    density: Density,
    textTransform: (String) -> String,
    contentColor: Color,
    paragraphSpacingDp: Float,
    enabled: Boolean,
    blockIndex: Int,
    underlines: List<ReaderUnderline>,
    onUnderline: (ReaderUnderline, Boolean) -> Unit
) {
    val (text, inlineContent) = remember(block, textStyle, textTransform, density) {
        block.toAnnotatedReaderText(
            baseStyle = textStyle,
            textMeasurer = textMeasurer,
            density = density,
            textTransform = textTransform
        )
    }
    ReaderUnderlineText(
        text = text,
        inlineContent = inlineContent,
        style = textStyle,
        color = contentColor,
        modifier = Modifier.padding(bottom = paragraphSpacingDp.dp),
        enabled = enabled,
        blockIndex = blockIndex,
        signature = remember(block, text) { ReaderUnderlines.signature(block, text.text) },
        underlines = underlines,
        onUnderline = onUnderline
    )
}

@Composable
fun ReaderChapterHeading(
    name: String,
    settings: ReaderSettings,
    contentColor: Color,
    textTransform: (String) -> String = { it },
    overflow: TextOverflow = TextOverflow.Clip
) {
    Text(
        text = textTransform(name),
        style = MaterialTheme.typography.headlineSmall.copy(
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            fontFamily = settings.font.family(),
            fontSize = (settings.fontSizeSp + 6f).sp,
            lineHeight = (settings.lineHeightSp + 6f).sp,
            letterSpacing = settings.letterSpacingSp.sp
        ),
        color = contentColor,
        overflow = overflow,
        modifier = Modifier
            .padding(bottom = (settings.paragraphSpacingDp + 8f).dp)
            .semantics { heading() }
    )
}

@Composable
internal fun ReaderImage(
    url: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.FillWidth
) {
    var expanded by rememberSaveable(url) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.standard)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f), AppShapes.standard)
            .clickable { expanded = true }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        AppReaderImage(
            model = url,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (expanded) {
        AppImageViewer(model = url, contentDescription = contentDescription,
            onDismiss = { expanded = false })
    }
}
