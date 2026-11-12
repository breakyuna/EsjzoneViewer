package com.breakyuna.esjzone.ui.reader

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.math.ceil

internal object ReaderGlowQuality {
    val reduced = MutableStateFlow(false)
}

@Composable
internal fun ReaderGlowFrameMonitor(active: Boolean) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(active, lifecycle) {
        if (!active || ReaderGlowQuality.reduced.value) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val tracker = ReaderGlowFrameTracker()
            while (!ReaderGlowQuality.reduced.value) {
                withFrameNanos { if (tracker.frame(it)) ReaderGlowQuality.reduced.value = true }
            }
        }
    }
}

private data class GlowConfig(
    val settings: ReaderImageBackground,
    val reduced: Boolean,
    val colors: ReaderGlowColors
)
private data class GlowTile(
    val layout: TextLayoutResult,
    val settings: ReaderImageBackground,
    val reduced: Boolean,
    val left: Int,
    val top: Int,
    val image: ImageBitmap?
)
private class GlowSlot {
    var coordinates: LayoutCoordinates? = null
    var layout: TextLayoutResult? = null
    var tiles by mutableStateOf<Map<Int, GlowTile>>(emptyMap())
}
private data class GlowText(val slot: GlowSlot, val layout: TextLayoutResult, val origin: Offset)
private data class GlowGlyph(val start: Int, val end: Int, val fontPx: Float)

// Text blocks and neighbouring pages can enter composition together. Limit their
// software rasterization so background work cannot crowd out reader scrolling.
private val glowRenderPermits = Semaphore(2)

private fun glyphFontSp(input: TextLayoutInput, offset: Int): Float {
    val base = input.style.fontSize.value
    var value = base
    for (span in input.text.spanStyles) if (offset in span.start until span.end) {
        val size = span.item.fontSize
        if (size.isSp) value = size.value else if (size.isEm) value = base * size.value
    }
    return value
}

private fun hasGlowGlyphs(input: TextLayoutInput): Boolean =
    input.style.fontSize.value >= 13f || input.text.spanStyles.any {
        glyphFontSp(input, it.start) >= 13f
    }

private class GlowSink {
    var coordinates: LayoutCoordinates? = null
    private val slots = LinkedHashSet<GlowSlot>()
    var revision by mutableIntStateOf(0)
    fun update(slot: GlowSlot) { slots.add(slot); revision++ }
    fun remove(slot: GlowSlot) { slots.remove(slot); revision++ }
    fun snapshot(): List<GlowText> {
        val surface = coordinates?.takeIf { it.isAttached } ?: return emptyList()
        return slots.mapNotNull { slot ->
            val c = slot.coordinates?.takeIf { it.isAttached } ?: return@mapNotNull null
            val layout = slot.layout ?: return@mapNotNull null
            if (!hasGlowGlyphs(layout.layoutInput)) return@mapNotNull null
            val point = surface.localPositionOf(c, Offset.Zero)
            // Keep a small margin for shadows that extend past a text block.
            if (point.y + layout.size.height < -READER_GLOW_TILE_HEIGHT ||
                point.y > surface.size.height + READER_GLOW_TILE_HEIGHT ||
                point.x + layout.size.width < -READER_GLOW_TILE_HEIGHT ||
                point.x > surface.size.width + READER_GLOW_TILE_HEIGHT) return@mapNotNull null
            GlowText(slot, layout, point)
        }
    }
}
private val LocalGlowSink = staticCompositionLocalOf<GlowSink?> { null }
private val LocalGlowConfig = staticCompositionLocalOf<GlowConfig?> { null }
internal val LocalReaderPhotoText = staticCompositionLocalOf<Color?> { null }

/** Keep cached glow inside the same viewport as the vertically scrolling body. */
@Composable
internal fun ReaderScrollGlowSurface(
    background: Bitmap?,
    settings: ReaderImageBackground,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    ReaderGlowSurface(background, settings, modifier.clipToBounds(), content)
}

/** Keep one of the existing layers on the text while the other layers are prepared. */
@Composable
internal fun readerGlowTextAppearance(text: AnnotatedString, style: TextStyle): Pair<AnnotatedString, TextStyle> {
    val config = LocalGlowConfig.current
    val density = LocalDensity.current
    return remember(text, style, config?.settings, density) {
        val glowConfig = config ?: return@remember text to style
        val settings = glowConfig.settings
        val color = Color(glowConfig.colors.glow).copy(alpha = READER_GLOW_ALPHA)
        val base = style.fontSize.value
        fun shadowFor(size: Float): Shadow {
            if (!size.isFinite() || size < 13f) return Shadow.None
            val px = with(density) { size.sp.toPx() }
            return Shadow(color, Offset.Zero, READER_GLOW_BASE_RADIUS_EM * px * settings.glowStrength / 100f)
        }
        val sizedSpans = text.spanStyles.filter { it.item.fontSize.isSp || it.item.fontSize.isEm }
        val adjusted = if (sizedSpans.isEmpty()) text else buildAnnotatedString {
            append(text)
            sizedSpans.forEach { span ->
                val size = span.item.fontSize
                val sizeSp = if (size.isSp) size.value else base * size.value
                addStyle(SpanStyle(shadow = shadowFor(sizeSp)), span.start, span.end)
            }
        }
        adjusted to style.copy(shadow = shadowFor(base))
    }
}

