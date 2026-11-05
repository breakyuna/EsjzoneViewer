package com.breakyuna.esjzone.ui.reader

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextLayoutInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

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

private class GlowSlot {
    var coordinates: LayoutCoordinates? = null
    var layout: TextLayoutResult? = null
}
private data class GlowText(val token: GlowSlot, val layout: TextLayoutResult, val origin: Offset) {
    val input: TextLayoutInput get() = layout.layoutInput
}
private fun glyphFontSp(input: TextLayoutInput, offset: Int): Float {
    val base = input.style.fontSize.value
    var value = base
    for (span in input.text.spanStyles) if (offset in span.start until span.end) {
        val size = span.item.fontSize
        if (size.isSp) value = size.value else if (size.isEm) value = base * size.value
    }
    return value
}
private data class GlowRequest(val size: IntSize, val text: List<GlowText>)
private data class GlowGlyph(val start: Int, val end: Int, val fontPx: Float)
private data class GlowMaskKey(val layout: TextLayoutResult, val glyphs: List<GlowGlyph>, val em: Float)
private data class GlowFrame(val request: GlowRequest, val bitmap: Bitmap)

private class GlowSink {
    var coordinates: LayoutCoordinates? = null
    private val slots = LinkedHashSet<GlowSlot>()
    var revision by mutableIntStateOf(0)
    fun update(slot: GlowSlot) { slots.add(slot); revision++ }
    fun remove(slot: GlowSlot) { slots.remove(slot); revision++ }
    fun snapshot(): GlowRequest {
        val surface = coordinates?.takeIf { it.isAttached } ?: return GlowRequest(IntSize.Zero, emptyList())
        return GlowRequest(surface.size, slots.mapNotNull { slot ->
            val c = slot.coordinates?.takeIf { it.isAttached } ?: return@mapNotNull null
            val layout = slot.layout ?: return@mapNotNull null
            if (layout.layoutInput.style.fontSize.value < 13f && layout.layoutInput.text.spanStyles.none {
                glyphFontSp(layout.layoutInput, it.start) >= 13f
            }) return@mapNotNull null
            val point = surface.localPositionOf(c, Offset.Zero)
            if (point.y + layout.size.height < 0 || point.y > surface.size.height ||
                point.x + layout.size.width < 0 || point.x > surface.size.width) return@mapNotNull null
            GlowText(slot, layout, Offset(point.x.roundToInt().toFloat(), point.y.roundToInt().toFloat()))
        })
    }
}
private val LocalGlowSink = staticCompositionLocalOf<GlowSink?> { null }
internal val LocalReaderPhotoText = staticCompositionLocalOf<Color?> { null }

/** Registers only body glyphs. Headings, image captions and toolbar controls never call this. */
@Composable
internal fun readerGlowTextModifier(layout: TextLayoutResult?): Modifier {
    val sink = LocalGlowSink.current ?: return Modifier
    val slot = remember(sink) { GlowSlot() }
    SideEffect {
        if (slot.layout != layout) { slot.layout = layout; sink.update(slot) }
    }
    DisposableEffect(sink, slot) { onDispose { sink.remove(slot) } }
    return Modifier.onGloballyPositioned {
        slot.coordinates = it
        sink.update(slot)
    }
}

/** One transparent glow bitmap per mounted page; the scroll canvas includes only visible blocks.
 * Registrations survive common parent translations, so entry/page animations reuse the texture.
 */
