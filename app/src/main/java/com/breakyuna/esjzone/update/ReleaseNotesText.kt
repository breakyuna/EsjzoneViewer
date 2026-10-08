package com.breakyuna.esjzone.update

import android.graphics.Typeface
import android.text.Spanned
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import com.breakyuna.esjzone.ui.designsystem.LocalGlobalScript
import com.breakyuna.esjzone.ui.reader.ReaderScript
import com.breakyuna.esjzone.ui.reader.ReaderScriptConverter
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Text as MarkdownText

@Composable
internal fun ReleaseNotesText(markdown: String) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val textColor = LocalContentColor.current
    val style = LocalTextStyle.current
    val density = LocalDensity.current
    val script = LocalGlobalScript.current
    val typeface = LocalFontFamilyResolver.current.resolve(
        style.fontFamily,
        style.fontWeight ?: FontWeight.Normal,
        style.fontStyle ?: FontStyle.Normal,
        style.fontSynthesis ?: FontSynthesis.All
    ).value as Typeface
    val textSize = with(density) { style.fontSize.toPx() }
    val lineHeight = with(density) { style.lineHeight.toPx() }
    val markwon = remember(context, colors, textColor) {
        Markwon.builder(context)
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder
                        .linkColor(colors.primary.toArgb())
                        .codeTextColor(textColor.toArgb())
                        .codeBlockTextColor(textColor.toArgb())
                        .codeBackgroundColor(colors.surfaceContainerHigh.toArgb())
                        .codeBlockBackgroundColor(colors.surfaceContainerHigh.toArgb())
                        .blockQuoteColor(colors.outline.toArgb())
                        .headingBreakColor(colors.outlineVariant.toArgb())
                        .thematicBreakColor(colors.outlineVariant.toArgb())
                }
            })
            .build()
    }
    val rendered by produceState<Spanned?>(null, markdown, script, markwon) {
        value = withContext(Dispatchers.Default) {
            val document = markwon.parse(markdown)
            if (script != ReaderScript.ORIGINAL) {
                // Convert visible prose while keeping link destinations and code intact.
                document.accept(object : AbstractVisitor() {
                    override fun visit(text: MarkdownText) {
                        text.literal = ReaderScriptConverter.convert(text.literal, script)
                    }
                })
            }
            markwon.render(document)
        }
    }
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { TextView(it).apply { includeFontPadding = false } },
        update = { view ->
            view.typeface = typeface
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
            view.setLineSpacing(0f, lineHeight / textSize)
            view.setTextColor(textColor.toArgb())
            view.setLinkTextColor(colors.primary.toArgb())
            rendered?.let { markwon.setParsedMarkdown(view, it) }
        }
    )
}
