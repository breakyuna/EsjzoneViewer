package com.breakyuna.esjzone.ui.page

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.ui.reader.ReaderNameReplaceSettings
import com.breakyuna.esjzone.ui.reader.ReaderPage
import com.breakyuna.esjzone.ui.reader.ReaderScript
import com.breakyuna.esjzone.ui.reader.ReaderSettings
import com.breakyuna.esjzone.ui.reader.paginateReaderChapter
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@Composable
internal fun chapterPagination(
    chapters: List<ReaderChapter>?,
    viewport: IntSize,
    topPadding: Dp,
    bottomPadding: Dp,
    readerSettings: ReaderSettings,
    textStyle: TextStyle,
    nameReplaceSettings: ReaderNameReplaceSettings
): ChapterPagination {
    val density = LocalDensity.current
    val widthPx = with(density) {
        (viewport.width - 2 * readerSettings.horizontalPaddingDp.dp.roundToPx()).coerceAtLeast(0)
    }
    val heightPx = with(density) {
        (viewport.height - topPadding.roundToPx() - bottomPadding.roundToPx()).coerceAtLeast(0)
    }
    val headingStyle = MaterialTheme.typography.headlineSmall.copy(
        fontWeight = FontWeight.Bold,
        fontFamily = readerSettings.font.family(),
        fontSize = (readerSettings.fontSizeSp + 6f).sp,
        lineHeight = (readerSettings.lineHeightSp + 6f).sp,
        letterSpacing = readerSettings.letterSpacingSp.sp
    )
    // Only layout settings invalidate pagination. Toggling chrome never measures text again.
    val settings = ReaderSettings(
        firstLineIndentChars = readerSettings.firstLineIndentChars,
        font = readerSettings.font,
        fontSizeSp = readerSettings.fontSizeSp,
        letterSpacingSp = readerSettings.letterSpacingSp,
        lineSpacingSp = readerSettings.lineSpacingSp,
        paragraphSpacingDp = readerSettings.paragraphSpacingDp
    )
    return ChapterPagination(
        chapters, settings, textStyle, headingStyle, density, LocalLayoutDirection.current,
        LocalFontFamilyResolver.current, widthPx, heightPx, readerSettings.script, nameReplaceSettings
    )
}

internal class ChapterPagination(
    chapters: List<ReaderChapter>?,
    val settings: ReaderSettings,
    private val textStyle: TextStyle,
    private val headingStyle: TextStyle,
    private val density: Density,
    private val layoutDirection: LayoutDirection,
    private val fontResolver: FontFamily.Resolver,
    private val widthPx: Int,
    val heightPx: Int,
    script: ReaderScript,
    nameReplaceSettings: ReaderNameReplaceSettings
) {
    private val entries = chapters.orEmpty()
    val layoutKey = listOf(widthPx, heightPx, settings, textStyle, headingStyle, density,
        layoutDirection, fontResolver, script, nameReplaceSettings)
    val key = layoutKey + listOf(chapters)
    val canMeasure: Boolean get() = widthPx > 0 && heightPx > 0

    suspend fun measure(previous: ReaderPaginationSnapshot?, transform: (String) -> String): ReaderPaginationSnapshot {
        val started = System.nanoTime()
        val cached = previous?.takeIf { it.layoutKey == layoutKey }?.pages.orEmpty()
        val snapshot = withContext(Dispatchers.Default) {
            // Keep the worker's measurer separate from UI text layout.
            val measurer = TextMeasurer(fontResolver, density, layoutDirection, cacheSize = 0)
            val pages = entries.associateWith { entry ->
                ensureActive()
                if (entry.requiresPassword) listOf(ReaderPage(emptyList(), -1f, isOversized = true))
                else cached[entry] ?: paginateReaderChapter(
                    entry.chapter.name, entry.document.blocks, settings, textStyle, headingStyle,
                    measurer, density, widthPx, heightPx, transform
                )
            }
            val items = entries.flatMap { entry ->
                val chapterKey = chapterIdentity(entry.chapter)
                val chapterPages = pages.getValue(entry)
                chapterPages.mapIndexed { index, page ->
                    ReaderDisplayItem("$chapterKey|page:$index", chapterKey, entry,
                        index, chapterPages.size, emptyList(), page)
                }
            }
            ReaderPaginationSnapshot(key, layoutKey, pages, items)
        }
        AppLogger.i("ReaderTiming", "paginationMs=${(System.nanoTime() - started) / 1_000_000} chapters=${entries.size} pages=${snapshot.items.size}")
        return snapshot
    }
}

internal fun scrollChapterItems(entries: List<ReaderChapter>): List<ReaderDisplayItem> = entries.flatMap { entry ->
    val chapterKey = chapterIdentity(entry.chapter)
    val blocks = entry.document.blocks
    val count = blocks.size + 1
    listOf(ReaderDisplayItem(chapterKey, chapterKey, entry, 0, count, emptyList())) +
        blocks.mapIndexed { index, block ->
            ReaderDisplayItem("$chapterKey|block:$index", chapterKey, entry, index + 1, count, listOf(block))
        }
}

internal data class ReaderPaginationSnapshot(
    val key: List<Any?>,
    val layoutKey: List<Any?>,
    val pages: Map<ReaderChapter, List<ReaderPage>>,
    val items: List<ReaderDisplayItem>
) {
    fun itemsFor(entries: List<ReaderChapter>): List<ReaderDisplayItem> {
        // Retain the password page's stable key until its replacement body is paginated.
        val loadedKeys = entries.map { chapterIdentity(it.chapter) }.toSet()
        return items.filter { it.chapterKey in loadedKeys }
    }

    fun hasReadablePage(currentLayoutKey: List<Any?>, entry: ReaderChapter?, loaded: List<ReaderChapter>): Boolean =
        layoutKey == currentLayoutKey && entry != null && entry in loaded && !pages[entry].isNullOrEmpty()
}

internal data class ReaderDisplayItem(
    val key: String,
    val chapterKey: String,
    val entry: ReaderChapter,
    val ordinal: Int,
    val itemCount: Int,
    val blocks: List<ReaderBlock>,
    val page: ReaderPage? = null
)

