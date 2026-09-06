package com.breakyuna.esjzone.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderChapterDocument
import com.breakyuna.esjzone.ui.designsystem.AppImage
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppSpacing

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
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = settings.font.family,
        fontSize = settings.fontSizeSp.sp,
        lineHeight = settings.lineHeightSp.sp,
        letterSpacing = settings.letterSpacingSp.sp
    )
    SelectionContainer {
        Column(modifier = Modifier.fillMaxWidth()) {
            ReaderChapterHeading(
                name = chapterName,
                settings = settings,
                contentColor = contentColor,
                textTransform = textTransform
            )

            document.blocks.forEach { block ->
                when (block) {
                    is ReaderBlock.Text -> {
                        val (text, inlineContent) = block.toAnnotatedReaderText(
                            baseStyle = textStyle,
                            textMeasurer = textMeasurer,
                            density = density,
                            textTransform = textTransform
                        )
                        Text(
                            text = text,
                            inlineContent = inlineContent,
                            style = textStyle,
                            color = contentColor,
                            modifier = Modifier.padding(bottom = settings.paragraphSpacingDp.dp)
                        )
                    }
                    is ReaderBlock.Image -> ReaderImage(
                        url = block.url,
                        contentDescription = stringResource(R.string.reader_open_image),
                        modifier = Modifier.padding(vertical = settings.paragraphSpacingDp.dp)
                    )
                    ReaderBlock.LineBreak -> Spacer(
                        modifier = Modifier.height(textStyle.lineHeight.value.sp)
                    )
                }
            }
        }
    }
}

@Composable
fun ReaderChapterHeading(
    name: String,
    settings: ReaderSettings,
    contentColor: Color,
    textTransform: (String) -> String = { it }
) {
    Text(
        text = textTransform(name),
        style = MaterialTheme.typography.headlineSmall.copy(
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            fontFamily = settings.font.family,
            fontSize = (settings.fontSizeSp + 6f).sp,
            lineHeight = (settings.lineHeightSp + 6f).sp,
            letterSpacing = settings.letterSpacingSp.sp
        ),
        color = contentColor,
        modifier = Modifier
            .padding(bottom = (settings.paragraphSpacingDp + 8f).dp)
            .semantics { heading() }
    )
}

@Composable
private fun ReaderImage(
    url: String,
    contentDescription: String,
    modifier: Modifier = Modifier
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
        AppImage(
            model = url,
            contentDescription = contentDescription,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (expanded) {
        Dialog(
            onDismissRequest = { expanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AppImage(
                        model = url,
                        contentDescription = contentDescription,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { expanded = false }
                    )
                    IconButton(
                        onClick = { expanded = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(AppSpacing.sm)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
                                AppShapes.pill
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
