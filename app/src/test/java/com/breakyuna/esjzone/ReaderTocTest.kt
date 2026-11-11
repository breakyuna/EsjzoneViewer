package com.breakyuna.esjzone

import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.analyseChapterList
import com.breakyuna.esjzone.ui.page.ReaderToc
import com.breakyuna.esjzone.ui.page.ReaderTocRow
import com.breakyuna.esjzone.ui.page.ReaderTocSourceVolume
import com.breakyuna.esjzone.ui.page.buildReaderToc
import com.breakyuna.esjzone.ui.page.ordered
import com.breakyuna.esjzone.ui.page.readerTocIndexOf
import com.breakyuna.esjzone.ui.page.readerTocRows
import com.breakyuna.esjzone.ui.page.readerTocSourceVolumes
import com.breakyuna.esjzone.ui.page.volumeKeyOf
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTocTest {
    private fun ch(id: Int, name: String = "第${id}章") = Chapter(name, "https://www.esjzone.cc/forum/9/$id.html", false)
    private val key: (Chapter) -> String = { it.url }

    private val c = (1..6).map { ch(it) }
    private val sources = listOf(
        ReaderTocSourceVolume("第一卷", c.subList(0, 2)),
        ReaderTocSourceVolume("第二卷", c.subList(2, 4)),
        ReaderTocSourceVolume("第三卷", c.subList(4, 6))
    )

    private fun names(rows: List<ReaderTocRow>) = rows.map {
        when (it) {
            is ReaderTocRow.Volume -> "[${it.volume.title}:${it.count}${if (it.expanded) "+" else "-"}]"
            is ReaderTocRow.Entry -> it.chapter.name
        }
    }

    @Test
    fun emptyCatalogHasNoVolumesOrRows() {
        val toc = buildReaderToc(emptyList(), sources, key)
        assertTrue(toc.volumes.isEmpty())
        assertFalse(toc.grouped)
        assertTrue(readerTocRows(toc, descending = false, expanded = emptySet(), keyOf = key).isEmpty())
        assertEquals(-1, readerTocIndexOf(emptyList(), "x", null))
    }

    @Test
    fun bookWithoutVolumesIsAFlatListThatStillReverses() {
        val toc = buildReaderToc(c, emptyList(), key)
        assertFalse(toc.grouped)
        assertEquals(c.map { it.name }, names(readerTocRows(toc, false, emptySet(), keyOf = key)))
        assertEquals(c.reversed().map { it.name }, names(readerTocRows(toc, true, emptySet(), keyOf = key)))
        // Untitled source groups alone do not count as volumes.
        val untitled = buildReaderToc(c, listOf(ReaderTocSourceVolume("", c)), key)
        assertFalse(untitled.grouped)
    }

    @Test
    fun multipleVolumesGroupAndOnlyExpandedOnesShowChapters() {
        val toc = buildReaderToc(c, sources, key)
        assertTrue(toc.grouped)
        assertEquals(listOf("第一卷", "第二卷", "第三卷"), toc.volumes.map { it.title })
        val second = toc.volumeKeyOf(key(c[3]), key)!!
        assertEquals(
            listOf("[第一卷:2-]", "[第二卷:2+]", "第3章", "第4章", "[第三卷:2-]"),
            names(readerTocRows(toc, false, setOf(second), keyOf = key))
        )
    }

    @Test
    fun descendingReversesVolumesAndChaptersInside() {
        val toc = buildReaderToc(c, sources, key)
        val all = toc.volumes.map { it.key }.toSet()
        assertEquals(
            listOf("[第三卷:2+]", "第6章", "第5章", "[第二卷:2+]", "第4章", "第3章", "[第一卷:2+]", "第2章", "第1章"),
            names(readerTocRows(toc, true, all, keyOf = key))
        )
        assertEquals(toc, toc.ordered(false))
        assertEquals(toc.volumes.map { it.key }.reversed(), toc.ordered(true).volumes.map { it.key })
    }

    @Test
    fun currentChapterIndexFollowsOrderAndFallsBackToCollapsedHeader() {
        val toc = buildReaderToc(c, sources, key)
        val current = key(c[4])
        val volume = toc.volumeKeyOf(current, key)
        assertEquals(toc.volumes[2].key, volume)
        val asc = readerTocRows(toc, false, setOf(volume!!), keyOf = key)
        assertEquals(3, readerTocIndexOf(asc, current, volume))
        val desc = readerTocRows(toc, true, setOf(volume), keyOf = key)
        assertEquals(2, readerTocIndexOf(desc, current, volume))
        val collapsed = readerTocRows(toc, false, emptySet(), keyOf = key)
        assertEquals(2, readerTocIndexOf(collapsed, current, volume))
        assertNull(toc.volumeKeyOf("missing", key))
        assertEquals(-1, readerTocIndexOf(collapsed, "missing", null))
    }

    @Test
    fun unknownChaptersStayWithThePreviousVolumeAndOrderIsKept() {
        val extra = ch(99, "番外")
        val reader = listOf(c[0], c[1], extra, c[2], c[3], c[4], c[5])
        val toc = buildReaderToc(reader, sources, key)
        assertEquals(listOf(3, 2, 2), toc.volumes.map { it.chapters.size })
        assertEquals(reader, toc.volumes.flatMap { it.chapters })
        // A chapter before any known one gets an untitled group, not a borrowed heading.
        val leading = buildReaderToc(listOf(extra) + c, sources, key)
        assertNull(leading.volumes.first().title)
        assertEquals(listOf(extra), leading.volumes.first().chapters)
    }

    @Test
    fun queryFiltersInsideVolumesAndExpandsMatches() {
        val named = listOf(ch(1, "序章"), ch(2, "终章"), ch(3, "序幕"), ch(4, "后记"))
        val toc = buildReaderToc(named, listOf(
            ReaderTocSourceVolume("上卷", named.subList(0, 2)), ReaderTocSourceVolume("下卷", named.subList(2, 4))
        ), key)
        assertEquals(listOf("[上卷:1+]", "序章", "[下卷:1+]", "序幕"), names(readerTocRows(toc, false, emptySet(), "序", key)))
        assertTrue(readerTocRows(toc, false, emptySet(), "不存在", key).isEmpty())
    }

    @Test
    fun sourceVolumesComeFromDetailsGroupsAndTextHeadings() {
        val integration = Jsoup.parse(
            """
            <div id="integration">
              <a href="/forum/9/1.html" data-title="序章"><p>序章</p></a>
              <details open><summary>第一卷</summary>
                <a href="/forum/9/2.html" data-title="一"><p>一</p></a>
                <details><summary>插图</summary><a href="/forum/9/3.html" data-title="插图"><p>插图</p></a></details>
              </details>
              <details><summary>空卷</summary></details>
              <p>第二卷</p>
              <a href="/forum/9/4.html" data-title="二"><p>二</p></a>
            </div>
            """.trimIndent()
        ).selectFirst("#integration")!!
        val volumes = readerTocSourceVolumes(analyseChapterList(integration).items)
        assertEquals(listOf("", "第一卷", "第二卷"), volumes.map { it.title })
        assertEquals(listOf(listOf("序章"), listOf("一", "插图"), listOf("二")), volumes.map { v -> v.chapters.map { it.name } })
        val toc: ReaderToc = buildReaderToc(volumes.flatMap { it.chapters }, volumes)
        assertTrue(toc.grouped)
        assertEquals(listOf(null, "第一卷", "第二卷"), toc.volumes.map { it.title })
    }
}