/** Each body block keeps its own glow, so position-only changes never invalidate its pixels. */
@Composable
internal fun readerGlowTextModifier(layout: TextLayoutResult?): Modifier {
    val sink = LocalGlowSink.current ?: return Modifier
    val config = LocalGlowConfig.current ?: return Modifier
    val slot = remember(sink) { GlowSlot() }
    SideEffect {
        if (slot.layout != layout) { slot.layout = layout; sink.update(slot) }
    }
    DisposableEffect(sink, slot) { onDispose { sink.remove(slot) } }
    LaunchedEffect(sink, slot, layout, config.settings, config.reduced) {
        if (layout == null || layout.size.width <= 0 || layout.size.height <= 0 ||
            !hasGlowGlyphs(layout.layoutInput)) {
            slot.tiles = emptyMap()
            return@LaunchedEffect
        }
        // A quality change keeps the old tiles until their replacements are ready.
        slot.tiles = slot.tiles.filterValues { it.layout == layout && it.settings == config.settings }
        suspend fun prepare(index: Int) {
            if (slot.tiles[index]?.reduced == config.reduced) return
            try {
                val tile = glowRenderPermits.withPermit {
                    withContext(Dispatchers.Default) { renderGlowTile(config, layout, index) }
                }
                slot.tiles = slot.tiles + (index to tile)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                AppLogger.w("ReaderGlow", "Unable to prepare text enhancement", error)
            }
        }
        if (layout.size.height <= READER_GLOW_TILE_HEIGHT) {
            // Lazy-list premeasurement can prepare a paragraph before it is placed on screen.
            prepare(0)
        } else {
            var wanted = ReaderGlowTileWindow(IntRange.EMPTY, IntRange.EMPTY)
            snapshotFlow {
                sink.revision
                val surface = sink.coordinates?.takeIf { it.isAttached }
                val text = slot.coordinates?.takeIf { it.isAttached }
                if (surface == null || text == null) ReaderGlowTileWindow(IntRange.EMPTY, IntRange.EMPTY)
                else readerGlowTileWindow(layout.size.height, surface.size.height,
                    surface.localPositionOf(text, Offset.Zero).y)
            }.onEach { wanted = it }.conflate().collect { window ->
                slot.tiles = slot.tiles.filterKeys { it in window.buffered }
                for (index in window.inPriorityOrder()) {
                    // Finish the current tile instead of cancelling it for every scroll pixel,
                    // then skip work that is no longer close to the viewport.
                    if (index !in wanted.buffered) continue
                    prepare(index)
                    if (index !in wanted.buffered) slot.tiles = slot.tiles - index
                }
            }
        }
    }
    return Modifier.onGloballyPositioned {
        slot.coordinates = it
        sink.update(slot)
    }
}

/** Draw independently cached text blocks below the body, including during list movement. */
@Composable
internal fun ReaderGlowSurface(
    background: Bitmap?,
    settings: ReaderImageBackground,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val reduced by ReaderGlowQuality.reduced.collectAsStateWithLifecycle()
    val enabled = background != null && settings.glowEnabled && settings.glowStrength > 0f
    val colors = remember(settings) { readerGlowColors(settings) }
    val sink = remember(background, enabled) { if (enabled) GlowSink() else null }
    val config = remember(settings, reduced, colors) { GlowConfig(settings, reduced, colors) }
    Box(modifier.onGloballyPositioned {
        sink?.let { target -> target.coordinates = it; target.revision++ }
    }.drawBehind {
        if (sink != null) {
            sink.revision
            sink.snapshot().forEach { text ->
                text.slot.tiles.values.forEach { tile ->
                    if (tile.layout == text.layout && tile.settings == settings) {
                        tile.image?.let { image ->
                            drawImage(image, topLeft = text.origin + Offset(tile.left.toFloat(), tile.top.toFloat()))
                        }
                    }
                }
            }
        }
    }) {
        CompositionLocalProvider(
            LocalGlowSink provides sink,
            LocalGlowConfig provides config.takeIf { enabled },
            LocalReaderPhotoText provides background?.let { Color(colors.text) }
        ) { content() }
    }
}