@OptIn(FlowPreview::class)
@Composable
internal fun ReaderGlowSurface(
    background: Bitmap?,
    settings: ReaderImageBackground,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val reduced by ReaderGlowQuality.reduced.collectAsState()
    val enabled = background != null && settings.glowEnabled && settings.glowStrength > 0f
    val colors = remember(settings) { readerGlowColors(settings) }
    val sink = remember(background, enabled) { if (enabled) GlowSink() else null }
    var frame by remember(background, settings, enabled, reduced) {
        mutableStateOf<GlowFrame?>(null)
    }
    LaunchedEffect(sink, background, settings, reduced) {
        if (sink == null || background == null) return@LaunchedEffect
        val layouts = LinkedHashMap<GlowMaskKey, TextLayoutResult>()
        snapshotFlow { sink.revision; sink.snapshot() }.sample(64).collectLatest { request ->
            if (request.size.width <= 0 || request.size.height <= 0 || request.text.isEmpty()) {
                frame = null
                layouts.clear()
            } else try {
                frame = withContext(Dispatchers.Default) {
                    renderGlow(background, settings, reduced, request, layouts)?.let { GlowFrame(request, it) }
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                frame = null
                AppLogger.w("ReaderGlow", "Unable to prepare text enhancement", error)
            }
        }
    }
    Box(modifier.onGloballyPositioned {
        sink?.let { target -> target.coordinates = it; target.revision++ }
    }.drawBehind {
        val cached = frame
        if (cached != null && sink != null) {
            sink.revision // Positions invalidate drawing, not composition or glyph rasterization.
            val current = sink.snapshot()
            // Scroll existing text with its glow while the next visible-region result is prepared.
            // A changed layout must never reuse glyphs from the old font/width/text.
            val first = cached.request.text.firstOrNull { old -> current.text.any { it.token === old.token && it.layout == old.layout } }
            if (first != null && current.size == cached.request.size) {
                val now = current.text.first { it.token === first.token }
                val delta = now.origin - first.origin
                val compatible = cached.request.text.all { old ->
                    current.text.firstOrNull { it.token === old.token }?.let {
                        it.layout == old.layout && it.origin - old.origin == delta
                    } ?: run {
                        val origin = old.origin + delta
                        origin.y + old.layout.size.height <= 0 || origin.y >= current.size.height ||
                            origin.x + old.layout.size.width <= 0 || origin.x >= current.size.width
                    }
                }
                if (compatible) drawImage(cached.bitmap.asImageBitmap(), topLeft = delta)
            }
        }
    }) {
        CompositionLocalProvider(
            LocalGlowSink provides sink,
            LocalReaderPhotoText provides background?.let { Color(colors.text) }
        ) { content() }
    }
}

private suspend fun renderGlow(background: Bitmap, settings: ReaderImageBackground, reduced: Boolean,
    request: GlowRequest, layouts: MutableMap<GlowMaskKey, TextLayoutResult>): Bitmap? {
    val job = currentCoroutineContext()
    val activeLayouts = request.text.mapTo(HashSet()) { it.layout }
    layouts.keys.removeAll { it.layout !in activeLayouts }
    val scale = max(request.size.width.toFloat() / background.width, request.size.height.toFloat() / background.height)
    val cropX = (request.size.width - background.width * scale) / 2f
    val cropY = (request.size.height - background.height * scale) / 2f
    val colors = readerGlowColors(settings)
    val luminance = readerPixelLuminance(colors.text.toInt())
    val bitmap = Bitmap.createBitmap(request.size.width, request.size.height, Bitmap.Config.ARGB_8888)
    var published = false
    try {
        val native = AndroidCanvas(bitmap)
        val canvas = Canvas(native)
        var painted = false
        for (text in request.text) {
            job.ensureActive()
            val input = text.input
            val layout = text.layout
            val selected = mutableListOf<GlowGlyph>()
            for (line in 0 until layout.lineCount) {
                val top = text.origin.y + layout.getLineTop(line)
                val bottom = text.origin.y + layout.getLineBottom(line)
                if (bottom < 0 || top > request.size.height) continue
                val cells = linkedMapOf<Pair<Float, Int>, MutableList<Pair<Int, androidx.compose.ui.geometry.Rect>>>()
                for (offset in layout.getLineStart(line) until layout.getLineEnd(line, visibleEnd = true)) {
                    if (input.text[offset].isWhitespace() || input.text[offset].isLowSurrogate()) continue
                    // Inline ruby has its own body text registration; placeholders contain no glyphs.
                    if (input.placeholders.any { offset in it.start until it.end }) continue
                    val fontSp = glyphFontSp(input, offset)
                    if (!fontSp.isFinite() || fontSp < 13f) continue
                    val fontPx = with(input.density) { fontSp.sp.toPx() }
                    val bounds = layout.getBoundingBox(offset)
                    if (bounds.width <= 0f) continue
                    cells.getOrPut(fontSp to floor(bounds.left / (fontPx * 2f)).toInt()) { mutableListOf() }.add(offset to bounds)
                }
                for ((cell, bounds) in cells) {
                    job.ensureActive()
                    val samples = ArrayList<Double>()
                    for ((_, glyph) in bounds) {
                        for (y in 0..4) for (x in 0..4) {
                            val sx = text.origin.x + glyph.left + glyph.width * (x + 0.5f) / 5f
                            val sy = text.origin.y + glyph.top + glyph.height * (y + 0.5f) / 5f
                            if (sx < 0 || sy < 0 || sx >= request.size.width || sy >= request.size.height) continue
                            val px = ((sx - cropX) / scale).toInt().coerceIn(0, background.width - 1)
                            val py = ((sy - cropY) / scale).toInt().coerceIn(0, background.height - 1)
                            val pixel = background.getPixel(px, py)
                            val opacity = 1f - settings.transparency / 100f
                            val dimmed = (0xFF shl 24) or
                                (((pixel shr 16 and 255) * opacity).roundToInt() shl 16) or
                                (((pixel shr 8 and 255) * opacity).roundToInt() shl 8) or
                                ((pixel and 255) * opacity).roundToInt()
                            samples.add(readerContrast(luminance, readerPixelLuminance(dimmed)))
                        }
                    }
                    if (readerGlowRequired(samples)) {
                        val fontPx = with(input.density) { cell.first.sp.toPx() }
                        for ((offset, _) in bounds) {
                            val end = offset + if (input.text[offset].isHighSurrogate() && offset + 1 < input.text.length &&
                                input.text[offset + 1].isLowSurrogate()) 2 else 1
                            selected.add(GlowGlyph(offset, end, fontPx))
                        }
                    }
                }
            }
            val radii = if (reduced) listOf(0.17f, 0.40f) else readerGlowRadiiEm
            val keys = radii.map { GlowMaskKey(layout, selected.toList(), it) }
            layouts.keys.removeAll { it.layout == layout && it !in keys }
            if (selected.isNotEmpty()) {
                painted = true
                native.save()
                native.translate(text.origin.x, text.origin.y)
                for (key in keys) {
                    job.ensureActive()
                    val mask = layouts.getOrPut(key) {
                        val layerColor = Color(colors.glow).copy(alpha =
                            if (key.em >= 0.30f) settings.outerGlowAlpha else READER_GLOW_ALPHA)
                        val glyphs = androidx.compose.ui.text.buildAnnotatedString {
                            append(input.text)
                            // Excluded glyphs must have neither fill nor shadow, even near a bad block.
                            addStyle(androidx.compose.ui.text.SpanStyle(color = Color.Transparent,
                                background = Color.Transparent, shadow = Shadow.None), 0, length)
                            key.glyphs.forEach { glyph ->
                                addStyle(androidx.compose.ui.text.SpanStyle(color = layerColor,
                                    shadow = Shadow(layerColor, Offset.Zero,
                                        key.em * glyph.fontPx * settings.glowStrength / 100f)), glyph.start, glyph.end)
                            }
                        }
                        val measurer = TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection, cacheSize = 0)
                        measurer.measure(glyphs, input.style, input.overflow, input.softWrap,
                            input.maxLines, input.placeholders, input.constraints)
                    }
                    mask.multiParagraph.paint(canvas)
                }
                native.restore()
            }
        }
        if (!painted) return null
        bitmap.prepareToDraw()
        published = true
        return bitmap
    } finally { if (!published) bitmap.recycle() }
}

/** Body-only facade for preview text and inline ruby; it shares the same surface cache. */
@Composable
internal fun ReaderGlowBodyText(text: String, style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val color = LocalReaderPhotoText.current ?: style.color
    androidx.compose.material3.Text(text = text, style = style, color = color, maxLines = maxLines,
        onTextLayout = { layout = it }, modifier = modifier.then(readerGlowTextModifier(layout)))
}
