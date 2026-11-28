package com.breakyuna.esjzone.ui.reader

import com.breakyuna.esjzone.data.reader.parseRichChapterContent
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderLink
import com.breakyuna.esjzone.domain.reader.sourceText
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.novellibrary.component.Component
import com.breakyuna.esjzone.novellibrary.component.ImageComponent
import com.breakyuna.esjzone.novellibrary.component.LinkTextStyle
import com.breakyuna.esjzone.novellibrary.component.NewLineComponent
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.ui.page.findCommunityUrls
import com.breakyuna.esjzone.ui.page.hasCommunityHtmlLink
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup

private data class ComponentLinks(
    val text: String,
    val isText: Boolean,
    val isImage: Boolean,
    val isLineBreak: Boolean,
    val links: List<ReaderLink>
)

private fun TextComponent.hasLinkStyle(): Boolean =
    getStyles().any { it is LinkTextStyle } || getExtras().any { it.hasLinkStyle() }

private fun extractComponentLinks(components: List<Component>, base: HttpUrl?): List<ComponentLinks> =
    components.map { component ->
        when {
            component is NewLineComponent ->
                ComponentLinks("", isText = false, isImage = false, isLineBreak = true, links = emptyList())
            component is ImageComponent ->
                ComponentLinks("", isText = false, isImage = true, isLineBreak = false, links = emptyList())
            component is TextComponent -> {
                var offset = 0
                val textBuilder = StringBuilder()
                val links = mutableListOf<ReaderLink>()
                fun visit(part: TextComponent) {
                    val start = offset
                    textBuilder.append(part.text)
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
                ComponentLinks(textBuilder.toString(), isText = true, isImage = false, isLineBreak = false, links = links)
            }
            else -> ComponentLinks("", isText = false, isImage = false, isLineBreak = false, links = emptyList())
        }
    }

private fun blockMatches(block: ReaderBlock, item: ComponentLinks): Boolean = when (block) {
    is ReaderBlock.Paragraph, is ReaderBlock.Text ->
        item.isText && block.sourceText() == item.text
    is ReaderBlock.Image -> item.isImage
    ReaderBlock.LineBreak -> item.isLineBreak
}

private fun matchLinksToBlock(block: ReaderBlock, item: ComponentLinks): List<ReaderLink>? {
    if (block !is ReaderBlock.Paragraph && block !is ReaderBlock.Text) return null
    val blockText = block.sourceText()
    if (blockText == item.text) return item.links
    val normalizedBlock = blockText.filterNot(Char::isWhitespace)
    val normalizedItem = item.text.filterNot(Char::isWhitespace)
    if (normalizedBlock.isNotEmpty() && normalizedBlock == normalizedItem) {
        val blockNonWsToIndex = IntArray(normalizedBlock.length)
        var k = 0
        for (idx in blockText.indices) {
            if (!blockText[idx].isWhitespace()) blockNonWsToIndex[k++] = idx
        }
        val mapped = item.links.mapNotNull { link ->
            var firstNonWs = -1
            var lastNonWs = -1
            var nonWsOrdinal = 0
            for (idx in item.text.indices) {
                if (!item.text[idx].isWhitespace()) {
                    if (idx in link.start until link.end) {
                        if (firstNonWs < 0) firstNonWs = nonWsOrdinal
                        lastNonWs = nonWsOrdinal
                    }
                    nonWsOrdinal++
                }
            }
            if (firstNonWs < 0 || lastNonWs < firstNonWs) null
            else ReaderLink(blockNonWsToIndex[firstNonWs], blockNonWsToIndex[lastNonWs] + 1, link.url)
        }
        if (mapped.isNotEmpty()) return mapped
    }
    return null
}

private fun alignComponentLinks(
    blocks: List<ReaderBlock>,
    componentInfos: List<ComponentLinks>
): Map<Int, List<ReaderLink>> {
    if (blocks.isEmpty() || componentInfos.none { it.links.isNotEmpty() }) return emptyMap()
    val matchedLinks = mutableMapOf<Int, List<ReaderLink>>()
    val unmatchedLinked = mutableListOf<ComponentLinks>()
    val matchedBlockIndices = HashSet<Int>()
    var b = 0
    var c = 0
    while (b < blocks.size && c < componentInfos.size) {
        if (blockMatches(blocks[b], componentInfos[c])) {
            if (componentInfos[c].links.isNotEmpty()) {
                matchedLinks[b] = componentInfos[c].links
            }
            matchedBlockIndices += b
            b++
            c++
            continue
        }
        var syncB = -1
        var syncC = -1
        val maxDist = 24
        search@ for (d in 1..maxDist) {
            for (db in 0..d) {
                val dc = d - db
                val nb = b + db
                val nc = c + dc
                if (nb < blocks.size && nc < componentInfos.size && blockMatches(blocks[nb], componentInfos[nc])) {
                    syncB = nb
                    syncC = nc
                    break@search
                }
            }
        }
        if (syncB >= 0) {
            for (skippedC in c until syncC) {
                val item = componentInfos[skippedC]
                if (item.links.isEmpty()) continue
                var recovered = false
                for (candidateB in b until syncB) {
                    if (candidateB in matchedBlockIndices) continue
                    val localMatch = matchLinksToBlock(blocks[candidateB], item) ?: continue
                    matchedLinks[candidateB] = matchedLinks[candidateB].orEmpty() + localMatch
                    matchedBlockIndices += candidateB
                    recovered = true
                    break
                }
                if (!recovered) unmatchedLinked += item
            }
            b = syncB
            c = syncC
        } else {
            val item = componentInfos[c]
            if (item.links.isNotEmpty()) {
                val localMatch = matchLinksToBlock(blocks[b], item)
                if (localMatch != null) {
                    matchedLinks[b] = matchedLinks[b].orEmpty() + localMatch
                    matchedBlockIndices += b
                } else {
                    unmatchedLinked += item
                }
            }
            b++
            c++
        }
    }
    while (c < componentInfos.size) {
        if (componentInfos[c].links.isNotEmpty()) unmatchedLinked += componentInfos[c]
        c++
    }
    if (unmatchedLinked.isNotEmpty()) {
        for (item in unmatchedLinked) {
            for (blockIndex in blocks.indices) {
                if (blockIndex in matchedBlockIndices) continue
                val recovered = matchLinksToBlock(blocks[blockIndex], item) ?: continue
                matchedLinks[blockIndex] = matchedLinks[blockIndex].orEmpty() + recovered
                matchedBlockIndices += blockIndex
                break
            }
        }
    }
    return matchedLinks
}

/** Recover link targets without changing the stored body or its position fingerprint. */
internal fun readerChapterLinks(
    blocks: List<ReaderBlock>,
    html: String?,
    baseUrl: String,
    components: List<Component> = emptyList()
): Map<Int, List<ReaderLink>> {
    val hasDirectLinks = components.any { it is TextComponent && it.hasLinkStyle() }
    val htmlLinks: Map<Int, List<ReaderLink>> = if (hasDirectLinks || (html != null && hasCommunityHtmlLink(html))) {
        runCatching {
            val base = baseUrl.toHttpUrlOrNull() ?: EsjzoneUrls.resolve(baseUrl).toHttpUrlOrNull()
            val sourceComponents = if (hasDirectLinks) {
                components
            } else {
                parseRichChapterContent(Jsoup.parseBodyFragment(html!!, base?.toString().orEmpty()).body()).components
            }
            alignComponentLinks(blocks, extractComponentLinks(sourceComponents, base))
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