private suspend fun renderGlowTile(config: GlowConfig, layout: TextLayoutResult, index: Int): GlowTile {
    val job = currentCoroutineContext()
    val input = layout.layoutInput
    val settings = config.settings
    var maxFontSp = input.style.fontSize.value.takeIf { it.isFinite() && it > 0f } ?: 0f
    input.text.spanStyles.forEach { span ->
        val size = glyphFontSp(input, span.start)
        if (size.isFinite()) maxFontSp = maxOf(maxFontSp, size)
    }
    val maxFontPx = with(input.density) { maxFontSp.sp.toPx() }
    // Include the full blurred edge outside the paragraph and neighbouring tiles.
    val padding = ceil(maxFontPx * 0.8f * settings.glowStrength / 100f).toInt() + 2
    val start = index * READER_GLOW_TILE_HEIGHT
    val end = minOf(start + READER_GLOW_TILE_HEIGHT, layout.size.height)
    val top = start - if (index == 0) padding else 0
    val bottom = end + if (end == layout.size.height) padding else 0
    val selected = mutableListOf<GlowGlyph>()
    for (line in 0 until layout.lineCount) {
        job.ensureActive()
        if (layout.getLineBottom(line) < top - padding || layout.getLineTop(line) > bottom + padding) continue
        for (offset in layout.getLineStart(line) until layout.getLineEnd(line, visibleEnd = true)) {
            if (offset % 128 == 0) job.ensureActive()
            if (input.text[offset].isWhitespace() || input.text[offset].isLowSurrogate()) continue
            if (input.placeholders.any { offset in it.start until it.end }) continue
            val fontSp = glyphFontSp(input, offset)
            if (!fontSp.isFinite() || fontSp < 13f) continue
            if (layout.getBoundingBox(offset).width <= 0f) continue
            val fontPx = with(input.density) { fontSp.sp.toPx() }
            val glyphEnd = offset + if (input.text[offset].isHighSurrogate() && offset + 1 < input.text.length &&
                input.text[offset + 1].isLowSurrogate()) 2 else 1
            selected.add(GlowGlyph(offset, glyphEnd, fontPx))
        }
    }
    if (selected.isEmpty()) return GlowTile(layout, settings, config.reduced, -padding, top, null)
    val bitmap = Bitmap.createBitmap(layout.size.width + 2 * padding, bottom - top, Bitmap.Config.ARGB_8888)
    var published = false
    try {
        val native = AndroidCanvas(bitmap)
        native.translate(padding.toFloat(), -top.toFloat())
        val canvas = Canvas(native)
        // The 0.17em layer is drawn with the text itself, including before a tile is ready.
        val radii = if (config.reduced) listOf(0.40f)
            else readerGlowRadiiEm.filter { it != READER_GLOW_BASE_RADIUS_EM }
        for (radius in radii) {
            job.ensureActive()
            val layerColor = Color(config.colors.glow).copy(alpha =
                if (radius >= 0.30f) settings.outerGlowAlpha else READER_GLOW_ALPHA)
            val glyphs = androidx.compose.ui.text.buildAnnotatedString {
                append(input.text)
                addStyle(androidx.compose.ui.text.SpanStyle(color = Color.Transparent,
                    background = Color.Transparent, shadow = Shadow.None), 0, length)
                selected.forEach { glyph ->
                    addStyle(androidx.compose.ui.text.SpanStyle(color = layerColor,
                        shadow = Shadow(layerColor, Offset.Zero,
                            radius * glyph.fontPx * settings.glowStrength / 100f)), glyph.start, glyph.end)
                }
            }
            val measurer = TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection, cacheSize = 0)
            val mask = measurer.measure(glyphs, input.style, input.overflow, input.softWrap,
                input.maxLines, input.placeholders, input.constraints)
            mask.multiParagraph.paint(canvas)
        }
        job.ensureActive()
        bitmap.prepareToDraw()
        published = true
        return GlowTile(layout, settings, config.reduced, -padding, top, bitmap.asImageBitmap())
    } finally { if (!published) bitmap.recycle() }
}

/** Body-only facade for preview text and inline ruby; it shares the same surface cache. */
@Composable
internal fun ReaderGlowBodyText(text: String, style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val color = LocalReaderPhotoText.current ?: style.color
    val annotated = remember(text) { AnnotatedString(text) }
    val (displayed, glowStyle) = readerGlowTextAppearance(annotated, style)
    androidx.compose.material3.Text(text = displayed, style = glowStyle, color = color, maxLines = maxLines,
        onTextLayout = { layout = it }, modifier = modifier.then(readerGlowTextModifier(layout)))
}
