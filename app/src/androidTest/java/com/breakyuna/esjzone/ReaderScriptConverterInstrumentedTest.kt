package com.breakyuna.esjzone

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderChapterDocument
import com.breakyuna.esjzone.domain.reader.ReaderChapterRef
import com.breakyuna.esjzone.domain.reader.ReaderRuby
import com.breakyuna.esjzone.domain.reader.ReaderTextStyle
import com.breakyuna.esjzone.ui.reader.ReaderScript
import com.breakyuna.esjzone.ui.reader.ReaderScriptConverter
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderScriptConverterInstrumentedTest {
    @Test
    fun originalAndEmptyTextReturnTheExistingString() {
        val source = String(CharArray(8192) { '龍' })
        assertSame(source, ReaderScriptConverter.convert(source, ReaderScript.ORIGINAL))
        ReaderScript.entries.forEach { script ->
            assertSame("", ReaderScriptConverter.convert("", script))
        }
    }

    @Test
    fun simultaneousConversionsPreserveBothScriptsAndSourceBoundaries() {
        val executor = Executors.newFixedThreadPool(2)
        val start = CyclicBarrier(2)
        try {
            val tasks = listOf(ReaderScript.SIMPLIFIED, ReaderScript.TRADITIONAL).map { script ->
                executor.submit(Callable {
                    start.await(10, TimeUnit.SECONDS)
                    repeat(48) { index ->
                        val source = (if (script == ReaderScript.SIMPLIFIED) "龍馬書國😊" else "龙马书国😊") + index
                        val expected = (if (script == ReaderScript.SIMPLIFIED) "龙马书国😊" else "龍馬書國😊") + index
                        val mapping = ReaderScriptConverter.mapping(source, script)
                        assertEquals(expected, mapping.text)
                        assertEquals(source.length, mapping.sourceLength)
                        assertArrayEquals(IntArray(source.length) { it }, mapping.sourceStarts)
                        assertArrayEquals(IntArray(source.length) { it + 1 }, mapping.sourceEnds)
                    }
                })
            }
            tasks.forEach { it.get(10, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun snapshotKeepsRubyStylesAndParagraphMappings() {
        val ruby = ReaderBlock.Text("龍", setOf(ReaderTextStyle.Bold), ReaderRuby("龍", "書"))
        val paragraph = ReaderBlock.Paragraph(listOf(ruby, ReaderBlock.Text("馬")))
        val document = ReaderChapterDocument(ReaderChapterRef("國", "/forum/1/1.html"), listOf(paragraph))
        val snapshot = ReaderScriptConverter.snapshot(listOf(document), ReaderScript.SIMPLIFIED)

        assertEquals("国", snapshot.values["國"])
        assertEquals("龙", snapshot.values["龍"])
        assertEquals("书", snapshot.values["書"])
        val mapping = requireNotNull(snapshot.blockMappings[paragraph])
        assertEquals("龙马", mapping.text)
        assertArrayEquals(intArrayOf(0, 1), mapping.sourceStarts)
        assertArrayEquals(intArrayOf(1, 2), mapping.sourceEnds)
        assertEquals(setOf(ReaderTextStyle.Bold), ruby.styles)
        assertEquals(ReaderRuby("龍", "書"), ruby.ruby)
    }
}
