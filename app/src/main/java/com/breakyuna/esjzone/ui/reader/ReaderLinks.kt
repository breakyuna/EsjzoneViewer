package com.breakyuna.esjzone.ui.reader

import com.breakyuna.esjzone.data.repository.toReaderBlocks
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderLink
import com.breakyuna.esjzone.domain.reader.sourceText
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.novellibrary.component.LinkTextStyle
import com.breakyuna.esjzone.novellibrary.component.NewLineComponent
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.novellibrary.component.analyseComponents
import com.breakyuna.esjzone.ui.page.findCommunityUrls
import com.breakyuna.esjzone.ui.page.hasCommunityHtmlLink
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup

/** Recover link targets without changing the stored body or its position fingerprint. */
internal fun readerChapterLinks(
    blocks: List<ReaderBlock>,
    html: String?,
    baseUrl: String
): Map<Int, List<ReaderLink>> {
    val htmlLinks: Map<Int, List<ReaderLink>> = if (html != null && hasCommunityHtmlLink(html)) {
        runCatching {
            val base = baseUrl.toHttpUrlOrNull() ?: EsjzoneUrls.resolve(baseUrl).toHttpUrlOrNull()
            val components = analyseComponents(Jsoup.parseBodyFragment(html, base?.toString().orEmpty()).body())
            val parsed = components.flatMap { it.toReaderBlocks() }
            val aligned = parsed.size == blocks.size && parsed.indices.all { index ->
                val expected = blocks[index]
                val actual = parsed[index]
                when (expected) {
                    is ReaderBlock.Paragraph, is ReaderBlock.Text ->
                        (actual is ReaderBlock.Paragraph || actual is ReaderBlock.Text) &&
                            actual.sourceText() == expected.sourceText()
                    is ReaderBlock.Image -> actual is ReaderBlock.Image
                    ReaderBlock.LineBreak -> actual == ReaderBlock.LineBreak
                }
            }
            if (!aligned || components.size != blocks.size) emptyMap<Int, List<ReaderLink>>() else buildMap<Int, List<ReaderLink>> {
                components.forEachIndexed { index, component ->
                    if (component !is TextComponent || component is NewLineComponent) return@forEachIndexed
                    var offset = 0
                    val links = mutableListOf<ReaderLink>()
                    fun visit(part: TextComponent) {
                        val start = offset
                        offset += part.text.length
                        val href = part.getStyles().filterIsInstance<LinkTextStyle>().lastOrNull()?.href
                        val url = href?.toHttpUrlOrNull() ?: href?.let { base?.resolve(it) }
                        if (start < offset && url != null && url.username.isEmpty() && url.password.isEmpty()) {
                            val previous = links.lastOrNull()
                            if (previous != null && previous.end == start && previous.url == url.toString()) {
                                links[links.lastIndex] = previous.copy(end = offset)
                            } else links += ReaderLink(start, offset, url.toString())
                        }
                        part.getExtras().forEach(::visit)
                    }
                    visit(component)
                    if (links.isNotEmpty()) put(index, links)
                }
            }
        }.getOrDefault(emptyMap())
    } else emptyMap()

    return buildMap {
        blocks.forEachIndexed { index, block ->
            if (block !is ReaderBlock.Paragraph && block !is ReaderBlock.Text) return@forEachIndexed
            val explicit = htmlLinks[index].orEmpty()
            val bare = findCommunityUrls(block.sourceText())
                .filter { candidate -> explicit.none { it.start < candidate.end && candidate.start < it.end } }
                .map { ReaderLink(it.start, it.end, it.url) }
            val links = (explicit + bare).sortedBy { it.start }
            if (links.isNotEmpty()) put(index, links)
        }
    }
}
