package com.breakyuna.esjzone.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.database.entity.BookshelfEntry
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import kotlin.math.roundToInt

private val recentReadOrder = listOf(1, 0, 2, 3)
private val recentReadScales = listOf(1f, 0.85f, 0.85f, 0.70f)
private const val COVER_ASPECT_RATIO = 0.7f
private const val OVERLAP_FRACTION = 0.08f

/**
 * Input is newest-read first. Physical left-to-right order is 2, 1, 3, 4, with the newest
 * cover highest and in front. All covers share a baseline and retain their portrait ratio.
 * The shelf below remains complete; these are shortcuts, not removed/reordered grid rows.
 */
@Composable
fun AppBookshelfRecentReads(
    books: List<BookshelfEntry>,
    onBookClick: (BookshelfEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    if (books.isEmpty()) return
    val order = remember(books.size) { recentReadOrder.filter { it < books.size } }
    val colors = MaterialTheme.colorScheme
    val shape = AppShapes.compact
    Layout(
        modifier = modifier.fillMaxWidth().semantics { isTraversalGroup = true },
        content = {
            // A shallow, theme-aware shelf under the common baseline; purely decorative.
            Box(Modifier.drawWithCache {
                val edge = size.height * 0.65f
                val platform = Path().apply {
                    moveTo(edge, 0f)
                    lineTo(size.width - edge, 0f)
                    quadraticTo(size.width, size.height * 0.40f, size.width, size.height * 0.65f)
                    quadraticTo(size.width, size.height, size.width - edge, size.height)
                    lineTo(edge, size.height)
                    quadraticTo(0f, size.height, 0f, size.height * 0.65f)
                    quadraticTo(0f, size.height * 0.40f, edge, 0f)
                    close()
                }
                val finish = Brush.verticalGradient(
                    listOf(colors.surfaceContainerHighest, colors.surfaceContainerLow)
                )
                onDrawBehind { drawPath(platform, finish) }
            })
            order.forEach { rank ->
                val entry = books[rank]
                key(entry.bookKey) {
                    val rankDescription = stringResource(R.string.bookshelf_recent_rank, rank + 1)
                    AppNovelCover(
                        coverUrl = entry.coverUrl,
                        title = entry.title.ifBlank { stringResource(R.string.download_unknown_novel) },
                        modifier = Modifier
                            .shadow(
                                elevation = if (rank == 0) 10.dp else 5.dp,
                                shape = shape,
                                clip = false,
                                ambientColor = Color.Black.copy(alpha = 0.12f),
                                spotColor = Color.Black.copy(alpha = 0.24f)
                            )
                            .clip(shape)
                            .clickable(role = Role.Button, onClick = { onBookClick(entry) })
                            .semantics(mergeDescendants = true) {
                                stateDescription = rankDescription
                                // TalkBack reads newest first, independent of the visual stacking.
                                traversalIndex = rank.toFloat()
                            }
                    )
                }
            }
        }
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val sideGutter = 12.dp.roundToPx()
        val topGutter = 10.dp.roundToPx()
        val bottomGutter = 14.dp.roundToPx()
        val units = order.sumOf { recentReadScales[it].toDouble() }.toFloat() -
            OVERLAP_FRACTION * (order.size - 1)
        val availableWidth = (width - sideGutter * 2).coerceAtLeast(0)
        val primaryWidth = minOf(
            availableWidth / units,
            168.dp.toPx(),
            (constraints.maxHeight - topGutter - bottomGutter).coerceAtLeast(0) * COVER_ASPECT_RATIO
        )
        val covers = order.mapIndexed { index, rank ->
            val coverWidth = (primaryWidth * recentReadScales[rank]).roundToInt()
            measurables[index + 1].measure(
                Constraints.fixed(coverWidth, (coverWidth / COVER_ASPECT_RATIO).roundToInt())
            )
        }
        val overlap = (primaryWidth * OVERLAP_FRACTION).roundToInt()
        val groupWidth = covers.sumOf { it.width } - overlap * (covers.size - 1)
        val groupHeight = covers.maxOf { it.height }
        val height = constraints.constrainHeight(topGutter + groupHeight + bottomGutter)
        val baseline = height - bottomGutter
        val shelf = measurables.first().measure(
            Constraints.fixed(
                (groupWidth + 14.dp.roundToPx()).coerceAtMost(width),
                12.dp.roundToPx()
            )
        )

        layout(width, height) {
            shelf.place((width - shelf.width) / 2, baseline - 8.dp.roundToPx())
            var x = (width - groupWidth) / 2
            covers.forEachIndexed { index, cover ->
                // Absolute placement intentionally preserves the reference's 2-1-3-4 order.
                // The newest cover also wins hit testing in the small overlap region.
                cover.place(x, baseline - cover.height, zIndex = (4 - order[index]).toFloat())
                x += cover.width - overlap
            }
        }
    }
}
